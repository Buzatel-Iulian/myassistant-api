package com.myassistant.configuration;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test (no Spring): the constraints on the stored document, checked with a Jakarta {@link Validator}. The same
 * constraints run before every save (MongoConfig), so a bug in a mapper cannot store an invalid configuration.
 */
class LlmConfigurationTest {
    private static final Instant NOW = Instant.parse("2030-01-01T08:00:00Z");

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private final LlmConfiguration configuration = LlmConfiguration.builder()
            .id("c1")
            .ownerId("u1")
            .name("English tutor")
            .description("Daily English practice")
            .modelId("Qwen2.5-1.5B-Instruct-q4f16_1-MLC")
            .settings(new GenerationSettings(0.7, 0.95, 512, 4096, 0.0, 0.0))
            .templateId("t1")
            .variableValues(Map.of("language", "English"))
            .favorite(true)
            .createdAt(NOW)
            .updatedAt(NOW)
            .build();

    @Test
    void validConfigurationHasNoViolations() {
        assertTrue(validator.validate(configuration).isEmpty());
    }

    @Test
    void templateIsOptional() {
        assertTrue(validator.validate(configuration.toBuilder().templateId(null).variableValues(Map.of()).build()).isEmpty());
    }

    @Test
    void ownerIsRequired() {
        assertEquals(Set.of("ownerId"), violated(configuration.toBuilder().ownerId(" ").build()));
    }

    @Test
    void nameIsRequiredAndAtMost80Characters() {
        assertEquals(Set.of("name"), violated(configuration.toBuilder().name("").build()));
        assertEquals(Set.of("name"), violated(configuration.toBuilder().name("n".repeat(81)).build()));
        assertTrue(validator.validate(configuration.toBuilder().name("n".repeat(80)).build()).isEmpty());
    }

    @Test
    void descriptionIsAtMost500Characters() {
        assertEquals(Set.of("description"), violated(configuration.toBuilder().description("d".repeat(501)).build()));
    }

    @Test
    void modelIdIsAWebLlmId() {
        assertEquals(Set.of("modelId"), violated(configuration.toBuilder().modelId(" ").build()));
        assertEquals(Set.of("modelId"), violated(configuration.toBuilder().modelId(null).build()));
        assertEquals(Set.of("modelId"), violated(configuration.toBuilder().modelId("gpt-4o").build()));
        assertEquals(Set.of("modelId"), violated(configuration.toBuilder().modelId("-MLC").build()));
        assertEquals(Set.of("modelId"), violated(configuration.toBuilder().modelId("Qwen-MLC-q4").build()));
        assertEquals(Set.of("modelId"), violated(configuration.toBuilder().modelId("m".repeat(197) + "-MLC").build()));
    }

    @Test
    void settingsAreRequiredAndValidated() {
        assertEquals(Set.of("settings"), violated(configuration.toBuilder().settings(null).build()));
        assertEquals(Set.of("settings.temperature"), violated(configuration.toBuilder()
                .settings(new GenerationSettings(3.0, 0.95, 512, 4096, 0.0, 0.0)).build()));
        assertEquals(Set.of("settings.maxTokens"), violated(configuration.toBuilder()
                .settings(new GenerationSettings(0.7, 0.95, 4096, 512, 0.0, 0.0)).build()));
    }

    @Test
    void variableValuesAreAtMost20ShortValues() {
        Map<String, String> many = new HashMap<>();
        IntStream.rangeClosed(1, 21).forEach(i -> many.put("v" + i, "x"));
        Map<String, String> withNull = new HashMap<>();
        withNull.put("language", null);

        assertEquals(Set.of("variableValues"), violated(configuration.toBuilder().variableValues(many).build()));
        assertEquals(1, validator.validate(configuration.toBuilder()
                .variableValues(Map.of("language", "x".repeat(1001))).build()).size());
        assertEquals(1, validator.validate(configuration.toBuilder().variableValues(withNull).build()).size());
        assertEquals(Set.of("variableValues"), violated(configuration.toBuilder().variableValues(null).build()));
    }

    @Test
    void timestampsAreRequired() {
        assertEquals(Set.of("createdAt", "updatedAt"),
                violated(configuration.toBuilder().createdAt(null).updatedAt(null).build()));
    }

    private Set<String> violated(LlmConfiguration invalid) {
        return validator.validate(invalid).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
