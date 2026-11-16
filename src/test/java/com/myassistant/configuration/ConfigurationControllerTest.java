package com.myassistant.configuration;

import com.myassistant.config.SecurityConfig;
import com.myassistant.configuration.dto.ConfigurationResponse;
import com.myassistant.configuration.dto.CreateConfigurationRequest;
import com.myassistant.configuration.dto.GenerationSettingsDto;
import com.myassistant.configuration.dto.UpdateConfigurationRequest;
import com.myassistant.template.TemplateNotFoundException;
import com.myassistant.template.dto.TemplateResponse;
import com.myassistant.template.dto.TemplateVariableDto;
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
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
 * Controller test ({@code @WebMvcTest}, the service is a mock): the HTTP side of the configurations API, behind the
 * real {@link SecurityConfig}. The owner is always the token's {@code sub}. The cases are listed in
 * {@code docs/configuration-cases.md}.
 */
@WebMvcTest(ConfigurationController.class)
@Import(SecurityConfig.class)
class ConfigurationControllerTest {
    private static final Instant CREATED_AT = Instant.parse("2030-01-01T08:00:00Z");
    private static final GenerationSettingsDto SETTINGS = new GenerationSettingsDto(0.7, 0.95, 512, 4096, 0.0, 0.0);
    private static final String SETTINGS_JSON = """
            {"temperature": 0.7, "topP": 0.95, "maxTokens": 512, "contextWindowSize": 4096,
             "frequencyPenalty": 0.0, "presencePenalty": 0.0}""";
    private static final String VALID_BODY = """
            {"name": "English tutor", "description": "Daily practice", "modelId": "Qwen2.5-1.5B-Instruct-q4f16_1-MLC",
             "settings": %s, "templateId": "t1", "variableValues": {"language": "English"}, "favorite": true}"""
            .formatted(SETTINGS_JSON);
    private static final TemplateResponse TEMPLATE = new TemplateResponse("t1", 4, "Language tutor", null,
            "You are a {{language}} tutor for a {{level}} student.",
            List.of(new TemplateVariableDto("language", "Language", "English"),
                    new TemplateVariableDto("level", "Level", "beginner")),
            CREATED_AT, CREATED_AT);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConfigurationService configurationService;

    @Test
    void listReturnsMyConfigurations() throws Exception {
        when(configurationService.findAll("u1")).thenReturn(List.of(
                configuration("c2", "Zeta", 1, true, TEMPLATE), configuration("c1", "Alpha", 0, false, null)));

        mockMvc.perform(get("/api/configurations").with(user("u1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("c2"))
                .andExpect(jsonPath("$[0].favorite").value(true))
                .andExpect(jsonPath("$[0].template.name").value("Language tutor"))
                .andExpect(jsonPath("$[1].name").value("Alpha"))
                .andExpect(jsonPath("$[0].ownerId").doesNotExist());
    }

    @Test
    void getReturnsConfigurationWithTemplate() throws Exception {
        when(configurationService.findById("u1", "c1")).thenReturn(configuration("c1", "English tutor", 2, true, TEMPLATE));
        when(configurationService.findById("u1", "c2")).thenReturn(configuration("c2", "Plain", 0, false, null));

        mockMvc.perform(get("/api/configurations/c1").with(user("u1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("c1"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.modelId").value("Qwen2.5-1.5B-Instruct-q4f16_1-MLC"))
                .andExpect(jsonPath("$.settings.temperature").value(0.7))
                .andExpect(jsonPath("$.settings.topP").value(0.95))
                .andExpect(jsonPath("$.settings.maxTokens").value(512))
                .andExpect(jsonPath("$.settings.contextWindowSize").value(4096))
                .andExpect(jsonPath("$.settings.frequencyPenalty").value(0.0))
                .andExpect(jsonPath("$.settings.presencePenalty").value(0.0))
                .andExpect(jsonPath("$.templateId").value("t1"))
                .andExpect(jsonPath("$.template.content").value("You are a {{language}} tutor for a {{level}} student."))
                .andExpect(jsonPath("$.template.variables[1].defaultValue").value("beginner"))
                .andExpect(jsonPath("$.variableValues.language").value("English"))
                .andExpect(jsonPath("$.resolvedVariableValues.language").value("English"))
                .andExpect(jsonPath("$.resolvedVariableValues.level").value("beginner"))
                .andExpect(jsonPath("$.favorite").value(true))
                .andExpect(jsonPath("$.createdAt").value("2030-01-01T08:00:00Z"));

        mockMvc.perform(get("/api/configurations/c2").with(user("u1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateId").value(nullValue()))
                .andExpect(jsonPath("$.template").value(nullValue()));
    }

    @Test
    void unknownConfigurationIsNotFound() throws Exception {
        when(configurationService.findById("u1", "c9")).thenThrow(new ConfigurationNotFoundException("c9"));

        mockMvc.perform(get("/api/configurations/c9").with(user("u1")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("No configuration with id c9"));
    }

    @Test
    void createReturns201WithLocation() throws Exception {
        CreateConfigurationRequest request = new CreateConfigurationRequest("English tutor", "Daily practice",
                "Qwen2.5-1.5B-Instruct-q4f16_1-MLC", SETTINGS, "t1", Map.of("language", "English"), true);
        when(configurationService.create("u1", request)).thenReturn(configuration("c7", "English tutor", 0, true, TEMPLATE));

        mockMvc.perform(post("/api/configurations").with(user("u1")).contentType(APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/configurations/c7")))
                .andExpect(jsonPath("$.id").value("c7"))
                .andExpect(jsonPath("$.template.id").value("t1"));
    }

    @Test
    void unknownTemplateOnCreateIsNotFound() throws Exception {
        when(configurationService.create(eq("u1"), any())).thenThrow(new TemplateNotFoundException("t1"));

        mockMvc.perform(post("/api/configurations").with(user("u1")).contentType(APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("No template with id t1"));
    }

    @Test
    void unknownVariableKeysAreBadRequest() throws Exception {
        when(configurationService.create(eq("u1"), any()))
                .thenThrow(new UnknownVariablesException(List.of("tone", "city")));

        mockMvc.perform(post("/api/configurations").with(user("u1")).contentType(APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", containsString("tone, city")))
                .andExpect(jsonPath("$.unknownVariables[0]").value("tone"))
                .andExpect(jsonPath("$.unknownVariables[1]").value("city"));
    }

    @Test
    void invalidConfigurationIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/configurations").with(user("u1")).contentType(APPLICATION_JSON).content("""
                        {"name": " ", "description": "%s", "modelId": "gpt-4o", "settings": %s}"""
                        .formatted("d".repeat(501), SETTINGS_JSON)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.description").exists())
                .andExpect(jsonPath("$.fieldErrors.modelId").exists());

        mockMvc.perform(post("/api/configurations").with(user("u1")).contentType(APPLICATION_JSON).content("""
                        {"name": "%s", "settings": %s}""".formatted("n".repeat(81), SETTINGS_JSON)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.modelId").exists());

        String manyValues = IntStream.rangeClosed(1, 21)
                .mapToObj(i -> "\"v" + i + "\": \"x\"")
                .collect(Collectors.joining(", "));
        mockMvc.perform(post("/api/configurations").with(user("u1")).contentType(APPLICATION_JSON).content(
                        VALID_BODY.replace("{\"language\": \"English\"}", "{" + manyValues + "}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.variableValues").exists());

        mockMvc.perform(post("/api/configurations").with(user("u1")).contentType(APPLICATION_JSON).content(
                        VALID_BODY.replace("\"English\"", "\"" + "x".repeat(1001) + "\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").exists());

        verify(configurationService, never()).create(any(), any());
    }

    @Test
    void invalidSettingsAreBadRequest() throws Exception {
        mockMvc.perform(post("/api/configurations").with(user("u1")).contentType(APPLICATION_JSON).content("""
                        {"name": "Tutor", "modelId": "Qwen2.5-0.5B-Instruct-q4f16_1-MLC"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.settings").exists());

        mockMvc.perform(post("/api/configurations").with(user("u1")).contentType(APPLICATION_JSON).content("""
                        {"name": "Tutor", "modelId": "Qwen2.5-0.5B-Instruct-q4f16_1-MLC",
                         "settings": {"temperature": 2.1, "topP": 1.1, "maxTokens": 4097, "contextWindowSize": 511,
                                      "frequencyPenalty": -2.1, "presencePenalty": 2.1}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['settings.temperature']").exists())
                .andExpect(jsonPath("$.fieldErrors['settings.topP']").exists())
                .andExpect(jsonPath("$.fieldErrors['settings.maxTokens']").exists())
                .andExpect(jsonPath("$.fieldErrors['settings.contextWindowSize']").exists())
                .andExpect(jsonPath("$.fieldErrors['settings.frequencyPenalty']").exists())
                .andExpect(jsonPath("$.fieldErrors['settings.presencePenalty']").exists());

        mockMvc.perform(post("/api/configurations").with(user("u1")).contentType(APPLICATION_JSON).content("""
                        {"name": "Tutor", "modelId": "Qwen2.5-0.5B-Instruct-q4f16_1-MLC",
                         "settings": {"temperature": -0.1, "topP": -0.1, "maxTokens": 0, "contextWindowSize": 32769}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['settings.temperature']").exists())
                .andExpect(jsonPath("$.fieldErrors['settings.topP']").exists())
                .andExpect(jsonPath("$.fieldErrors['settings.maxTokens']").exists())
                .andExpect(jsonPath("$.fieldErrors['settings.contextWindowSize']").exists())
                .andExpect(jsonPath("$.fieldErrors['settings.frequencyPenalty']").exists())
                .andExpect(jsonPath("$.fieldErrors['settings.presencePenalty']").exists());

        verify(configurationService, never()).create(any(), any());
    }

    @Test
    void maxTokensNotBelowContextWindowIsBadRequest() throws Exception {
        String tooBig = VALID_BODY.replace("\"maxTokens\": 512, \"contextWindowSize\": 4096",
                "\"maxTokens\": 4096, \"contextWindowSize\": 512");

        mockMvc.perform(post("/api/configurations").with(user("u1")).contentType(APPLICATION_JSON).content(tooBig))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.fieldErrors['settings.maxTokens']").value("must be less than the context window size"));
        mockMvc.perform(put("/api/configurations/c1").with(user("u1")).contentType(APPLICATION_JSON)
                        .content(tooBig.replace("{\"name\"", "{\"version\": 1, \"name\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['settings.maxTokens']").value("must be less than the context window size"));

        verifyNoInteractions(configurationService);
    }

    @Test
    void updateReturnsConfiguration() throws Exception {
        UpdateConfigurationRequest request = new UpdateConfigurationRequest("English tutor", "Daily practice",
                "Qwen2.5-1.5B-Instruct-q4f16_1-MLC", SETTINGS, "t1", Map.of("language", "English"), true, 2L);
        when(configurationService.update("u1", "c1", request)).thenReturn(configuration("c1", "English tutor", 3, true, TEMPLATE));

        mockMvc.perform(put("/api/configurations/c1").with(user("u1")).contentType(APPLICATION_JSON)
                        .content(VALID_BODY.replace("{\"name\"", "{\"version\": 2, \"name\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void updateWithoutVersionIsBadRequest() throws Exception {
        mockMvc.perform(put("/api/configurations/c1").with(user("u1")).contentType(APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.version").exists());

        verify(configurationService, never()).update(any(), any(), any());
    }

    @Test
    void staleVersionIsConflict() throws Exception {
        when(configurationService.update(eq("u1"), eq("c1"), any()))
                .thenThrow(new OptimisticLockingFailureException("stale"));

        mockMvc.perform(put("/api/configurations/c1").with(user("u1")).contentType(APPLICATION_JSON)
                        .content(VALID_BODY.replace("{\"name\"", "{\"version\": 1, \"name\"")))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("This item was changed in another tab. Reload it and try again."));
    }

    @Test
    void deleteAnswersNoContent() throws Exception {
        mockMvc.perform(delete("/api/configurations/c1").with(user("u1")))
                .andExpect(status().isNoContent());

        verify(configurationService).delete("u1", "c1");
    }

    @Test
    void duplicateReturns201WithLocation() throws Exception {
        when(configurationService.duplicate("u1", "c1"))
                .thenReturn(configuration("c8", "English tutor (copy)", 0, false, TEMPLATE));

        mockMvc.perform(post("/api/configurations/c1/duplicate").with(user("u1")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/configurations/c8")))
                .andExpect(jsonPath("$.id").value("c8"))
                .andExpect(jsonPath("$.name").value("English tutor (copy)"));
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/configurations")).andExpect(status().isUnauthorized())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON));
        mockMvc.perform(get("/api/configurations/c1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/configurations").contentType(APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/configurations/c1").contentType(APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/configurations/c1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/configurations/c1/duplicate")).andExpect(status().isUnauthorized());

        verifyNoInteractions(configurationService);
    }

    private static JwtRequestPostProcessor user(String userId) {
        return jwt().jwt(token -> token.subject(userId));
    }

    private static ConfigurationResponse configuration(String id, String name, long version, boolean favorite,
                                                       TemplateResponse template) {
        return new ConfigurationResponse(id, version, name, "Daily practice", "Qwen2.5-1.5B-Instruct-q4f16_1-MLC",
                SETTINGS, template == null ? null : template.id(), template,
                template == null ? Map.of() : Map.of("language", "English"),
                template == null ? Map.of() : Map.of("language", "English", "level", "beginner"),
                favorite, CREATED_AT, CREATED_AT);
    }
}
