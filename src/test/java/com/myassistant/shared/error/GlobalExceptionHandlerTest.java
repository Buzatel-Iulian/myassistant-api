package com.myassistant.shared.error;

import com.myassistant.config.SecurityConfig;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * How {@link GlobalExceptionHandler} answers each kind of error, through a controller that exists only in this test
 * and throws them on purpose. The real {@link SecurityConfig} is on, so every request runs as a logged-in user.
 */
@WebMvcTest(GlobalExceptionHandlerTest.ProbeController.class)
@Import({GlobalExceptionHandlerTest.ProbeController.class, SecurityConfig.class})
@WithMockUser
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void notFoundExceptionIs404ProblemDetail() throws Exception {
        mockMvc.perform(get("/probe/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("No probe with id p1"));
    }

    @Test
    void conflictExceptionIs409WithItsProperties() throws Exception {
        mockMvc.perform(get("/probe/conflict"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("The probe is still in use"))
                .andExpect(jsonPath("$.usedBy[0]").value("First"))
                .andExpect(jsonPath("$.usedBy[1]").value("Second"));
    }

    @Test
    void staleVersionIs409WithReloadMessage() throws Exception {
        mockMvc.perform(get("/probe/stale"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("This item was changed in another tab. Reload it and try again."));
    }

    @Test
    void duplicateKeyIs409WithoutTheDuplicatedValue() throws Exception {
        mockMvc.perform(get("/probe/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("An item with the same unique value already exists."))
                .andExpect(content().string(not(containsString("someone@example.com"))));
    }

    @Test
    void invalidBodyIs400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/probe/body").contentType(APPLICATION_JSON).content("""
                        {"name": " ", "code": "TOO-LONG"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Some fields are invalid."))
                .andExpect(jsonPath("$.fieldErrors.name").value("must not be blank"))
                .andExpect(jsonPath("$.fieldErrors.code").value("size must be between 0 and 3"));
    }

    @Test
    void validBodyPassesThrough() throws Exception {
        mockMvc.perform(post("/probe/body").contentType(APPLICATION_JSON).content("""
                        {"name": "Probe", "code": "P1"}"""))
                .andExpect(status().isOk());
    }

    @Test
    void invalidEntityBeforeSaveIs400WithFieldErrors() throws Exception {
        mockMvc.perform(get("/probe/invalid-entity"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.fieldErrors.name").value("must not be blank"));
    }

    @Test
    void unreadableBodyIsStillAProblemDetail() throws Exception {
        mockMvc.perform(post("/probe/body").contentType(APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON));
    }

    @Test
    void unknownPathIsStillAProblemDetail() throws Exception {
        mockMvc.perform(get("/probe/nothing-here"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON));
    }

    @Test
    void unexpectedExceptionIs500WithoutDetails() throws Exception {
        mockMvc.perform(get("/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("Something went wrong. Try again later."))
                .andExpect(content().string(not(containsString("hunter2"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    void authenticationExceptionIsNot500() throws Exception {
        mockMvc.perform(get("/probe/unauthenticated"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(content().string(not(containsString("internal reason"))));
    }

    @Test
    void accessDeniedIsNot500() throws Exception {
        mockMvc.perform(get("/probe/access-denied"))
                .andExpect(status().isForbidden());
    }

    @Test
    void wrongMethodIsStill405() throws Exception {
        mockMvc.perform(get("/probe/body"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON));
    }

    @Test
    void wrongContentTypeIsStill415() throws Exception {
        mockMvc.perform(post("/probe/body").contentType(TEXT_PLAIN).content("name=Probe"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON));
    }

    record ProbeRequest(@NotBlank String name, @NotBlank @Size(max = 3) String code) {
    }

    static class ProbeNotFoundException extends NotFoundException {
        ProbeNotFoundException(String id) {
            super("No probe with id " + id);
        }
    }

    static class ProbeInUseException extends ConflictException {
        ProbeInUseException(List<String> usedBy) {
            super("The probe is still in use", Map.of("usedBy", usedBy));
        }
    }

    @RestController
    @RequestMapping("/probe")
    static class ProbeController {

        private final Validator validator;

        ProbeController(Validator validator) {
            this.validator = validator;
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new ProbeNotFoundException("p1");
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ProbeInUseException(List.of("First", "Second"));
        }

        @GetMapping("/stale")
        void stale() {
            throw new OptimisticLockingFailureException("Version 3 is stale");
        }

        @GetMapping("/duplicate")
        void duplicate() {
            throw new DuplicateKeyException("E11000 duplicate key error dup key: { email: \"someone@example.com\" }");
        }

        @PostMapping("/body")
        void body(@Valid @RequestBody ProbeRequest request) {
        }

        @GetMapping("/invalid-entity")
        void invalidEntity() {
            throw new ConstraintViolationException(validator.validate(new ProbeRequest(" ", "P1")));
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("db password=hunter2");
        }

        @GetMapping("/unauthenticated")
        void unauthenticated() {
            throw new InsufficientAuthenticationException("internal reason");
        }

        @GetMapping("/access-denied")
        void accessDenied() {
            throw new AccessDeniedException("internal reason");
        }
    }
}
