package com.myassistant.auth;

import com.myassistant.auth.dto.TokenResponse;
import com.myassistant.config.MyAssistantProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256;

/**
 * Issues the login tokens: HS256, signed with {@code myassistant.jwt.secret}, valid for {@code myassistant.jwt.ttl}.
 * The resource server in SecurityConfig checks them on every request.
 */
@Service
@RequiredArgsConstructor
public class JwtService {
    private final JwtEncoder jwtEncoder;
    private final MyAssistantProperties properties;

    public TokenResponse issueToken(String userId) {
        // Whole seconds: a JWT stores seconds, so the expiresAt we answer is exactly the token's exp.
        Instant issuedAt = Instant.now().truncatedTo(SECONDS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(properties.jwt().ttl()))
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(HS256).build(), claims)).getTokenValue();
        return new TokenResponse(token, claims.getExpiresAt());
    }
}
