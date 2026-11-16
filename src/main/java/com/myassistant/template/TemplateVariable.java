package com.myassistant.template;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A variable a template declares: its {@code {{name}}} may appear in the content. Embedded in {@link PromptTemplate}.
 * The label (shown in the form) and the default value are optional.
 */
public record TemplateVariable(@NotNull @Pattern(regexp = NAME_PATTERN, message = NAME_MESSAGE) String name,
                               @Size(max = 80) String label,
                               @Size(max = 1000) String defaultValue) {

    public static final String NAME_PATTERN = "[a-zA-Z][a-zA-Z0-9_]{0,39}";
    public static final String NAME_MESSAGE =
            "must start with a letter and contain only letters, digits and _ (at most 40 characters)";
}
