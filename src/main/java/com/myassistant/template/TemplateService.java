package com.myassistant.template;

import com.myassistant.template.dto.CreateTemplateRequest;
import com.myassistant.template.dto.TemplateResponse;
import com.myassistant.template.dto.UpdateTemplateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The templates of one owner: every method takes the owner id (the token's user) and only ever sees that user's
 * templates. Another user's template is answered exactly like a missing one.
 */
@Service
@RequiredArgsConstructor
public class TemplateService {
    // {{ + anything without braces + }}, taken as written: "{{ level }}" is the placeholder " level ".
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^{}]+)}}");

    private final TemplateRepository templateRepository;
    private final TemplateMapper templateMapper;
    // An ObjectProvider, because the configuration feature (Phase 5) brings the implementation; with none, no
    // configuration exists yet, so no template is in use.
    private final ObjectProvider<TemplateUsage> templateUsages;

    // Sorted by name ignoring letter case, here rather than in MongoDB: a case-insensitive collation would stop the
    // ownerId index from serving the query, and one user's list is small.
    public List<TemplateResponse> findAll(String ownerId) {
        return templateRepository.findByOwnerId(ownerId).stream()
                .sorted(Comparator.comparing(PromptTemplate::getName,
                        String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder())))
                .map(templateMapper::toResponse)
                .toList();
    }

    public TemplateResponse findById(String ownerId, String id) {
        return templateMapper.toResponse(getTemplate(ownerId, id));
    }

    /** Like {@link #findById}, but empty instead of a 404: for a reader that tolerates a template deleted meanwhile. */
    public Optional<TemplateResponse> find(String ownerId, String id) {
        return templateRepository.findByIdAndOwnerId(id, ownerId).map(templateMapper::toResponse);
    }

    public TemplateResponse create(String ownerId, CreateTemplateRequest request) {
        PromptTemplate template = templateMapper.toEntity(ownerId, request);
        checkPlaceholders(template);
        return templateMapper.toResponse(templateRepository.save(template));
    }

    // A stale version makes the save throw OptimisticLockingFailureException (409).
    public TemplateResponse update(String ownerId, String id, UpdateTemplateRequest request) {
        PromptTemplate template = templateMapper.update(getTemplate(ownerId, id), request);
        checkPlaceholders(template);
        return templateMapper.toResponse(templateRepository.save(template));
    }

    public void delete(String ownerId, String id) {
        PromptTemplate template = getTemplate(ownerId, id);
        List<String> usedBy = templateUsages.orderedStream()
                .flatMap(usage -> usage.configurationNamesUsing(ownerId, id).stream())
                .toList();
        if (!usedBy.isEmpty()) {
            throw new TemplateInUseException(usedBy);
        }
        templateRepository.delete(template);
    }

    private PromptTemplate getTemplate(String ownerId, String id) {
        return templateRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new TemplateNotFoundException(id));
    }

    // Bean Validation checks each field; this rule spans two (content and variables), so it lives here.
    private static void checkPlaceholders(PromptTemplate template) {
        Set<String> declared = new HashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (TemplateVariable variable : template.getVariables()) {
            if (!declared.add(variable.name())) {
                duplicates.add(variable.name());
            }
        }
        Set<String> unknown = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(template.getContent());
        while (matcher.find()) {
            if (!declared.contains(matcher.group(1))) {
                unknown.add(matcher.group(1));
            }
        }
        if (!unknown.isEmpty() || !duplicates.isEmpty()) {
            throw new InvalidTemplateException(List.copyOf(unknown), List.copyOf(duplicates));
        }
    }
}
