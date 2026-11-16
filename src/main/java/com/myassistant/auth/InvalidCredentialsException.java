package com.myassistant.auth;

import org.springframework.security.authentication.BadCredentialsException;

/**
 * A failed login. The same message for an unknown email and a wrong password, so the answer does not tell which
 * addresses have an account. {@code GlobalExceptionHandler} answers it with 401.
 */
public class InvalidCredentialsException extends BadCredentialsException {

    public InvalidCredentialsException() {
        super("Invalid email or password.");
    }
}
