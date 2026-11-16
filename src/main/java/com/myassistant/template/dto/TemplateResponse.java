package com.myassistant.template.dto;

import java.time.Instant;
import java.util.List;

// The owner id is left out: it is always the caller.
public record TemplateResponse(String id,
                               long version,
                               String name,
                               String description,
                               String content,
                               List<TemplateVariableDto> variables,
                               Instant createdAt,
                               Instant updatedAt) {
}
