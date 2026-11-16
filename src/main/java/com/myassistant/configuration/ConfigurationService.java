package com.myassistant.configuration;

import com.myassistant.configuration.dto.ConfigurationResponse;
import com.myassistant.configuration.dto.CreateConfigurationRequest;
import com.myassistant.configuration.dto.UpdateConfigurationRequest;
import com.myassistant.template.TemplateService;
import com.myassistant.template.dto.TemplateResponse;
import com.myassistant.template.dto.TemplateVariableDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The configurations ("assistants") of one owner: every method takes the owner id and only ever sees that user's
 * configurations. Templates are read through {@link TemplateService} only, so they are owner-scoped the same way: a
 * {@code templateId} of another user is a 404, exactly like an unknown one.
 * <p>
 * A configuration may point at a template that was deleted after the "in use" check ran (no transaction, see
 * {@link ConfigurationTemplateUsage}): reading it still works, with no template embedded; saving it again with that
 * {@code templateId} is a 404, so the user picks another template.
 */
@Service
@RequiredArgsConstructor
public class ConfigurationService {
    // As a person reads a list: letter case ignored (then exact, so equal-looking names keep a stable order).
    // Sorted here, not by MongoDB: a case-insensitive collation would stop the ownerId index from serving the
    // query, and one user's list is small.
    static final Comparator<String> BY_NAME = String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());
    private static final Comparator<LlmConfiguration> FAVORITES_FIRST_THEN_BY_NAME =
            Comparator.comparing(LlmConfiguration::isFavorite).reversed()
                    .thenComparing(LlmConfiguration::getName, BY_NAME);

    private final ConfigurationRepository configurationRepository;
    private final ConfigurationMapper configurationMapper;
    private final TemplateService templateService;

    // One template query for the whole list, not one per configuration.
    public List<ConfigurationResponse> findAll(String ownerId) {
        Map<String, TemplateResponse> templates = templateService.findAll(ownerId).stream()
                .collect(Collectors.toMap(TemplateResponse::id, Function.identity()));
        return configurationRepository.findByOwnerId(ownerId).stream()
                .sorted(FAVORITES_FIRST_THEN_BY_NAME)
                .map(configuration -> configurationMapper.toResponse(configuration,
                        configuration.getTemplateId() == null ? null : templates.get(configuration.getTemplateId())))
                .toList();
    }

    public ConfigurationResponse findById(String ownerId, String id) {
        return toResponse(ownerId, getConfiguration(ownerId, id));
    }

    public ConfigurationResponse create(String ownerId, CreateConfigurationRequest request) {
        LlmConfiguration configuration = configurationMapper.toEntity(ownerId, request);
        TemplateResponse template = checkTemplateAndVariables(ownerId, configuration);
        return configurationMapper.toResponse(configurationRepository.save(configuration), template);
    }

    // A stale version makes the save throw OptimisticLockingFailureException (409).
    public ConfigurationResponse update(String ownerId, String id, UpdateConfigurationRequest request) {
        LlmConfiguration configuration = configurationMapper.update(getConfiguration(ownerId, id), request);
        TemplateResponse template = checkTemplateAndVariables(ownerId, configuration);
        return configurationMapper.toResponse(configurationRepository.save(configuration), template);
    }

    public void delete(String ownerId, String id) {
        configurationRepository.delete(getConfiguration(ownerId, id));
    }

    // The copy is the same recipe, already checked when the original was saved.
    public ConfigurationResponse duplicate(String ownerId, String id) {
        LlmConfiguration copy = configurationRepository.save(configurationMapper.copy(getConfiguration(ownerId, id)));
        return toResponse(ownerId, copy);
    }

    private LlmConfiguration getConfiguration(String ownerId, String id) {
        return configurationRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ConfigurationNotFoundException(id));
    }

    private ConfigurationResponse toResponse(String ownerId, LlmConfiguration configuration) {
        TemplateResponse template = configuration.getTemplateId() == null ? null
                : templateService.find(ownerId, configuration.getTemplateId()).orElse(null);
        return configurationMapper.toResponse(configuration, template);
    }

    // The template must be one of the owner's (else 404), and every value must belong to one of its variables.
    // Missing values are fine: the template's defaults fill them in when the configuration is read.
    private TemplateResponse checkTemplateAndVariables(String ownerId, LlmConfiguration configuration) {
        TemplateResponse template = configuration.getTemplateId() == null ? null
                : templateService.findById(ownerId, configuration.getTemplateId());
        Set<String> declared = template == null ? Set.of()
                : template.variables().stream().map(TemplateVariableDto::name).collect(Collectors.toSet());
        List<String> unknown = configuration.getVariableValues().keySet().stream()
                .filter(name -> !declared.contains(name))
                .toList();
        if (!unknown.isEmpty()) {
            throw new UnknownVariablesException(unknown);
        }
        return template;
    }
}
