package com.myassistant.template;

import com.myassistant.MongoPersistenceTest;
import com.myassistant.template.dto.CreateTemplateRequest;
import com.myassistant.template.dto.TemplateResponse;
import com.myassistant.template.dto.TemplateVariableDto;
import com.myassistant.template.dto.UpdateTemplateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The template rules against a real MongoDB: owner scoping, the placeholder check, optimistic locking and the "in use"
 * check. {@link TemplateUsage} (implemented by the configuration feature in Phase 5) is a mock. The cases are listed
 * in {@code docs/template-cases.md}.
 */
@MongoPersistenceTest
@Import({TemplateService.class, TemplateMapper.class})
class TemplateServiceTest {
    private static final String ANA = "user-ana";
    private static final String BOB = "user-bob";
    private static final TemplateVariableDto LANGUAGE = new TemplateVariableDto("language", "Language", "English");
    private static final TemplateVariableDto LEVEL = new TemplateVariableDto("level", "Student level", "beginner");

    @Autowired
    private TemplateService templateService;

    @Autowired
    private TemplateRepository templateRepository;

    @MockitoBean
    private TemplateUsage templateUsage;

    @BeforeEach
    void setUp() {
        templateRepository.deleteAll();
    }

    @Test
    void createStoresTemplateForOwner() {
        TemplateResponse created = templateService.create(ANA, tutor("Language tutor"));

        assertNotNull(created.id());
        assertEquals(0, created.version());
        assertEquals("Language tutor", created.name());
        assertEquals(List.of(LANGUAGE, LEVEL), created.variables());
        assertNotNull(created.createdAt());
        assertEquals(created.createdAt(), created.updatedAt());

        PromptTemplate stored = templateRepository.findById(created.id()).orElseThrow();
        assertEquals(ANA, stored.getOwnerId());
        assertEquals("You are a {{language}} tutor for a {{level}} student.", stored.getContent());
        assertEquals(created, templateService.findById(ANA, created.id()));
    }

    @Test
    void createWithoutVariablesHasEmptyList() {
        TemplateResponse created = templateService.create(ANA, new CreateTemplateRequest("Plain", null, "Be brief.", null));

        assertEquals(List.of(), created.variables());
        assertEquals(List.of(), templateService.findById(ANA, created.id()).variables());
    }

    @Test
    void listReturnsOnlyMyTemplatesSortedByName() {
        templateService.create(ANA, tutor("Language tutor"));
        templateService.create(BOB, tutor("Bob's tutor"));
        templateService.create(ANA, tutor("Code helper"));

        assertEquals(List.of("Code helper", "Language tutor"), names(templateService.findAll(ANA)));
        assertEquals(List.of("Bob's tutor"), names(templateService.findAll(BOB)));
        assertEquals(List.of(), templateService.findAll("user-without-templates"));
    }

    @Test
    void listSortIgnoresLetterCase() {
        templateService.create(ANA, tutor("zeta"));
        templateService.create(ANA, tutor("Beta"));
        templateService.create(ANA, tutor("alpha"));

        assertEquals(List.of("alpha", "Beta", "zeta"), names(templateService.findAll(ANA)));
    }

    @Test
    void getReturnsMyTemplate() {
        TemplateResponse created = templateService.create(ANA, tutor("Language tutor"));

        assertEquals("Language tutor", templateService.findById(ANA, created.id()).name());
    }

    // The lookup ConfigurationService uses to embed a template that may have been deleted in the meantime.
    @Test
    void findIsEmptyForUnknownOrOtherUsersTemplate() {
        TemplateResponse anas = templateService.create(ANA, tutor("Language tutor"));

        assertEquals(Optional.of(anas), templateService.find(ANA, anas.id()));
        assertEquals(Optional.empty(), templateService.find(BOB, anas.id()));
        assertEquals(Optional.empty(), templateService.find(ANA, "t9"));
    }

    @Test
    void unknownTemplateIsNotFound() {
        TemplateNotFoundException exception = assertThrows(TemplateNotFoundException.class,
                () -> templateService.findById(ANA, "t9"));

        assertEquals("No template with id t9", exception.getMessage());
    }

    @Test
    void otherUsersTemplateIsNotFound() {
        TemplateResponse anas = templateService.create(ANA, tutor("Language tutor"));

        TemplateNotFoundException onGet = assertThrows(TemplateNotFoundException.class,
                () -> templateService.findById(BOB, anas.id()));
        assertThrows(TemplateNotFoundException.class,
                () -> templateService.update(BOB, anas.id(), update("Taken over", anas.version())));
        assertThrows(TemplateNotFoundException.class, () -> templateService.delete(BOB, anas.id()));

        // The same answer as for an id that does not exist: nothing tells Bob the template is there.
        assertEquals("No template with id " + anas.id(), onGet.getMessage());
        assertEquals(anas, templateService.findById(ANA, anas.id()));
        verify(templateUsage, never()).configurationNamesUsing(any(), any());
    }

    @Test
    void undeclaredPlaceholderIsRejected() {
        CreateTemplateRequest request = new CreateTemplateRequest("Tutor", null,
                "A {{language}} tutor, {{tone}} and {{ level }}; {{tone}} again, {{language}}.", List.of(LANGUAGE));

        InvalidTemplateException exception = assertThrows(InvalidTemplateException.class,
                () -> templateService.create(ANA, request));

        assertEquals(List.of("tone", " level "), exception.properties().get("unknownPlaceholders"));
        assertTrue(exception.getMessage().contains("tone"), exception.getMessage());
        assertEquals(0, templateRepository.count());
    }

    @Test
    void duplicateVariableNamesAreRejected() {
        CreateTemplateRequest request = new CreateTemplateRequest("Tutor", null, "A {{language}} tutor.",
                List.of(LANGUAGE, LEVEL, new TemplateVariableDto("language", "Again", null)));

        InvalidTemplateException exception = assertThrows(InvalidTemplateException.class,
                () -> templateService.create(ANA, request));

        assertEquals(List.of("language"), exception.properties().get("duplicateVariables"));
        assertEquals(0, templateRepository.count());
    }

    @Test
    void unusedVariableIsAccepted() {
        TemplateResponse created = templateService.create(ANA,
                new CreateTemplateRequest("Tutor", null, "A {{language}} tutor.", List.of(LANGUAGE, LEVEL)));

        assertEquals(2, created.variables().size());
    }

    @Test
    void twentyVariablesAreAccepted() {
        List<TemplateVariableDto> variables = IntStream.rangeClosed(1, 20)
                .mapToObj(i -> new TemplateVariableDto("v" + i, null, null))
                .toList();

        assertEquals(20, templateService.create(ANA,
                new CreateTemplateRequest("Many", null, "{{v1}} and {{v20}}", variables)).variables().size());
    }

    @Test
    void updateChangesTemplateAndVersion() {
        TemplateResponse created = templateService.create(ANA, tutor("Language tutor"));

        TemplateResponse updated = templateService.update(ANA, created.id(), update("Patient tutor", created.version()));

        assertEquals(created.id(), updated.id());
        assertEquals(created.version() + 1, updated.version());
        assertEquals("Patient tutor", updated.name());
        assertEquals("Speak {{language}} only.", updated.content());
        assertEquals(List.of(LANGUAGE), updated.variables());
        assertEquals(created.createdAt(), updated.createdAt());
        assertFalse(updated.updatedAt().isBefore(created.updatedAt()));
        assertEquals(updated, templateService.findById(ANA, created.id()));
    }

    @Test
    void staleVersionIsConflict() {
        TemplateResponse created = templateService.create(ANA, tutor("Language tutor"));
        templateService.update(ANA, created.id(), update("First tab", created.version()));

        assertThrows(OptimisticLockingFailureException.class,
                () -> templateService.update(ANA, created.id(), update("Second tab", created.version())));

        assertEquals("First tab", templateService.findById(ANA, created.id()).name());
    }

    @Test
    void updateChecksPlaceholdersToo() {
        TemplateResponse created = templateService.create(ANA, tutor("Language tutor"));

        InvalidTemplateException exception = assertThrows(InvalidTemplateException.class,
                () -> templateService.update(ANA, created.id(), new UpdateTemplateRequest("Tutor", null,
                        "A {{language}} tutor in {{city}}.", List.of(LANGUAGE), created.version())));

        assertEquals(List.of("city"), exception.properties().get("unknownPlaceholders"));
        assertEquals(created, templateService.findById(ANA, created.id()));
    }

    @Test
    void deleteRemovesTemplate() {
        TemplateResponse created = templateService.create(ANA, tutor("Language tutor"));
        when(templateUsage.configurationNamesUsing(ANA, created.id())).thenReturn(List.of());

        templateService.delete(ANA, created.id());

        assertFalse(templateRepository.existsById(created.id()));
    }

    @Test
    void deleteInUseIsConflict() {
        TemplateResponse created = templateService.create(ANA, tutor("Language tutor"));
        when(templateUsage.configurationNamesUsing(ANA, created.id())).thenReturn(List.of("English tutor", "Code helper"));

        TemplateInUseException exception = assertThrows(TemplateInUseException.class,
                () -> templateService.delete(ANA, created.id()));

        assertEquals(List.of("English tutor", "Code helper"), exception.properties().get("configurations"));
        assertTrue(templateRepository.existsById(created.id()));
    }

    private static CreateTemplateRequest tutor(String name) {
        return new CreateTemplateRequest(name, "Corrects mistakes",
                "You are a {{language}} tutor for a {{level}} student.", List.of(LANGUAGE, LEVEL));
    }

    private static UpdateTemplateRequest update(String name, long version) {
        return new UpdateTemplateRequest(name, "Changed", "Speak {{language}} only.", List.of(LANGUAGE), version);
    }

    private static List<String> names(List<TemplateResponse> templates) {
        return templates.stream().map(TemplateResponse::name).toList();
    }
}
