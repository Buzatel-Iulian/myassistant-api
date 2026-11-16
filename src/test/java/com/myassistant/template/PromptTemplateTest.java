package com.myassistant.template;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test (no Spring): the constraints on the stored document, checked with a Jakarta {@link Validator}. The same
 * constraints run before every save (MongoConfig), so a bug in a mapper cannot store an invalid template.
 */
class PromptTemplateTest {
    private static final Instant NOW = Instant.parse("2030-01-01T08:00:00Z");

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private final PromptTemplate template = PromptTemplate.builder()
            .id("t1")
            .ownerId("u1")
            .name("Language tutor")
            .description("Corrects mistakes")
            .content("You are a {{language}} tutor.")
            .variables(List.of(new TemplateVariable("language", "Language", "English")))
            .createdAt(NOW)
            .updatedAt(NOW)
            .build();

    @Test
    void validTemplateHasNoViolations() {
        assertTrue(validator.validate(template).isEmpty());
    }

    @Test
    void ownerIsRequired() {
        assertEquals(Set.of("ownerId"), violatedProperties(template.toBuilder().ownerId(null).build()));
    }

    @Test
    void nameIsRequiredAndAtMost80Characters() {
        assertEquals(Set.of("name"), violatedProperties(template.toBuilder().name(" ").build()));
        assertEquals(Set.of("name"), violatedProperties(template.toBuilder().name("n".repeat(81)).build()));
        assertTrue(validator.validate(template.toBuilder().name("n".repeat(80)).build()).isEmpty());
    }

    @Test
    void contentIsRequiredAndAtMost8000Characters() {
        assertEquals(Set.of("content"), violatedProperties(template.toBuilder().content("").build()));
        assertEquals(Set.of("content"), violatedProperties(template.toBuilder().content("c".repeat(8001)).build()));
        assertTrue(validator.validate(template.toBuilder().content("c".repeat(8000)).build()).isEmpty());
    }

    @Test
    void descriptionIsAtMost500Characters() {
        assertEquals(Set.of("description"), violatedProperties(template.toBuilder().description("d".repeat(501)).build()));
    }

    @Test
    void variableNamesFollowThePattern() {
        assertEquals(Set.of("variables[0].name"), violatedProperties(withVariable("1st")));
        assertEquals(Set.of("variables[0].name"), violatedProperties(withVariable("has space")));
        assertEquals(Set.of("variables[0].name"), violatedProperties(withVariable("a".repeat(41))));
        assertEquals(Set.of("variables[0].name"), violatedProperties(withVariable(null)));
        assertTrue(validator.validate(withVariable("a" + "_1".repeat(19) + "b")).isEmpty());
    }

    @Test
    void atMost20Variables() {
        assertEquals(Set.of("variables"), violatedProperties(template.toBuilder().variables(variables(21)).build()));
        assertTrue(validator.validate(template.toBuilder().variables(variables(20)).build()).isEmpty());
    }

    private PromptTemplate withVariable(String name) {
        return template.toBuilder().variables(List.of(new TemplateVariable(name, null, null))).build();
    }

    private static List<TemplateVariable> variables(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> new TemplateVariable("v" + i, null, null)).toList();
    }

    private Set<String> violatedProperties(PromptTemplate invalid) {
        return validator.validate(invalid).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
