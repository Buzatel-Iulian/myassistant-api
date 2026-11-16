package com.myassistant.configuration;

import com.myassistant.MongoPersistenceTest;
import com.myassistant.auth.AuthService;
import com.myassistant.auth.JwtService;
import com.myassistant.auth.UserMapper;
import com.myassistant.auth.UserRepository;
import com.myassistant.auth.dto.LoginRequest;
import com.myassistant.auth.dto.RegisterRequest;
import com.myassistant.auth.dto.UserResponse;
import com.myassistant.config.MyAssistantProperties;
import com.myassistant.configuration.dto.ConfigurationResponse;
import com.myassistant.template.TemplateMapper;
import com.myassistant.template.TemplateRepository;
import com.myassistant.template.TemplateService;
import com.myassistant.template.dto.TemplateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Registration creates the starter kit of {@code myassistant.starter-kit} (the real {@code application.yml}) for the
 * new user, through the same services the API uses; a failure while creating it does not undo the registration.
 * The cases are listed in {@code docs/configuration-cases.md}.
 */
@MongoPersistenceTest
@Import({AuthService.class, UserMapper.class, JwtService.class, StarterKitTest.SecurityBeans.class,
        StarterKit.class, ConfigurationService.class, ConfigurationMapper.class, ConfigurationTemplateUsage.class,
        TemplateService.class, TemplateMapper.class})
class StarterKitTest {
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
    private TemplateService templateService;

    @MockitoSpyBean
    private ConfigurationService configurationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TemplateRepository templateRepository;

    @Autowired
    private ConfigurationRepository configurationRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        templateRepository.deleteAll();
        configurationRepository.deleteAll();
    }

    @Test
    void registrationCreatesStarterKit() {
        UserResponse ana = authService.register(new RegisterRequest("ana@example.com", "secret-pass", "Ana"));
        UserResponse bob = authService.register(new RegisterRequest("bob@example.com", "secret-pass", "Bob"));

        List<TemplateResponse> templates = templateService.findAll(ana.id());
        assertEquals(List.of("Code helper", "General assistant"), templates.stream().map(TemplateResponse::name).toList());

        List<ConfigurationResponse> configurations = configurationService.findAll(ana.id());
        assertEquals(List.of("General assistant", "Code helper"),
                configurations.stream().map(ConfigurationResponse::name).toList());

        ConfigurationResponse general = configurations.get(0);
        assertTrue(general.favorite());
        assertEquals("Qwen2.5-0.5B-Instruct-q4f16_1-MLC", general.modelId());
        assertNotNull(general.template());
        assertEquals("General assistant", general.template().name());
        assertFalse(general.resolvedVariableValues().isEmpty());

        ConfigurationResponse code = configurations.get(1);
        assertFalse(code.favorite());
        assertEquals("Qwen2.5-Coder-1.5B-Instruct-q4f16_1-MLC", code.modelId());
        assertNotNull(code.template());
        assertEquals("Code helper", code.template().name());

        // Bob gets his own kit, not Ana's items.
        assertEquals(2, templateService.findAll(bob.id()).size());
        assertEquals(2, configurationService.findAll(bob.id()).size());
        assertTrue(configurationService.findAll(bob.id()).stream()
                .noneMatch(configuration -> configurations.stream().anyMatch(c -> c.id().equals(configuration.id()))));
    }

    @Test
    void registrationSurvivesStarterKitFailure() {
        doThrow(new DataAccessResourceFailureException("database down")).when(configurationService).create(any(), any());

        UserResponse ana = authService.register(new RegisterRequest("ana@example.com", "secret-pass", "Ana"));

        assertNotNull(ana.id());
        assertTrue(userRepository.existsById(ana.id()));
        assertNotNull(authService.login(new LoginRequest("ana@example.com", "secret-pass")).token());
        assertEquals(0, configurationRepository.count());
    }
}
