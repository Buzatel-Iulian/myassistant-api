package com.myassistant.configuration.dto;

import com.myassistant.template.dto.TemplateResponse;

import java.time.Instant;
import java.util.Map;

/**
 * A configuration with everything the web client needs to start a chat in one call: the template is embedded
 * ({@code null} without one, or when it was deleted meanwhile), {@code variableValues} is what the user typed (the
 * edit form), and {@code resolvedVariableValues} has one value per template variable, the template's default where
 * the user left it empty (the rendered system prompt). The owner id is left out: it is always the caller.
 */
public record ConfigurationResponse(String id,
                                    long version,
                                    String name,
                                    String description,
                                    String modelId,
                                    GenerationSettingsDto settings,
                                    String templateId,
                                    TemplateResponse template,
                                    Map<String, String> variableValues,
                                    Map<String, String> resolvedVariableValues,
                                    boolean favorite,
                                    Instant createdAt,
                                    Instant updatedAt) {
}
