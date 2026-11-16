package com.myassistant.template;

import com.myassistant.shared.error.ConflictException;

import java.util.List;
import java.util.Map;

public class TemplateInUseException extends ConflictException {

    public TemplateInUseException(List<String> configurationNames) {
        super("This template is used by: " + String.join(", ", configurationNames)
                        + ". Change or delete these assistants first.",
                Map.of("configurations", configurationNames));
    }
}
