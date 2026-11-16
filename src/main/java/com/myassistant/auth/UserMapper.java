package com.myassistant.auth;

import com.myassistant.auth.dto.RegisterRequest;
import com.myassistant.auth.dto.UserResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Translates between the {@link User} entity and the account DTOs. The password hash never leaves this direction.
 */
@Component
public class UserMapper {

    /** The form an email is stored and looked up in: an address is the same account in any letter case. */
    public String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public User toEntity(RegisterRequest request, String passwordHash) {
        return User.builder()
                .id(UUID.randomUUID().toString())
                .email(normalizeEmail(request.email()))
                .passwordHash(passwordHash)
                .displayName(request.displayName().trim())
                .createdAt(Instant.now())
                .build();
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName());
    }
}
