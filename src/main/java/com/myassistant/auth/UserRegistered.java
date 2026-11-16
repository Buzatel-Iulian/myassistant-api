package com.myassistant.auth;

/**
 * Published by {@link AuthService} once a new account is saved. Other features react to it (the configuration
 * feature creates the starter kit) without {@code auth} depending on them.
 */
public record UserRegistered(String userId) {
}
