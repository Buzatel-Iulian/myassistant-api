package com.myassistant.template.dto;

import com.myassistant.template.TemplateVariable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A variable as the client sends and receives it. The label and the default value are optional. */
public record TemplateVariableDto(
        @NotNull @Pattern(regexp = TemplateVariable.NAME_PATTERN, message = TemplateVariable.NAME_MESSAGE) String name,
        @Size(max = 80) String label,
        @Size(max = 1000) String defaultValue) {
}
