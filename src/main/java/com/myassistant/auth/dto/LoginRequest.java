package com.myassistant.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String email, @NotBlank String password) {

    // The password stays out of logs and error messages.
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + "]";
    }
}
