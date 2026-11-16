package com.myassistant.template;

import com.myassistant.template.dto.CreateTemplateRequest;
import com.myassistant.template.dto.TemplateResponse;
import com.myassistant.template.dto.TemplateVariableDto;
import com.myassistant.template.dto.UpdateTemplateRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static java.time.temporal.ChronoUnit.MILLIS;

/**
 * Translates between the {@link PromptTemplate} entity and the template DTOs. It also sets the timestamps: two fields
 * do not justify Spring Data auditing, and here they are visible next to the rest of the document.
 */
@Component
public class TemplateMapper {

    public PromptTemplate toEntity(String ownerId, CreateTemplateRequest request) {
        Instant now = now();
        return PromptTemplate.builder()
                // The id is ours, made before the save, so the first save is an insert (the version is still null).
                .id(UUID.randomUUID().toString())
                .ownerId(ownerId)
                .name(request.name())
                .description(request.description())
                .content(request.content())
                .variables(toVariables(request.variables()))
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    // The version is the one the client read, not the stored one: the save then fails if they differ.
    public PromptTemplate update(PromptTemplate template, UpdateTemplateRequest request) {
        return template.toBuilder()
                .version(request.version())
                .name(request.name())
                .description(request.description())
                .content(request.content())
                .variables(toVariables(request.variables()))
                .updatedAt(now())
                .build();
    }

    public TemplateResponse toResponse(PromptTemplate template) {
        return new TemplateResponse(
                template.getId(),
                template.getVersion(),
                template.getName(),
                template.getDescription(),
                template.getContent(),
                template.getVariables().stream()
                        .map(variable -> new TemplateVariableDto(variable.name(), variable.label(), variable.defaultValue()))
                        .toList(),
                template.getCreatedAt(),
                template.getUpdatedAt());
    }

    private static List<TemplateVariable> toVariables(List<TemplateVariableDto> variables) {
        if (variables == null) {
            return List.of();
        }
        return variables.stream()
                .map(variable -> new TemplateVariable(variable.name(), variable.label(), variable.defaultValue()))
                .toList();
    }

    // MongoDB stores milliseconds: the answer to a save then equals what a later read returns.
    private static Instant now() {
        return Instant.now().truncatedTo(MILLIS);
    }
}
