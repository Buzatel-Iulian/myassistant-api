package com.myassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * The application's own settings, under {@code myassistant.*} in {@code application.yml}.
 */
@ConfigurationProperties(prefix = "myassistant")
public record MyAssistantProperties(Jwt jwt) {

    /**
     * The login token settings. {@code secret} is the HS256 signing key ({@code MYASSISTANT_JWT_SECRET}), {@code ttl}
     * how long a token stays valid.
     */
    public record Jwt(String secret, Duration ttl) {

        // HS256 needs a key of at least 256 bits; a shorter one would make every token easy to forge.
        static final int MIN_SECRET_BYTES = 32;

        // The value the wrapper's .env.example ships. It is long enough, but public in git: tokens signed with it could
        // be forged by anyone who has read the repository (QA-3-1). `make bootstrap` writes a random one instead.
        private static final String ENV_EXAMPLE_PLACEHOLDER = "change-me-to-a-long-random-string-of-32-chars-or-more";

        /**
         * The signing key. Called while the security beans are created, so a missing, short or placeholder secret
         * stops the application at startup. The message names the setting, never its value.
         */
        public SecretKey signingKey() {
            if (secret != null && secret.strip().equals(ENV_EXAMPLE_PLACEHOLDER)) {
                throw new IllegalStateException("myassistant.jwt.secret (environment variable MYASSISTANT_JWT_SECRET) "
                        + "is still the placeholder from .env.example, which is public. Run 'make bootstrap' to write "
                        + "a random one into .env, or generate one with: openssl rand -base64 48");
            }
            byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException("myassistant.jwt.secret (environment variable MYASSISTANT_JWT_SECRET) "
                        + "must be at least " + MIN_SECRET_BYTES + " bytes long. Generate one with: openssl rand -base64 48");
            }
            return new SecretKeySpec(bytes, "HmacSHA256");
        }

        // The record's default toString would print the secret, for example in a debug log of the bound settings.
        @Override
        public String toString() {
            return "Jwt[secret=<hidden>, ttl=" + ttl + "]";
        }
    }
}
