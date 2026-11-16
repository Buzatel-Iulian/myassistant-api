package com.myassistant.configuration.dto;

import com.myassistant.configuration.LlmConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

// templateId and variableValues may be left out: an assistant without a template has no variables. favorite may be
// left out too (false); a Boolean, because Jackson 3 refuses a missing value for a primitive.
public record CreateConfigurationRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 500) String description,
        @NotBlank @Size(max = 200)
        @Pattern(regexp = LlmConfiguration.MODEL_ID_PATTERN, message = LlmConfiguration.MODEL_ID_MESSAGE) String modelId,
        @NotNull @Valid GenerationSettingsDto settings,
        String templateId,
        @Size(max = 20) Map<String, @NotNull @Size(max = 1000) String> variableValues,
        Boolean favorite) {
}
