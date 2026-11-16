package com.myassistant.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(@NotBlank @Email @Size(max = 254) String email,
                              @NotBlank @Size(min = 8, message = "must be at least 8 characters") @MaxBytes(72) String password,
                              @NotBlank @Size(max = 80) String displayName) {

    // The password stays out of logs and error messages.
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", displayName=" + displayName + "]";
    }
}
