package com.myassistant.auth.dto;

import java.time.Instant;

/** The login token, sent back as {@code Authorization: Bearer <token>}, and the moment it stops being accepted. */
public record TokenResponse(String token, Instant expiresAt) {
}
