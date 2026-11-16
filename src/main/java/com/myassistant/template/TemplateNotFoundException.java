package com.myassistant.template;

import com.myassistant.shared.error.NotFoundException;

// Also thrown for another user's template: the answer must not reveal that it exists.
public class TemplateNotFoundException extends NotFoundException {

    public TemplateNotFoundException(String templateId) {
        super("No template with id " + templateId);
    }
}
