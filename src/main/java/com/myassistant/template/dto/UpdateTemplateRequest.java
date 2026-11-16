package com.myassistant.template.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Every field of the template (PUT replaces it), plus the {@code version} the client read. A version that is no
 * longer the stored one means someone saved in between: 409.
 */
public record UpdateTemplateRequest(@NotBlank @Size(max = 80) String name,
                                    @Size(max = 500) String description,
                                    @NotBlank @Size(max = 8000) String content,
                                    @Size(max = 20) List<@NotNull @Valid TemplateVariableDto> variables,
                                    @NotNull Long version) {
}
