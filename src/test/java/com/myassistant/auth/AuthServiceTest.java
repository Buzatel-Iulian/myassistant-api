package com.myassistant.auth;

import com.myassistant.MongoPersistenceTest;
import com.myassistant.auth.dto.LoginRequest;
import com.myassistant.auth.dto.RegisterRequest;
import com.myassistant.auth.dto.TokenResponse;
import com.myassistant.auth.dto.UserResponse;
import com.myassistant.config.MyAssistantProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Duration;
import java.time.Instant;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The account rules against a real MongoDB: what is stored, the unique email, the login and its token. The cases are
 * listed in {@code docs/auth-cases.md}.
 */
@MongoPersistenceTest
@Import({AuthService.class, UserMapper.class, JwtService.class, AuthServiceTest.SecurityBeans.class})
class AuthServiceTest {
    private static final MyAssistantProperties PROPERTIES = new MyAssistantProperties(
            new MyAssistantProperties.Jwt("test-only-jwt-secret-not-for-any-real-use-0123456789", Duration.ofHours(8)));

    /** The beans SecurityConfig makes in the application, built here because this slice has no web security. */
    @TestConfiguration
    static class SecurityBeans {

        @Bean
        MyAssistantProperties myAssistantProperties() {
            return PROPERTIES;
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }

        @Bean
        JwtEncoder jwtEncoder() {
            return NimbusJwtEncoder.withSecretKey(PROPERTIES.jwt().signingKey()).build();
        }
    }

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void registerCreatesUser() {
        UserResponse created = authService.register(new RegisterRequest(" Ana@Example.COM ", "secret-pass", "Ana"));

        assertNotNull(created.id());
        assertEquals("ana@example.com", created.email());
        assertEquals("Ana", created.displayName());

        User stored = userRepository.findById(created.id()).orElseThrow();
        assertEquals("ana@example.com", stored.getEmail());
        assertNotEquals("secret-pass", stored.getPasswordHash());
        assertTrue(passwordEncoder.matches("secret-pass", stored.getPasswordHash()));
        assertNotNull(stored.getCreatedAt());
    }

    @Test
    void duplicateEmailIsConflict() {
        authService.register(new RegisterRequest("ana@example.com", "secret-pass", "Ana"));

        EmailAlreadyUsedException exception = assertThrows(EmailAlreadyUsedException.class,
                () -> authService.register(new RegisterRequest("ANA@example.com", "other-pass", "Another Ana")));

        assertEquals("An account with this email already exists.", exception.getMessage());
        assertEquals(1, userRepository.count());
    }

    @Test
    void loginReturnsToken() {
        UserResponse user = authService.register(new RegisterRequest("ana@example.com", "secret-pass", "Ana"));
        Instant before = Instant.now().truncatedTo(SECONDS);

        TokenResponse token = authService.login(new LoginRequest("ana@example.com", "secret-pass"));

        Jwt decoded = NimbusJwtDecoder.withSecretKey(PROPERTIES.jwt().signingKey()).build().decode(token.token());
        assertEquals(user.id(), decoded.getSubject());
        assertEquals(decoded.getExpiresAt(), token.expiresAt());
        Duration ttl = Duration.between(before, token.expiresAt());
        assertTrue(ttl.compareTo(Duration.ofHours(8)) >= 0 && ttl.compareTo(Duration.ofHours(8).plusMinutes(1)) < 0,
                "the token lives 8 hours, but lives " + ttl);
    }

    @Test
    void loginIgnoresEmailCase() {
        authService.register(new RegisterRequest("ana@example.com", "secret-pass", "Ana"));

        assertNotNull(authService.login(new LoginRequest(" ANA@Example.com", "secret-pass")).token());
    }

    @Test
    void wrongCredentialsAreUnauthorized() {
        authService.register(new RegisterRequest("ana@example.com", "secret-pass", "Ana"));

        InvalidCredentialsException wrongPassword = assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("ana@example.com", "wrong-pass")));
        InvalidCredentialsException unknownEmail = assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("nobody@example.com", "secret-pass")));

        assertEquals("Invalid email or password.", wrongPassword.getMessage());
        assertEquals(wrongPassword.getMessage(), unknownEmail.getMessage());
    }

    @Test
    void meReturnsCurrentUser() {
        UserResponse created = authService.register(new RegisterRequest("ana@example.com", "secret-pass", "Ana"));

        assertEquals(created, authService.findById(created.id()));
    }

    @Test
    void unknownUserIsNotFound() {
        assertThrows(UserNotFoundException.class, () -> authService.findById("no-such-user"));
    }
}
