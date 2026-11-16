package com.myassistant.config;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static java.time.temporal.ChronoUnit.HOURS;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole application with a real MongoDB: real tokens from a real login, checked by the real security filter. The
 * cases are listed in {@code docs/auth-cases.md}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecurityIntegrationTest {
    private static final String TOKEN_REQUIRED = "A valid login token is required. Log in and try again.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void realTokenFromLoginOpensMe() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String registered = mockMvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content("""
                        {"email": "%s", "password": "secret-pass", "displayName": "Ana"}""".formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String userId = JsonPath.read(registered, "$.id");

        String login = mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content("""
                        {"email": "%s", "password": "secret-pass"}""".formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(login, "$.token");

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.displayName").value("Ana"));
    }

    @Test
    void wrongPasswordIsUnauthorizedProblemDetail() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON).content("""
                        {"email": "%s", "password": "secret-pass", "displayName": "Ana"}""".formatted(email)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON).content("""
                        {"email": "%s", "password": "wrong-pass"}""".formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid email or password."));
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(TOKEN_REQUIRED));

        // Every other path is protected too, even one that does not exist yet.
        mockMvc.perform(get("/api/templates"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON));
    }

    @Test
    void expiredTokenIsUnauthorized() throws Exception {
        Instant twoHoursAgo = Instant.now().minus(2, HOURS);
        String expired = token(jwtEncoder, twoHoursAgo, twoHoursAgo.plus(1, HOURS));

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(TOKEN_REQUIRED));
    }

    @Test
    void tokenSignedWithAnotherKeyIsUnauthorized() throws Exception {
        byte[] otherSecret = "another-secret-that-the-api-does-not-know-0123456789".getBytes(StandardCharsets.UTF_8);
        JwtEncoder otherEncoder = NimbusJwtEncoder.withSecretKey(new SecretKeySpec(otherSecret, "HmacSHA256")).build();
        Instant now = Instant.now();

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token(otherEncoder, now, now.plus(1, HOURS))))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON));

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON));
    }

    @Test
    void publicEndpointsNeedNoToken() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/auth/register'].post").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post").exists())
                .andExpect(jsonPath("$.paths['/api/users/me'].get").exists());

        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk());
    }

    private static String token(JwtEncoder encoder, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("some-user-id")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(HS256).build(), claims)).getTokenValue();
    }
}
