package com.myassistant.configuration;

import com.myassistant.shared.error.BadRequestException;

import java.util.List;
import java.util.Map;

/** {@code variableValues} has keys that the chosen template does not declare (any key, when there is no template). */
public class UnknownVariablesException extends BadRequestException {

    public UnknownVariablesException(List<String> unknownVariables) {
        super("The template does not declare these variables: " + String.join(", ", unknownVariables) + ".",
                Map.of("unknownVariables", unknownVariables));
    }
}
