package com.myassistant.config;

import com.myassistant.MyAssistantApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The signing secret: a short one, or the public placeholder of the wrapper's {@code .env.example}, stops the
 * application at startup, and the secret itself never appears in an error message or a log line.
 */
@ExtendWith(OutputCaptureExtension.class)
class JwtSecretTest {
    // 31 bytes: one short of the HS256 minimum.
    private static final String SHORT_SECRET = "short-secret-xyz-0123456789-abc";
    // The value `.env.example` ships (QA-3-1): long enough, but public in git, so anyone could sign tokens with it.
    private static final String ENV_EXAMPLE_PLACEHOLDER = "change-me-to-a-long-random-string-of-32-chars-or-more";

    @Test
    void shortSecretIsRefused() {
        MyAssistantProperties.Jwt jwt = new MyAssistantProperties.Jwt(SHORT_SECRET, Duration.ofHours(8));

        IllegalStateException exception = assertThrows(IllegalStateException.class, jwt::signingKey);

        assertTrue(exception.getMessage().contains("myassistant.jwt.secret"), exception.getMessage());
        assertFalse(exception.getMessage().contains(SHORT_SECRET));
    }

    @Test
    void missingSecretIsRefused() {
        assertThrows(IllegalStateException.class, new MyAssistantProperties.Jwt(null, Duration.ofHours(8))::signingKey);
        assertThrows(IllegalStateException.class, new MyAssistantProperties.Jwt("", Duration.ofHours(8))::signingKey);
    }

    @Test
    void secretOf32BytesIsAccepted() {
        MyAssistantProperties.Jwt jwt = new MyAssistantProperties.Jwt(SHORT_SECRET + "d", Duration.ofHours(8));

        assertEquals(32, jwt.signingKey().getEncoded().length);
    }

    @Test
    void secretIsNotInToString() {
        MyAssistantProperties properties = new MyAssistantProperties(
                new MyAssistantProperties.Jwt(SHORT_SECRET + "d", Duration.ofHours(8)));

        assertFalse(properties.toString().contains(SHORT_SECRET));
    }

    @Test
    void applicationDoesNotStartWithAShortSecretAndDoesNotLogIt(CapturedOutput output) {
        // No index creation: the application must fail on the secret, without waiting for a MongoDB.
        Exception exception = assertThrows(Exception.class, () -> SpringApplication.run(MyAssistantApplication.class,
                "--myassistant.jwt.secret=" + SHORT_SECRET,
                "--server.port=0",
                "--spring.data.mongodb.auto-index-creation=false"));

        Throwable rootCause = exception;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        assertTrue(rootCause instanceof IllegalStateException, "root cause: " + rootCause);
        assertTrue(rootCause.getMessage().contains("myassistant.jwt.secret"), rootCause.getMessage());
        assertFalse(output.getAll().contains(SHORT_SECRET), "the secret was printed");
    }

    @Test
    void placeholderSecretOfEnvExampleIsRefused() {
        MyAssistantProperties.Jwt jwt = new MyAssistantProperties.Jwt(ENV_EXAMPLE_PLACEHOLDER, Duration.ofHours(8));

        IllegalStateException exception = assertThrows(IllegalStateException.class, jwt::signingKey);

        assertTrue(exception.getMessage().contains("MYASSISTANT_JWT_SECRET"), exception.getMessage());
        assertTrue(exception.getMessage().contains("placeholder"), exception.getMessage());
        assertTrue(exception.getMessage().contains("openssl rand -base64 48"), exception.getMessage());
        assertFalse(exception.getMessage().contains(ENV_EXAMPLE_PLACEHOLDER));
    }

    @Test
    void placeholderSecretIsRefusedEvenWithSurroundingSpaces() {
        MyAssistantProperties.Jwt jwt = new MyAssistantProperties.Jwt(" " + ENV_EXAMPLE_PLACEHOLDER + "\n",
                Duration.ofHours(8));

        assertThrows(IllegalStateException.class, jwt::signingKey);
    }

    @Test
    void applicationDoesNotStartWithThePlaceholderSecret() {
        Exception exception = assertThrows(Exception.class, () -> SpringApplication.run(MyAssistantApplication.class,
                "--myassistant.jwt.secret=" + ENV_EXAMPLE_PLACEHOLDER,
                "--server.port=0",
                "--spring.data.mongodb.auto-index-creation=false"));

        Throwable rootCause = exception;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        assertTrue(rootCause instanceof IllegalStateException, "root cause: " + rootCause);
        assertTrue(rootCause.getMessage().contains("placeholder"), rootCause.getMessage());
    }
}
