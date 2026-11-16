package com.myassistant.auth;

import com.myassistant.shared.error.ConflictException;

import java.util.Map;

public class EmailAlreadyUsedException extends ConflictException {

    // The email itself is not repeated: the client knows which one it sent.
    public EmailAlreadyUsedException() {
        super("An account with this email already exists.", Map.of());
    }
}
