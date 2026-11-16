package com.myassistant.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
import java.util.Map;

// A user's assistant: model, generation settings, an optional template and the values for its variables.
// The constraints are checked before every save (MongoConfig). No toString: the values are the user's own text.
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@EqualsAndHashCode(of = "id")
@Document(collection = "configurations")
public class LlmConfiguration {
    // The format of a WebLLM model id. The catalog itself lives in the web client, so new models need no API change.
    public static final String MODEL_ID_PATTERN = ".+-MLC";
    public static final String MODEL_ID_MESSAGE = "must be a WebLLM model id ending in -MLC";

    @Id
    private String id;

    // Optimistic locking, as in PromptTemplate: null before the first save, then the version the client read.
    @Version
    private Long version;

    @Indexed
    @NotBlank
    private String ownerId;

    @NotBlank
    @Size(max = 80)
    private String name;

    @Size(max = 500)
    private String description;

    @NotBlank
    @Size(max = 200)
    @Pattern(regexp = MODEL_ID_PATTERN, message = MODEL_ID_MESSAGE)
    private String modelId;

    @NotNull
    @Valid
    private GenerationSettings settings;

    // One of the owner's templates, or null. Checked through TemplateService when saved; it may still point at a
    // template deleted in between (see ConfigurationService).
    private String templateId;

    // Only what the user typed; the template's defaults fill the rest when the configuration is read.
    @NotNull
    @Size(max = 20)
    private Map<String, @NotNull @Size(max = 1000) String> variableValues;

    private boolean favorite;

    @NotNull
    private Instant createdAt;

    @NotNull
    private Instant updatedAt;
}
