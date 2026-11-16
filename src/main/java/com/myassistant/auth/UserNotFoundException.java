package com.myassistant.auth;

import com.myassistant.shared.error.NotFoundException;

public class UserNotFoundException extends NotFoundException {

    public UserNotFoundException(String userId) {
        super("No user with id " + userId);
    }
}
