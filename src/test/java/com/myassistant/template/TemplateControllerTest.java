package com.myassistant.template;

import com.myassistant.config.SecurityConfig;
import com.myassistant.template.dto.CreateTemplateRequest;
import com.myassistant.template.dto.TemplateResponse;
import com.myassistant.template.dto.TemplateVariableDto;
import com.myassistant.template.dto.UpdateTemplateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller test ({@code @WebMvcTest}, the service is a mock): the HTTP side of the templates API, behind the real
 * {@link SecurityConfig}. The owner is always the token's {@code sub}. The cases are listed in
 * {@code docs/template-cases.md}.
 */
@WebMvcTest(TemplateController.class)
@Import(SecurityConfig.class)
class TemplateControllerTest {
    private static final Instant CREATED_AT = Instant.parse("2030-01-01T08:00:00Z");
    private static final TemplateVariableDto LANGUAGE = new TemplateVariableDto("language", "Language", "English");
    private static final String VALID_BODY = """
            {"name": "Language tutor", "description": "Corrects mistakes",
             "content": "You are a {{language}} tutor.",
             "variables": [{"name": "language", "label": "Language", "defaultValue": "English"}]}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TemplateService templateService;

    @Test
    void listReturnsMyTemplates() throws Exception {
        when(templateService.findAll("u1")).thenReturn(List.of(
                template("t1", "Code helper", 0), template("t2", "Language tutor", 3)));

        mockMvc.perform(get("/api/templates").with(user("u1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("t1"))
                .andExpect(jsonPath("$[1].name").value("Language tutor"))
                .andExpect(jsonPath("$[1].version").value(3))
                .andExpect(jsonPath("$[0].ownerId").doesNotExist());
    }

    @Test
    void getReturnsTemplate() throws Exception {
        when(templateService.findById("u1", "t1")).thenReturn(template("t1", "Language tutor", 2));

        mockMvc.perform(get("/api/templates/t1").with(user("u1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("t1"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.content").value("You are a {{language}} tutor."))
                .andExpect(jsonPath("$.variables[0].name").value("language"))
                .andExpect(jsonPath("$.variables[0].label").value("Language"))
                .andExpect(jsonPath("$.variables[0].defaultValue").value("English"))
                .andExpect(jsonPath("$.createdAt").value("2030-01-01T08:00:00Z"));
    }

    // Another user's template gives the same exception in the service (TemplateServiceTest): the same 404 here.
    @Test
    void unknownTemplateIsNotFound() throws Exception {
        when(templateService.findById("u1", "t9")).thenThrow(new TemplateNotFoundException("t9"));

        mockMvc.perform(get("/api/templates/t9").with(user("u1")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("No template with id t9"));
    }

    @Test
    void createReturns201WithLocation() throws Exception {
        CreateTemplateRequest request = new CreateTemplateRequest("Language tutor", "Corrects mistakes",
                "You are a {{language}} tutor.", List.of(LANGUAGE));
        when(templateService.create("u1", request)).thenReturn(template("t7", "Language tutor", 0));

        mockMvc.perform(post("/api/templates").with(user("u1")).contentType(APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/templates/t7")))
                .andExpect(jsonPath("$.id").value("t7"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void invalidTemplateIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/templates").with(user("u1")).contentType(APPLICATION_JSON).content("""
                        {"name": " ", "description": "%s", "content": ""}""".formatted("d".repeat(501))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.description").exists())
                .andExpect(jsonPath("$.fieldErrors.content").exists());

        mockMvc.perform(post("/api/templates").with(user("u1")).contentType(APPLICATION_JSON).content("""
                        {"name": "%s", "content": "%s"}""".formatted("n".repeat(81), "c".repeat(8001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.content").exists());

        verify(templateService, never()).create(any(), any());
    }

    @Test
    void invalidVariableNameIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/templates").with(user("u1")).contentType(APPLICATION_JSON).content("""
                        {"name": "Tutor", "content": "Hello",
                         "variables": [{"name": "1st"}, {"name": "has space"}, {"name": "%s"}, {"label": "No name"},
                                       {"name": "ok_Name1"}]}""".formatted("a".repeat(41))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['variables[0].name']").exists())
                .andExpect(jsonPath("$.fieldErrors['variables[1].name']").exists())
                .andExpect(jsonPath("$.fieldErrors['variables[2].name']").exists())
                .andExpect(jsonPath("$.fieldErrors['variables[3].name']").exists())
                .andExpect(jsonPath("$.fieldErrors['variables[4].name']").doesNotExist());

        verify(templateService, never()).create(any(), any());
    }

    @Test
    void tooManyVariablesIsBadRequest() throws Exception {
        String variables = IntStream.rangeClosed(1, 21)
                .mapToObj(i -> "{\"name\": \"v" + i + "\"}")
                .collect(Collectors.joining(", "));

        mockMvc.perform(post("/api/templates").with(user("u1")).contentType(APPLICATION_JSON).content("""
                        {"name": "Tutor", "content": "Hello", "variables": [%s]}""".formatted(variables)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.variables").exists());

        verify(templateService, never()).create(any(), any());
    }

    @Test
    void undeclaredPlaceholderIsBadRequest() throws Exception {
        when(templateService.create(eq("u1"), any()))
                .thenThrow(new InvalidTemplateException(List.of("level", "tone"), List.of("language")));

        mockMvc.perform(post("/api/templates").with(user("u1")).contentType(APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail", containsString("{{level}}, {{tone}}")))
                .andExpect(jsonPath("$.unknownPlaceholders[0]").value("level"))
                .andExpect(jsonPath("$.unknownPlaceholders[1]").value("tone"))
                .andExpect(jsonPath("$.duplicateVariables[0]").value("language"));
    }

    @Test
    void updateReturnsTemplate() throws Exception {
        UpdateTemplateRequest request = new UpdateTemplateRequest("Language tutor", "Corrects mistakes",
                "You are a {{language}} tutor.", List.of(LANGUAGE), 2L);
        when(templateService.update("u1", "t1", request)).thenReturn(template("t1", "Language tutor", 3));

        mockMvc.perform(put("/api/templates/t1").with(user("u1")).contentType(APPLICATION_JSON)
                        .content(VALID_BODY.replace("{\"name\": \"Language tutor\"", "{\"version\": 2, \"name\": \"Language tutor\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("t1"))
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void updateWithoutVersionIsBadRequest() throws Exception {
        mockMvc.perform(put("/api/templates/t1").with(user("u1")).contentType(APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.version").exists());

        verify(templateService, never()).update(any(), any(), any());
    }

    @Test
    void staleVersionIsConflict() throws Exception {
        when(templateService.update(eq("u1"), eq("t1"), any())).thenThrow(new OptimisticLockingFailureException("stale"));

        mockMvc.perform(put("/api/templates/t1").with(user("u1")).contentType(APPLICATION_JSON)
                        .content(VALID_BODY.replace("{\"name\"", "{\"version\": 1, \"name\"")))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("This item was changed in another tab. Reload it and try again."));
    }

    @Test
    void deleteAnswersNoContent() throws Exception {
        mockMvc.perform(delete("/api/templates/t1").with(user("u1")))
                .andExpect(status().isNoContent());

        verify(templateService).delete("u1", "t1");
    }

    @Test
    void deleteInUseIsConflict() throws Exception {
        doThrow(new TemplateInUseException(List.of("English tutor", "Code helper")))
                .when(templateService).delete("u1", "t1");

        mockMvc.perform(delete("/api/templates/t1").with(user("u1")))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", containsString("English tutor")))
                .andExpect(jsonPath("$.configurations[0]").value("English tutor"))
                .andExpect(jsonPath("$.configurations[1]").value("Code helper"));
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/templates")).andExpect(status().isUnauthorized())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON));
        mockMvc.perform(get("/api/templates/t1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/templates").contentType(APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/templates/t1").contentType(APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/templates/t1")).andExpect(status().isUnauthorized());

        verifyNoInteractions(templateService);
    }

    private static JwtRequestPostProcessor user(String userId) {
        return jwt().jwt(token -> token.subject(userId));
    }

    private static TemplateResponse template(String id, String name, long version) {
        return new TemplateResponse(id, version, name, "Corrects mistakes", "You are a {{language}} tutor.",
                List.of(LANGUAGE), CREATED_AT, CREATED_AT);
    }
}
