package com.myassistant.shared.error;

import java.util.Map;

/**
 * Base class for "the request is well-formed, but a business rule refuses its content", a rule that Bean Validation
 * cannot express on one field (for example a text that refers to names declared in another field).
 * {@link GlobalExceptionHandler} answers it with 400, and adds the {@link #properties()} to the problem detail.
 */
public abstract class BadRequestException extends RuntimeException {

    private final Map<String, Object> properties;

    protected BadRequestException(String message, Map<String, Object> properties) {
        super(message);
        this.properties = Map.copyOf(properties);
    }

    public Map<String, Object> properties() {
        return properties;
    }
}
