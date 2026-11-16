package com.myassistant.configuration;

import com.myassistant.MongoPersistenceTest;
import com.myassistant.configuration.dto.ConfigurationResponse;
import com.myassistant.configuration.dto.CreateConfigurationRequest;
import com.myassistant.configuration.dto.GenerationSettingsDto;
import com.myassistant.configuration.dto.UpdateConfigurationRequest;
import com.myassistant.template.TemplateInUseException;
import com.myassistant.template.TemplateMapper;
import com.myassistant.template.TemplateNotFoundException;
import com.myassistant.template.TemplateRepository;
import com.myassistant.template.TemplateService;
import com.myassistant.template.dto.CreateTemplateRequest;
import com.myassistant.template.dto.TemplateResponse;
import com.myassistant.template.dto.TemplateVariableDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The configuration rules against a real MongoDB, with the real {@link TemplateService} and the real
 * {@link com.myassistant.template.TemplateUsage} implementation of this feature: owner scoping, the template check,
 * variable values, optimistic locking, duplicate, and "a template in use cannot be deleted". The cases are listed in
 * {@code docs/configuration-cases.md}.
 */
@MongoPersistenceTest
@Import({ConfigurationService.class, ConfigurationMapper.class, ConfigurationTemplateUsage.class,
        TemplateService.class, TemplateMapper.class})
class ConfigurationServiceTest {
    private static final String ANA = "user-ana";
    private static final String BOB = "user-bob";
    private static final String MODEL = "Qwen2.5-1.5B-Instruct-q4f16_1-MLC";
    private static final GenerationSettingsDto SETTINGS = new GenerationSettingsDto(0.7, 0.95, 512, 4096, 0.0, 0.0);

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private ConfigurationRepository configurationRepository;

    @Autowired
    private TemplateService templateService;

    @Autowired
    private TemplateRepository templateRepository;

    private TemplateResponse anasTutor;

    @BeforeEach
    void setUp() {
        configurationRepository.deleteAll();
        templateRepository.deleteAll();
        anasTutor = templateService.create(ANA, new CreateTemplateRequest("Language tutor", null,
                "You are a {{language}} tutor for a {{level}} student. {{extra}}",
                List.of(new TemplateVariableDto("language", "Language", "English"),
                        new TemplateVariableDto("level", "Level", "beginner"),
                        new TemplateVariableDto("extra", null, null))));
    }

    @Test
    void createStoresConfigurationForOwner() {
        ConfigurationResponse created = configurationService.create(ANA,
                request("English tutor", anasTutor.id(), Map.of("level", "advanced"), true));

        assertNotNull(created.id());
        assertEquals(0, created.version());
        assertEquals("English tutor", created.name());
        assertEquals(MODEL, created.modelId());
        assertEquals(SETTINGS, created.settings());
        assertEquals(anasTutor.id(), created.templateId());
        assertEquals(anasTutor, created.template());
        assertEquals(Map.of("level", "advanced"), created.variableValues());
        assertTrue(created.favorite());
        assertNotNull(created.createdAt());
        assertEquals(created.createdAt(), created.updatedAt());

        LlmConfiguration stored = configurationRepository.findById(created.id()).orElseThrow();
        assertEquals(ANA, stored.getOwnerId());
        assertEquals(new GenerationSettings(0.7, 0.95, 512, 4096, 0.0, 0.0), stored.getSettings());
        assertEquals(created, configurationService.findById(ANA, created.id()));
    }

    @Test
    void createWithDefaults() {
        ConfigurationResponse created = configurationService.create(ANA, new CreateConfigurationRequest(
                "Plain", null, MODEL, SETTINGS, null, null, false));

        assertEquals(Map.of(), created.variableValues());
        assertFalse(created.favorite());
        assertEquals(Map.of(), configurationRepository.findById(created.id()).orElseThrow().getVariableValues());
    }

    @Test
    void listIsFavoritesFirstThenByName() {
        configurationService.create(ANA, request("Delta", null, Map.of(), false));
        configurationService.create(ANA, request("Beta", anasTutor.id(), Map.of(), true));
        configurationService.create(ANA, request("Alpha", null, Map.of(), false));
        configurationService.create(ANA, request("Gamma", null, Map.of(), true));
        configurationService.create(BOB, request("Bob's", null, Map.of(), true));

        List<ConfigurationResponse> anas = configurationService.findAll(ANA);

        assertEquals(List.of("Beta", "Gamma", "Alpha", "Delta"), anas.stream().map(ConfigurationResponse::name).toList());
        assertEquals(anasTutor, anas.getFirst().template());
        assertEquals("beginner", anas.getFirst().resolvedVariableValues().get("level"));
        assertNull(anas.get(1).template());
        assertEquals(List.of("Bob's"), configurationService.findAll(BOB).stream().map(ConfigurationResponse::name).toList());
        assertEquals(List.of(), configurationService.findAll("user-without-configurations"));
    }

    @Test
    void listSortIgnoresLetterCase() {
        configurationService.create(ANA, request("beta", null, Map.of(), false));
        configurationService.create(ANA, request("Gamma", null, Map.of(), false));
        configurationService.create(ANA, request("alpha", null, Map.of(), false));
        configurationService.create(ANA, request("Delta", null, Map.of(), true));
        configurationService.create(ANA, request("charlie", null, Map.of(), true));

        assertEquals(List.of("charlie", "Delta", "alpha", "beta", "Gamma"),
                configurationService.findAll(ANA).stream().map(ConfigurationResponse::name).toList());
    }

    // A form's "no template" option may send an empty string: it means no template, not a 404.
    @Test
    void blankTemplateIdMeansNoTemplate() {
        ConfigurationResponse created = configurationService.create(ANA, request("Plain", "  ", Map.of(), false));

        assertNull(created.templateId());
        assertNull(created.template());
        assertNull(configurationRepository.findById(created.id()).orElseThrow().getTemplateId());

        ConfigurationResponse updated = configurationService.update(ANA, created.id(),
                update("Plain", "", Map.of(), created.version()));
        assertNull(updated.templateId());
        assertNull(updated.template());
    }

    @Test
    void getEmbedsTemplateAndResolvesVariables() {
        ConfigurationResponse created = configurationService.create(ANA,
                request("English tutor", anasTutor.id(), Map.of("level", "intermediate"), false));

        ConfigurationResponse found = configurationService.findById(ANA, created.id());

        assertEquals(anasTutor, found.template());
        assertEquals(List.of("language", "level", "extra"), List.copyOf(found.resolvedVariableValues().keySet()));
        assertEquals(Map.of("language", "English", "level", "intermediate", "extra", ""), found.resolvedVariableValues());
    }

    @Test
    void getWithoutTemplate() {
        ConfigurationResponse created = configurationService.create(ANA, request("Plain", null, Map.of(), false));

        ConfigurationResponse found = configurationService.findById(ANA, created.id());

        assertNull(found.templateId());
        assertNull(found.template());
        assertEquals(Map.of(), found.resolvedVariableValues());
    }

    @Test
    void missingVariablesFallBackToDefaults() {
        Map<String, String> values = new HashMap<>();
        values.put("language", "  ");
        values.put("extra", "Be kind.");

        ConfigurationResponse created = configurationService.create(ANA,
                request("Tutor", anasTutor.id(), values, false));

        // Stored as given; resolved on read.
        assertEquals(values, configurationRepository.findById(created.id()).orElseThrow().getVariableValues());
        assertEquals(values, created.variableValues());
        assertEquals(Map.of("language", "English", "level", "beginner", "extra", "Be kind."),
                configurationService.findById(ANA, created.id()).resolvedVariableValues());
    }

    @Test
    void unknownConfigurationIsNotFound() {
        ConfigurationNotFoundException exception = assertThrows(ConfigurationNotFoundException.class,
                () -> configurationService.findById(ANA, "c9"));

        assertEquals("No configuration with id c9", exception.getMessage());
    }

    @Test
    void otherUsersConfigurationIsNotFound() {
        ConfigurationResponse anas = configurationService.create(ANA, request("English tutor", anasTutor.id(), Map.of(), true));

        ConfigurationNotFoundException onGet = assertThrows(ConfigurationNotFoundException.class,
                () -> configurationService.findById(BOB, anas.id()));
        assertThrows(ConfigurationNotFoundException.class,
                () -> configurationService.update(BOB, anas.id(), update("Taken over", null, Map.of(), anas.version())));
        assertThrows(ConfigurationNotFoundException.class, () -> configurationService.duplicate(BOB, anas.id()));
        assertThrows(ConfigurationNotFoundException.class, () -> configurationService.delete(BOB, anas.id()));

        assertEquals("No configuration with id " + anas.id(), onGet.getMessage());
        assertEquals(anas, configurationService.findById(ANA, anas.id()));
        assertEquals(1, configurationRepository.count());
    }

    @Test
    void templateOfAnotherUserIsNotFound() {
        TemplateResponse bobsTemplate = templateService.create(BOB,
                new CreateTemplateRequest("Bob's", null, "Hello", List.of()));

        TemplateNotFoundException othersTemplate = assertThrows(TemplateNotFoundException.class,
                () -> configurationService.create(ANA, request("Sneaky", bobsTemplate.id(), Map.of(), false)));
        assertThrows(TemplateNotFoundException.class,
                () -> configurationService.create(ANA, request("Unknown", "no-such-template", Map.of(), false)));

        assertEquals("No template with id " + bobsTemplate.id(), othersTemplate.getMessage());
        assertEquals(0, configurationRepository.count());
    }

    @Test
    void unknownVariableKeysAreRejected() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("tone", "warm");
        values.put("language", "French");
        values.put("city", "Paris");

        UnknownVariablesException exception = assertThrows(UnknownVariablesException.class,
                () -> configurationService.create(ANA, request("Tutor", anasTutor.id(), values, false)));
        UnknownVariablesException withoutTemplate = assertThrows(UnknownVariablesException.class,
                () -> configurationService.create(ANA, request("Plain", null, Map.of("language", "French"), false)));

        assertEquals(List.of("tone", "city"), exception.properties().get("unknownVariables"));
        assertTrue(exception.getMessage().contains("tone"), exception.getMessage());
        assertEquals(List.of("language"), withoutTemplate.properties().get("unknownVariables"));
        assertEquals(0, configurationRepository.count());
    }

    @Test
    void updateChangesConfigurationAndVersion() {
        ConfigurationResponse created = configurationService.create(ANA, request("English tutor", anasTutor.id(), Map.of(), false));

        ConfigurationResponse updated = configurationService.update(ANA, created.id(),
                update("French tutor", anasTutor.id(), Map.of("language", "French"), created.version()));

        assertEquals(created.id(), updated.id());
        assertEquals(created.version() + 1, updated.version());
        assertEquals("French tutor", updated.name());
        assertEquals("Llama-3.2-1B-Instruct-q4f16_1-MLC", updated.modelId());
        assertEquals(new GenerationSettingsDto(1.2, 0.9, 1024, 8192, 0.5, -0.5), updated.settings());
        assertEquals("French", updated.resolvedVariableValues().get("language"));
        assertTrue(updated.favorite());
        assertEquals(created.createdAt(), updated.createdAt());
        assertFalse(updated.updatedAt().isBefore(created.updatedAt()));
        assertEquals(updated, configurationService.findById(ANA, created.id()));
    }

    @Test
    void staleVersionIsConflict() {
        ConfigurationResponse created = configurationService.create(ANA, request("English tutor", null, Map.of(), false));
        configurationService.update(ANA, created.id(), update("First tab", null, Map.of(), created.version()));

        assertThrows(OptimisticLockingFailureException.class, () -> configurationService.update(ANA, created.id(),
                update("Second tab", null, Map.of(), created.version())));

        assertEquals("First tab", configurationService.findById(ANA, created.id()).name());
    }

    @Test
    void updateChecksTemplateAndVariables() {
        ConfigurationResponse created = configurationService.create(ANA, request("English tutor", anasTutor.id(), Map.of(), false));
        TemplateResponse bobsTemplate = templateService.create(BOB, new CreateTemplateRequest("Bob's", null, "Hi", List.of()));

        assertThrows(TemplateNotFoundException.class, () -> configurationService.update(ANA, created.id(),
                update("Changed", bobsTemplate.id(), Map.of(), created.version())));
        assertThrows(UnknownVariablesException.class, () -> configurationService.update(ANA, created.id(),
                update("Changed", anasTutor.id(), Map.of("tone", "warm"), created.version())));

        assertEquals(created, configurationService.findById(ANA, created.id()));
    }

    @Test
    void deleteRemovesConfiguration() {
        ConfigurationResponse created = configurationService.create(ANA, request("English tutor", null, Map.of(), false));

        configurationService.delete(ANA, created.id());

        assertFalse(configurationRepository.existsById(created.id()));
    }

    @Test
    void duplicateCopiesConfiguration() {
        ConfigurationResponse original = configurationService.create(ANA,
                request("English tutor", anasTutor.id(), Map.of("level", "advanced"), true));
        configurationService.update(ANA, original.id(),
                update("English tutor", anasTutor.id(), Map.of("level", "advanced"), original.version()));

        ConfigurationResponse copy = configurationService.duplicate(ANA, original.id());

        assertNotEquals(original.id(), copy.id());
        assertEquals("English tutor (copy)", copy.name());
        assertEquals(0, copy.version());
        assertFalse(copy.favorite());
        assertEquals("Llama-3.2-1B-Instruct-q4f16_1-MLC", copy.modelId());
        assertEquals(new GenerationSettingsDto(1.2, 0.9, 1024, 8192, 0.5, -0.5), copy.settings());
        assertEquals(anasTutor.id(), copy.templateId());
        assertEquals(anasTutor, copy.template());
        assertEquals(Map.of("level", "advanced"), copy.variableValues());
        assertEquals(ANA, configurationRepository.findById(copy.id()).orElseThrow().getOwnerId());
        assertEquals(copy, configurationService.findById(ANA, copy.id()));
        assertEquals("English tutor", configurationService.findById(ANA, original.id()).name());
        assertEquals(2, configurationService.findAll(ANA).size());
    }

    @Test
    void duplicateKeepsNameWithinLimit() {
        ConfigurationResponse original = configurationService.create(ANA, request("n".repeat(80), null, Map.of(), false));

        ConfigurationResponse copy = configurationService.duplicate(ANA, original.id());

        assertEquals(80, copy.name().length());
        assertTrue(copy.name().endsWith(" (copy)"), copy.name());
    }

    @Test
    void deletingUsedTemplateIsConflict() {
        configurationService.create(ANA, request("Zulu tutor", anasTutor.id(), Map.of(), false));
        configurationService.create(ANA, request("English tutor", anasTutor.id(), Map.of(), true));
        configurationService.create(ANA, request("Unrelated", null, Map.of(), false));

        TemplateInUseException exception = assertThrows(TemplateInUseException.class,
                () -> templateService.delete(ANA, anasTutor.id()));

        assertEquals(List.of("English tutor", "Zulu tutor"), exception.properties().get("configurations"));
        configurationService.create(ANA, request("abc tutor", anasTutor.id(), Map.of(), false));
        assertEquals(List.of("abc tutor", "English tutor", "Zulu tutor"), assertThrows(TemplateInUseException.class,
                () -> templateService.delete(ANA, anasTutor.id())).properties().get("configurations"));
        assertTrue(exception.getMessage().contains("English tutor"), exception.getMessage());
        assertTrue(templateRepository.existsById(anasTutor.id()));
    }

    @Test
    void deletingUnusedTemplateWorks() {
        ConfigurationResponse user = configurationService.create(ANA, request("English tutor", anasTutor.id(), Map.of(), false));
        configurationService.update(ANA, user.id(), update("English tutor", null, Map.of(), user.version()));

        templateService.delete(ANA, anasTutor.id());

        assertFalse(templateRepository.existsById(anasTutor.id()));
    }

    @Test
    void missingTemplateIsTolerated() {
        ConfigurationResponse created = configurationService.create(ANA,
                request("English tutor", anasTutor.id(), Map.of("level", "advanced"), false));
        // The template vanishes between the "in use" check and its delete (QA-4-2): simulated by deleting it directly.
        templateRepository.deleteById(anasTutor.id());

        ConfigurationResponse found = configurationService.findById(ANA, created.id());

        assertEquals(anasTutor.id(), found.templateId());
        assertNull(found.template());
        assertEquals(Map.of(), found.resolvedVariableValues());
        assertEquals(Map.of("level", "advanced"), found.variableValues());
        assertEquals(1, configurationService.findAll(ANA).size());
        assertThrows(TemplateNotFoundException.class, () -> configurationService.update(ANA, created.id(),
                update("English tutor", anasTutor.id(), Map.of(), found.version())));
    }

    private static CreateConfigurationRequest request(String name, String templateId, Map<String, String> values,
                                                      boolean favorite) {
        return new CreateConfigurationRequest(name, "Daily practice", MODEL, SETTINGS, templateId, values, favorite);
    }

    private static UpdateConfigurationRequest update(String name, String templateId, Map<String, String> values,
                                                     long version) {
        return new UpdateConfigurationRequest(name, "Changed", "Llama-3.2-1B-Instruct-q4f16_1-MLC",
                new GenerationSettingsDto(1.2, 0.9, 1024, 8192, 0.5, -0.5), templateId, values, true, version);
    }
}
