package com.myassistant.template;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

// A user's reusable system prompt, one document in the templates collection with its variables embedded.
// The constraints are checked before every save (MongoConfig). No toString: the content is the user's own text.
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@EqualsAndHashCode(of = "id")
@Document(collection = "templates")
public class PromptTemplate {
    @Id
    private String id;

    // Optimistic locking. A wrapper type on purpose: null means "never saved", so the first save is an insert even
    // though the id is set before it; every later save must carry the version that was read (stale → 409).
    @Version
    private Long version;

    // Every query filters on it (TemplateRepository), hence the index.
    @Indexed
    @NotBlank
    private String ownerId;

    @NotBlank
    @Size(max = 80)
    private String name;

    @Size(max = 500)
    private String description;

    @NotBlank
    @Size(max = 8000)
    private String content;

    @NotNull
    @Size(max = 20)
    private List<@NotNull @Valid TemplateVariable> variables;

    @NotNull
    private Instant createdAt;

    @NotNull
    private Instant updatedAt;
}
