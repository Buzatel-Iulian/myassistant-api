package com.myassistant.configuration;

import com.myassistant.auth.UserRegistered;
import com.myassistant.configuration.StarterKitProperties.StarterConfiguration;
import com.myassistant.configuration.StarterKitProperties.StarterTemplate;
import com.myassistant.configuration.dto.CreateConfigurationRequest;
import com.myassistant.template.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Gives every new user the starter templates and assistants of {@link StarterKitProperties}, so the app is usable
 * right after registration. Data here is per user, so this replaces the ticketing app's global {@code DataSeeder}.
 * <p>
 * It listens to {@link UserRegistered}, so {@code auth} knows nothing about templates or configurations. The items
 * go through {@link TemplateService} and {@link ConfigurationService}: the same rules as the user's own items.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(StarterKitProperties.class)
class StarterKit {
    private final StarterKitProperties properties;
    private final TemplateService templateService;
    private final ConfigurationService configurationService;

    // Runs in the registering request's thread. The account matters more than its sample data: MongoDB here has no
    // transactions to undo the user with, so a failure is logged and the registration still succeeds (with the part
    // of the kit that was created; the user can create the rest).
    @EventListener
    void onUserRegistered(UserRegistered event) {
        try {
            create(event.userId());
        } catch (RuntimeException exception) {
            log.warn("Starter kit not (fully) created for user {}", event.userId(), exception);
        }
    }

    private void create(String userId) {
        Map<String, String> templateIds = new HashMap<>();
        for (StarterTemplate template : properties.templates()) {
            templateIds.put(template.key(), templateService.create(userId, template.template()).id());
        }
        for (StarterConfiguration starter : properties.configurations()) {
            CreateConfigurationRequest c = starter.configuration();
            configurationService.create(userId, new CreateConfigurationRequest(c.name(), c.description(), c.modelId(),
                    c.settings(), starter.templateKey() == null ? null : templateIds.get(starter.templateKey()),
                    c.variableValues(), c.favorite()));
        }
    }
}
