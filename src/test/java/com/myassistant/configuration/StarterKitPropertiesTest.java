package com.myassistant.configuration;

import com.myassistant.configuration.StarterKitProperties.StarterConfiguration;
import com.myassistant.configuration.StarterKitProperties.StarterTemplate;
import com.myassistant.configuration.dto.CreateConfigurationRequest;
import com.myassistant.configuration.dto.GenerationSettingsDto;
import com.myassistant.template.dto.CreateTemplateRequest;
import com.myassistant.template.dto.TemplateVariableDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test (no Spring): the starter-kit settings refuse references that could only fail later, at a user's
 * registration, so a wrong {@code application.yml} stops the application at startup instead.
 */
class StarterKitPropertiesTest {
    private static final StarterTemplate GENERAL = new StarterTemplate("general",
            new CreateTemplateRequest("General assistant", null, "Answer in {{language}}.",
                    List.of(new TemplateVariableDto("language", "Language", "English"))));

    @Test
    void validKitIsAccepted() {
        StarterKitProperties properties = new StarterKitProperties(List.of(GENERAL),
                List.of(configuration("general", Map.of("language", "French")), configuration(null, Map.of())));

        assertEquals(2, properties.configurations().size());
    }

    @Test
    void missingListsAreEmpty() {
        StarterKitProperties properties = new StarterKitProperties(null, null);

        assertTrue(properties.templates().isEmpty());
        assertTrue(properties.configurations().isEmpty());
    }

    @Test
    void unknownTemplateKeyIsRejected() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new StarterKitProperties(List.of(GENERAL), List.of(configuration("coding", Map.of()))));

        assertTrue(exception.getMessage().contains("coding"), exception.getMessage());
    }

    @Test
    void undeclaredVariableIsRejected() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new StarterKitProperties(List.of(GENERAL), List.of(configuration("general", Map.of("tone", "warm")))));
        assertThrows(IllegalStateException.class,
                () -> new StarterKitProperties(List.of(GENERAL), List.of(configuration(null, Map.of("language", "x")))));

        assertTrue(exception.getMessage().contains("tone"), exception.getMessage());
    }

    private static StarterConfiguration configuration(String templateKey, Map<String, String> values) {
        return new StarterConfiguration(templateKey, new CreateConfigurationRequest("General assistant", null,
                "Qwen2.5-0.5B-Instruct-q4f16_1-MLC", new GenerationSettingsDto(0.7, 0.95, 512, 4096, 0.0, 0.0),
                null, values, true));
    }
}
