package com.myassistant.shared.error;

/**
 * Base class for "the thing you asked for does not exist" (or belongs to someone else, which the API answers the
 * same way). {@link GlobalExceptionHandler} answers it with 404.
 */
public abstract class NotFoundException extends RuntimeException {

    protected NotFoundException(String message) {
        super(message);
    }
}
