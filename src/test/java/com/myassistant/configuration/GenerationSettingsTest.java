package com.myassistant.configuration;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test (no Spring): the limits of the WebLLM generation settings, embedded in {@link LlmConfiguration}, like
 * {@code VenueTest} in the ticketing app. The limits themselves are accepted, one step outside is refused.
 */
class GenerationSettingsTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private final GenerationSettings valid = new GenerationSettings(0.7, 0.95, 512, 4096, 0.0, 0.0);

    @Test
    void validSettingsHaveNoViolations() {
        assertTrue(validator.validate(valid).isEmpty());
    }

    @Test
    void limitsAreAccepted() {
        assertTrue(validator.validate(new GenerationSettings(0.0, 0.0, 1, 512, -2.0, -2.0)).isEmpty());
        assertTrue(validator.validate(new GenerationSettings(2.0, 1.0, 4096, 32768, 2.0, 2.0)).isEmpty());
    }

    // The answer must fit in the context window with at least one token left for the prompt (Phase 10 keeps the
    // history within contextWindowSize - maxTokens). The violation is reported on maxTokens, the field to change.
    @Test
    void maxTokensIsBelowContextWindowSize() {
        assertEquals(Set.of("maxTokens"), violated(new GenerationSettings(0.7, 0.95, 4096, 512, 0.0, 0.0)));
        assertEquals(Set.of("maxTokens"), violated(new GenerationSettings(0.7, 0.95, 512, 512, 0.0, 0.0)));
        assertTrue(validator.validate(new GenerationSettings(0.7, 0.95, 511, 512, 0.0, 0.0)).isEmpty());
        assertEquals("must be less than the context window size",
                validator.validate(new GenerationSettings(0.7, 0.95, 4096, 512, 0.0, 0.0)).iterator().next().getMessage());
    }

    @Test
    void temperatureIsBetween0And2() {
        assertEquals(Set.of("temperature"), violated(new GenerationSettings(-0.01, 0.95, 512, 4096, 0.0, 0.0)));
        assertEquals(Set.of("temperature"), violated(new GenerationSettings(2.01, 0.95, 512, 4096, 0.0, 0.0)));
    }

    @Test
    void topPIsBetween0And1() {
        assertEquals(Set.of("topP"), violated(new GenerationSettings(0.7, -0.1, 512, 4096, 0.0, 0.0)));
        assertEquals(Set.of("topP"), violated(new GenerationSettings(0.7, 1.01, 512, 4096, 0.0, 0.0)));
    }

    @Test
    void maxTokensIsBetween1And4096() {
        assertEquals(Set.of("maxTokens"), violated(new GenerationSettings(0.7, 0.95, 0, 4096, 0.0, 0.0)));
        assertEquals(Set.of("maxTokens"), violated(new GenerationSettings(0.7, 0.95, 4097, 4096, 0.0, 0.0)));
    }

    @Test
    void contextWindowSizeIsBetween512And32768() {
        assertEquals(Set.of("contextWindowSize"), violated(new GenerationSettings(0.7, 0.95, 256, 511, 0.0, 0.0)));
        assertEquals(Set.of("contextWindowSize"), violated(new GenerationSettings(0.7, 0.95, 512, 32769, 0.0, 0.0)));
    }

    @Test
    void penaltiesAreBetweenMinus2And2() {
        assertEquals(Set.of("frequencyPenalty", "presencePenalty"),
                violated(new GenerationSettings(0.7, 0.95, 512, 4096, -2.01, 2.01)));
        assertEquals(Set.of("frequencyPenalty", "presencePenalty"),
                violated(new GenerationSettings(0.7, 0.95, 512, 4096, 2.01, -2.01)));
    }

    @Test
    void everySettingIsRequired() {
        assertEquals(Set.of("temperature", "topP", "maxTokens", "contextWindowSize", "frequencyPenalty", "presencePenalty"),
                violated(new GenerationSettings(null, null, null, null, null, null)));
    }

    private Set<String> violated(GenerationSettings settings) {
        return validator.validate(settings).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
