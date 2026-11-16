package com.myassistant.template.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

// variables may be left out: a template without placeholders needs none.
public record CreateTemplateRequest(@NotBlank @Size(max = 80) String name,
                                    @Size(max = 500) String description,
                                    @NotBlank @Size(max = 8000) String content,
                                    @Size(max = 20) List<@NotNull @Valid TemplateVariableDto> variables) {
}
