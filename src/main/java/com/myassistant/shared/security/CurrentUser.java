package com.myassistant.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * The logged-in user of the current request: the {@code sub} of the login token, which is the user id. Controllers
 * pass it to their service as the owner id of every query.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static String id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            return token.getToken().getSubject();
        }
        // Only reachable if an endpoint that needs a user was left public in SecurityConfig: a bug, hence a 500.
        throw new IllegalStateException("No logged-in user for this request");
    }
}
