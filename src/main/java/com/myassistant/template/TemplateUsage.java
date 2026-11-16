package com.myassistant.template;

import java.util.List;

/**
 * The question "which of this user's configurations use this template?", asked before a template is deleted.
 * <p>
 * A port: the template feature declares it, the configuration feature answers it (Phase 5), so {@code template}
 * never depends on {@code configuration}. Until a bean implements it, {@link TemplateService} treats every template
 * as unused.
 */
public interface TemplateUsage {

    /** The names of the owner's configurations that use the template; empty when none does. */
    List<String> configurationNamesUsing(String ownerId, String templateId);
}
