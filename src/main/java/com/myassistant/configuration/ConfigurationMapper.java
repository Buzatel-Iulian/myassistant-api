package com.myassistant.configuration;

import com.myassistant.configuration.dto.ConfigurationResponse;
import com.myassistant.configuration.dto.CreateConfigurationRequest;
import com.myassistant.configuration.dto.GenerationSettingsDto;
import com.myassistant.configuration.dto.UpdateConfigurationRequest;
import com.myassistant.template.dto.TemplateResponse;
import com.myassistant.template.dto.TemplateVariableDto;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static java.time.temporal.ChronoUnit.MILLIS;

/**
 * Translates between the {@link LlmConfiguration} entity and the configuration DTOs, sets the timestamps (as
 * TemplateMapper does) and resolves the variable values against the template for the answer.
 */
@Component
public class ConfigurationMapper {
    static final String COPY_SUFFIX = " (copy)";
    private static final int MAX_NAME_LENGTH = 80;

    public LlmConfiguration toEntity(String ownerId, CreateConfigurationRequest request) {
        Instant now = now();
        return LlmConfiguration.builder()
                // Our own id, so the first save is an insert (the version is still null).
                .id(UUID.randomUUID().toString())
                .ownerId(ownerId)
                .name(request.name())
                .description(request.description())
                .modelId(request.modelId())
                .settings(toSettings(request.settings()))
                .templateId(toTemplateId(request.templateId()))
                .variableValues(toValues(request.variableValues()))
                .favorite(Boolean.TRUE.equals(request.favorite()))
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    // The version is the one the client read, not the stored one: the save then fails if they differ.
    public LlmConfiguration update(LlmConfiguration configuration, UpdateConfigurationRequest request) {
        return configuration.toBuilder()
                .version(request.version())
                .name(request.name())
                .description(request.description())
                .modelId(request.modelId())
                .settings(toSettings(request.settings()))
                .templateId(toTemplateId(request.templateId()))
                .variableValues(toValues(request.variableValues()))
                .favorite(Boolean.TRUE.equals(request.favorite()))
                .updatedAt(now())
                .build();
    }

    // A new item: new id, no version yet, new timestamps. Not a favorite, so the copy does not jump to the top.
    public LlmConfiguration copy(LlmConfiguration original) {
        Instant now = now();
        String base = original.getName();
        if (base.length() + COPY_SUFFIX.length() > MAX_NAME_LENGTH) {
            base = base.substring(0, MAX_NAME_LENGTH - COPY_SUFFIX.length());
        }
        return original.toBuilder()
                .id(UUID.randomUUID().toString())
                .version(null)
                .name(base + COPY_SUFFIX)
                .variableValues(new LinkedHashMap<>(original.getVariableValues()))
                .favorite(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /** {@code template} is the configuration's template, or null when it has none (or it no longer exists). */
    public ConfigurationResponse toResponse(LlmConfiguration configuration, TemplateResponse template) {
        GenerationSettings settings = configuration.getSettings();
        return new ConfigurationResponse(
                configuration.getId(),
                configuration.getVersion(),
                configuration.getName(),
                configuration.getDescription(),
                configuration.getModelId(),
                new GenerationSettingsDto(settings.temperature(), settings.topP(), settings.maxTokens(),
                        settings.contextWindowSize(), settings.frequencyPenalty(), settings.presencePenalty()),
                configuration.getTemplateId(),
                template,
                configuration.getVariableValues(),
                resolve(template, configuration.getVariableValues()),
                configuration.isFavorite(),
                configuration.getCreatedAt(),
                configuration.getUpdatedAt());
    }

    // One entry per template variable, in the template's order: the user's value, or the default if it is blank.
    private static Map<String, String> resolve(TemplateResponse template, Map<String, String> values) {
        Map<String, String> resolved = new LinkedHashMap<>();
        if (template == null) {
            return resolved;
        }
        for (TemplateVariableDto variable : template.variables()) {
            String value = values.get(variable.name());
            if (value == null || value.isBlank()) {
                value = variable.defaultValue() == null ? "" : variable.defaultValue();
            }
            resolved.put(variable.name(), value);
        }
        return resolved;
    }

    private static GenerationSettings toSettings(GenerationSettingsDto settings) {
        return new GenerationSettings(settings.temperature(), settings.topP(), settings.maxTokens(),
                settings.contextWindowSize(), settings.frequencyPenalty(), settings.presencePenalty());
    }

    // A form's "no template" option may send an empty string: the same as no template.
    private static String toTemplateId(String templateId) {
        return templateId == null || templateId.isBlank() ? null : templateId;
    }

    private static Map<String, String> toValues(Map<String, String> values) {
        return values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
    }

    // MongoDB stores milliseconds: the answer to a save then equals what a later read returns.
    private static Instant now() {
        return Instant.now().truncatedTo(MILLIS);
    }
}
