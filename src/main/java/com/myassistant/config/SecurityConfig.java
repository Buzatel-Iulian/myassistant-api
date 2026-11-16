package com.myassistant.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

import static org.springframework.security.config.Customizer.withDefaults;
import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;
import static org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256;

/**
 * Who may call what. Registering, logging in, Swagger and health are public; everything else needs a valid login
 * token ({@code Authorization: Bearer <JWT>}), checked by Spring Security's OAuth2 resource server.
 * <p>
 * Stateless: no session and no cookie, the token comes with every request. That is also why CSRF protection is off:
 * a browser never sends the token on its own, so another site cannot make it act for the user.
 */
@Configuration
@EnableConfigurationProperties(MyAssistantProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        AuthenticationEntryPoint problemDetailEntryPoint = problemDetailEntryPoint(exceptionResolver);
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/auth/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
                                "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(withDefaults())
                        .authenticationEntryPoint(problemDetailEntryPoint))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(problemDetailEntryPoint))
                .build();
    }

    // The standard bearer entry point sets 401 and the WWW-Authenticate header; the body is then written by
    // GlobalExceptionHandler, so a missing or expired token is a problem detail like every other error.
    private static AuthenticationEntryPoint problemDetailEntryPoint(HandlerExceptionResolver exceptionResolver) {
        BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
        return (request, response, exception) -> {
            bearerEntryPoint.commence(request, response, exception);
            exceptionResolver.resolveException(request, response, null, exception);
        };
    }

    @Bean
    public JwtDecoder jwtDecoder(MyAssistantProperties properties) {
        return NimbusJwtDecoder.withSecretKey(properties.jwt().signingKey()).macAlgorithm(HS256).build();
    }

    @Bean
    public JwtEncoder jwtEncoder(MyAssistantProperties properties) {
        return NimbusJwtEncoder.withSecretKey(properties.jwt().signingKey()).algorithm(HS256).build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
