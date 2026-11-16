package com.myassistant.configuration;

import com.myassistant.template.TemplateUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Answers the template feature's {@link TemplateUsage} port, so a template that assistants still use is not deleted.
 * <p>
 * The check and the delete are two steps (no transaction), so a configuration saved between them can still point
 * at a deleted template. That window is tiny for one user's own data, and the configuration side tolerates it: the
 * configuration keeps loading, without a template (see {@link ConfigurationService}).
 */
@Component
@RequiredArgsConstructor
class ConfigurationTemplateUsage implements TemplateUsage {
    private final ConfigurationRepository configurationRepository;

    @Override
    public List<String> configurationNamesUsing(String ownerId, String templateId) {
        return configurationRepository.findByOwnerIdAndTemplateId(ownerId, templateId).stream()
                .map(LlmConfiguration::getName)
                .sorted(ConfigurationService.BY_NAME)
                .toList();
    }
}
