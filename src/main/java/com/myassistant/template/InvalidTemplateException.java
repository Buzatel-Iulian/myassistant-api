package com.myassistant.template;

import com.myassistant.shared.error.BadRequestException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The content uses placeholders that are not declared as variables, or two variables have the same name. The 400
 * lists the names, so the client can point at them.
 */
public class InvalidTemplateException extends BadRequestException {

    public InvalidTemplateException(List<String> unknownPlaceholders, List<String> duplicateVariables) {
        super(message(unknownPlaceholders, duplicateVariables),
                Map.of("unknownPlaceholders", unknownPlaceholders, "duplicateVariables", duplicateVariables));
    }

    private static String message(List<String> unknownPlaceholders, List<String> duplicateVariables) {
        List<String> sentences = new ArrayList<>();
        if (!unknownPlaceholders.isEmpty()) {
            // Written back as {{...}}, so a name with spaces in it stays readable.
            sentences.add("The content uses placeholders that are not declared as variables: "
                    + String.join(", ", unknownPlaceholders.stream().map(name -> "{{" + name + "}}").toList()) + ".");
        }
        if (!duplicateVariables.isEmpty()) {
            sentences.add("Variable names must be unique; declared more than once: "
                    + String.join(", ", duplicateVariables) + ".");
        }
        return String.join(" ", sentences);
    }
}
