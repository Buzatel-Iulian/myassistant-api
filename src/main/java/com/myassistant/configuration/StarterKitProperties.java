package com.myassistant.configuration;

import com.myassistant.configuration.dto.CreateConfigurationRequest;
import com.myassistant.template.dto.CreateTemplateRequest;
import com.myassistant.template.dto.TemplateVariableDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The starter kit every new user gets ({@code myassistant.starter-kit} in {@code application.yml}), like
 * {@code TicketingProperties} in the ticketing app. The items are the API's own request DTOs, so their constraints are
 * checked at startup ({@code @Validated}), and so are the references between them (constructor): a wrong kit stops
 * the application instead of failing at a user's registration.
 */
@Validated
@ConfigurationProperties(prefix = "myassistant.starter-kit")
public record StarterKitProperties(List<@NotNull @Valid StarterTemplate> templates,
                                   List<@NotNull @Valid StarterConfiguration> configurations) {

    public StarterKitProperties {
        templates = templates == null ? List.of() : List.copyOf(templates);
        configurations = configurations == null ? List.of() : List.copyOf(configurations);
        Map<String, StarterTemplate> byKey = templates.stream()
                .collect(Collectors.toMap(StarterTemplate::key, template -> template));
        for (StarterConfiguration configuration : configurations) {
            Set<String> declared = Set.of();
            if (configuration.templateKey() != null) {
                StarterTemplate template = byKey.get(configuration.templateKey());
                if (template == null) {
                    throw new IllegalStateException("myassistant.starter-kit: configuration '"
                            + configuration.configuration().name() + "' refers to an unknown template key '"
                            + configuration.templateKey() + "'");
                }
                declared = template.declaredVariables();
            }
            Map<String, String> values = configuration.configuration().variableValues();
            for (String name : values == null ? Set.<String>of() : values.keySet()) {
                if (!declared.contains(name)) {
                    throw new IllegalStateException("myassistant.starter-kit: configuration '"
                            + configuration.configuration().name() + "' sets the undeclared variable '" + name + "'");
                }
            }
        }
    }

    /** A starter template; {@code key} lets a starter configuration refer to it before it has an id. */
    public record StarterTemplate(@NotBlank String key, @NotNull @Valid CreateTemplateRequest template) {

        Set<String> declaredVariables() {
            return template.variables() == null ? Set.of()
                    : template.variables().stream().map(TemplateVariableDto::name).collect(Collectors.toSet());
        }
    }

    /** A starter configuration; its template is the starter template {@code templateKey} (or none). */
    public record StarterConfiguration(String templateKey, @NotNull @Valid CreateConfigurationRequest configuration) {
    }
}
