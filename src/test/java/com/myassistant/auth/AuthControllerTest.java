package com.myassistant.auth;

import com.myassistant.auth.dto.LoginRequest;
import com.myassistant.auth.dto.RegisterRequest;
import com.myassistant.auth.dto.TokenResponse;
import com.myassistant.auth.dto.UserResponse;
import com.myassistant.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller test ({@code @WebMvcTest}, the service is a mock): the HTTP side of registering, logging in and reading
 * the current user, behind the real {@link SecurityConfig}. The cases are listed in {@code docs/auth-cases.md}.
 */
@WebMvcTest({AuthController.class, UserController.class})
@Import(SecurityConfig.class)
class AuthControllerTest {
    private static final Instant EXPIRES_AT = Instant.parse("2030-01-01T08:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void registerCreatesUser() throws Exception {
        when(authService.register(new RegisterRequest("ana@example.com", "secret-pass", "Ana")))
                .thenReturn(new UserResponse("u1", "ana@example.com", "Ana"));

        mockMvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content("""
                        {"email": "ana@example.com", "password": "secret-pass", "displayName": "Ana"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("u1"))
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.displayName").value("Ana"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void duplicateEmailIsConflict() throws Exception {
        when(authService.register(any())).thenThrow(new EmailAlreadyUsedException());

        mockMvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content("""
                        {"email": "ana@example.com", "password": "secret-pass", "displayName": "Ana"}"""))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("An account with this email already exists."));
    }

    @Test
    void invalidRegistrationIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content("""
                        {"email": "not-an-email", "password": "short", "displayName": " "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.displayName").exists());

        mockMvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content("""
                        {"password": "secret-pass", "displayName": "Ana"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());

        verify(authService, never()).register(any());
    }

    // BCrypt reads at most 72 bytes: a longer password would be cut silently, so it is refused. 40 x "é" is 80 bytes.
    @Test
    void passwordLongerThan72BytesIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content("""
                        {"email": "ana@example.com", "password": "%s", "displayName": "Ana"}""".formatted("é".repeat(40))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());

        verify(authService, never()).register(any());
    }

    @Test
    void loginReturnsToken() throws Exception {
        when(authService.login(new LoginRequest("ana@example.com", "secret-pass")))
                .thenReturn(new TokenResponse("header.payload.signature", EXPIRES_AT));

        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content("""
                        {"email": "ana@example.com", "password": "secret-pass"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("header.payload.signature"))
                .andExpect(jsonPath("$.expiresAt").value("2030-01-01T08:00:00Z"));
    }

    @Test
    void wrongCredentialsAreUnauthorized() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content("""
                        {"email": "ana@example.com", "password": "wrong-pass"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Invalid email or password."));
    }

    @Test
    void incompleteLoginIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content("""
                        {"email": "ana@example.com"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());

        verify(authService, never()).login(any());
    }

    @Test
    void meReturnsCurrentUser() throws Exception {
        when(authService.findById("u1")).thenReturn(new UserResponse("u1", "ana@example.com", "Ana"));

        mockMvc.perform(get("/api/users/me").with(jwt().jwt(token -> token.subject("u1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("u1"))
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.displayName").value("Ana"));
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("A valid login token is required. Log in and try again."));

        verifyNoInteractions(authService);
    }

    @Test
    void invalidTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("A valid login token is required. Log in and try again."));

        verifyNoInteractions(authService);
    }
}
