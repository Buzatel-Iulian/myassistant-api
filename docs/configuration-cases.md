# LLM configurations ("assistants"): /api/configurations

Bodies:

- `POST /api/configurations`:
  ```json
  {
    "name": "English tutor",
    "description": "Daily English practice",
    "modelId": "Qwen2.5-1.5B-Instruct-q4f16_1-MLC",
    "settings": {
      "temperature": 0.7, "topP": 0.95, "maxTokens": 512, "contextWindowSize": 4096,
      "frequencyPenalty": 0.0, "presencePenalty": 0.0
    },
    "templateId": "uuid of one of my templates, or null",
    "variableValues": { "language": "English" },
    "favorite": true
  }
  ```
- `PUT /api/configurations/{id}`: the same fields **plus `version`**, the `version` that was read. PUT sends every field.
- `GET /api/configurations`, `GET /api/configurations/{id}`, `DELETE /api/configurations/{id}`, `POST /api/configurations/{id}/duplicate`: no body.

Every request needs `Authorization: Bearer <token>`; the owner of a configuration is the token's user (`sub`).

The answer (also for every item of the list) is a `ConfigurationResponse`:

```json
{
  "id": "…", "version": 0, "name": "English tutor", "description": "…", "modelId": "…-MLC",
  "settings": { "temperature": 0.7, "topP": 0.95, "maxTokens": 512, "contextWindowSize": 4096,
                "frequencyPenalty": 0.0, "presencePenalty": 0.0 },
  "templateId": "…",
  "template": { "id": "…", "version": 0, "name": "…", "content": "…", "variables": [ … ], … },
  "variableValues": { "language": "English" },
  "resolvedVariableValues": { "language": "English", "level": "beginner" },
  "favorite": true, "createdAt": "…", "updatedAt": "…"
}
```

The owner id is not in it. `template` is the full `TemplateResponse` (null when `templateId` is null).

## Variable values: stored as given, resolved in the answer

- `variableValues` is **stored exactly as the user sent it** (only the overrides). It is what the edit form shows; a variable left out (or blank) shows the template's default as a placeholder.
- `resolvedVariableValues` is computed on every read: **one entry per variable of the template, in the template's order**, the given value when it is not blank, otherwise the template's `defaultValue` (`""` when it has none). With it and `template.content` the web client renders the system prompt (Phase 10) without a second call.
- Filling the defaults in on save was rejected: a later change of a template default would then never reach the configurations that did not override it.

Every row is checked by a test. `ConfigurationControllerTest` (`@WebMvcTest`, the service is a mock, the real `SecurityConfig`) checks the HTTP side; `ConfigurationServiceTest` (`@MongoPersistenceTest`, a real MongoDB, the **real** `TemplateService` and `TemplateUsage` implementation) the rules; `GenerationSettingsTest` and `LlmConfigurationTest` (no Spring) the constraints on the stored document; `StarterKitTest` (`@MongoPersistenceTest` with `AuthService`) the starter kit; `StarterKitPropertiesTest` (no Spring) the starter-kit settings check.

| Case | Expected | Test |
| --- | --- | --- |
| List my configurations | 200, only mine, **favorites first, then by name**, each with its template embedded | `listReturnsMyConfigurations` (controller), `listIsFavoritesFirstThenByName` (service) |
| The name order ignores letter case (`alpha`, `beta`, `Gamma`; templates too) | sorted as a person reads it | `listSortIgnoresLetterCase` (configuration and template service) |
| Get one of my configurations | 200, with `version`, `settings`, `variableValues`, `resolvedVariableValues` and the template embedded | `getReturnsConfigurationWithTemplate` (controller), `getEmbedsTemplateAndResolvesVariables` (service) |
| Get a configuration without a template (`templateId` null) | 200, `template` null, `resolvedVariableValues` `{}` | `getWithoutTemplate` (service), `getReturnsConfigurationWithTemplate` (controller, null template shape) |
| Get an unknown id | 404 ProblemDetail "No configuration with id …" | `unknownConfigurationIsNotFound` (controller + service) |
| User B gets, updates, duplicates or deletes user A's configuration | 404, the same answer as an unknown id (never 403); A's configuration is unchanged | `otherUsersConfigurationIsNotFound` (service) |
| Create a valid configuration | 201, `Location: …/api/configurations/{id}`; stored with my id as owner, `version` 0, `createdAt` = `updatedAt` | `createReturns201WithLocation` (controller), `createStoresConfigurationForOwner` (service) |
| Create without `variableValues` / without `favorite` | 201, `variableValues` `{}`, `favorite` false | `createWithDefaults` (service) |
| Create with a `templateId` that does not exist, or that is **another user's** template | 404 "No template with id …"; nothing is saved | `templateOfAnotherUserIsNotFound` (service), `unknownTemplateOnCreateIsNotFound` (controller) |
| `templateId` is `""` or only spaces | the same as null: no template, stored as null, `template` null (create and update) | `blankTemplateIdMeansNoTemplate` (service) |
| `variableValues` with keys the template does not declare (or any key when `templateId` is null) | 400 ProblemDetail, `unknownVariables` lists them (in the order sent); nothing is saved | `unknownVariableKeysAreBadRequest` (controller), `unknownVariableKeysAreRejected` (service) |
| Missing or blank variable values | accepted; stored as given; `resolvedVariableValues` uses the template's `defaultValue` (`""` when none) | `missingVariablesFallBackToDefaults` (service) |
| Blank name, name over 80, description over 500 | 400 `fieldErrors`; the service is never called | `invalidConfigurationIsBadRequest` (controller), `LlmConfigurationTest` |
| `modelId` blank or not ending in `-MLC` (e.g. `gpt-4o`), over 200 characters | 400 `fieldErrors.modelId` | `invalidConfigurationIsBadRequest` (controller), `LlmConfigurationTest` |
| `settings` missing | 400 `fieldErrors.settings` | `invalidSettingsAreBadRequest` (controller), `LlmConfigurationTest` |
| `temperature` < 0 or > 2; `topP` < 0 or > 1; `maxTokens` < 1 or > 4096; `contextWindowSize` < 512 or > 32768; `frequencyPenalty` / `presencePenalty` < −2 or > 2; any of them missing | 400 `fieldErrors["settings.<field>"]`; the limits themselves (0, 2, 1, 4096, 512, 32768, −2, 2) are accepted | `invalidSettingsAreBadRequest` (controller), `GenerationSettingsTest` |
| `maxTokens` not below `contextWindowSize` (4096/512, 512/512) | 400 `fieldErrors["settings.maxTokens"]` "must be less than the context window size", on POST and PUT, and for the stored document before save; 511/512 is accepted | `maxTokensNotBelowContextWindowIsBadRequest` (controller), `maxTokensIsBelowContextWindowSize` (`GenerationSettingsTest`), `settingsAreRequiredAndValidated` (`LlmConfigurationTest`) |
| More than 20 variable values, a value over 1000 characters, a null value | 400 `fieldErrors` | `invalidConfigurationIsBadRequest` (controller), `LlmConfigurationTest` |
| Update with the current `version` | 200, the new values, `version` + 1, `createdAt` kept, `updatedAt` moved | `updateReturnsConfiguration` (controller), `updateChangesConfigurationAndVersion` (service) |
| Update without `version` | 400 `fieldErrors.version`; the service is never called | `updateWithoutVersionIsBadRequest` (controller) |
| Update with a stale `version` | 409 "This item was changed in another tab. Reload it and try again."; the stored configuration is unchanged | `staleVersionIsConflict` (controller + service) |
| Update with another user's template or unknown variable keys | 404 / 400, as on create; nothing is saved | `updateChecksTemplateAndVariables` (service) |
| Delete my configuration | 204; it is gone | `deleteAnswersNoContent` (controller), `deleteRemovesConfiguration` (service) |
| Duplicate my configuration | 201 + `Location` of the copy; a new id, name "… (copy)", `version` 0, `favorite` false, same model, settings, template and values, same owner; the original is unchanged | `duplicateReturns201WithLocation` (controller), `duplicateCopiesConfiguration` (service) |
| Duplicate a configuration whose name is already 80 characters | the copy's name is cut so that "… (copy)" fits in 80 | `duplicateKeepsNameWithinLimit` (service) |
| **Delete a template that a configuration uses** (real `TemplateUsage` implementation in `configuration`) | 409 from `DELETE /api/templates/{id}`, `configurations` lists the names of **my** configurations that use it (sorted by name, ignoring letter case); the template is kept. Another user's configurations never appear | `deletingUsedTemplateIsConflict` (service) |
| Delete a template no configuration uses any more | 204 | `deletingUnusedTemplateWorks` (service) |
| A configuration whose template was deleted in between (the check-then-delete race of QA-4-2) | GET still works: `templateId` kept, `template` null, `resolvedVariableValues` `{}`; saving it with that `templateId` again → 404 | `missingTemplateIsTolerated` (service) |
| Any configuration endpoint without a token | 401 ProblemDetail; the service is never called | `missingTokenIsUnauthorized` (controller) |
| **Register a new user** | the starter kit is created for that user: templates "Code helper" and "General assistant", configurations "General assistant" (favorite, `Qwen2.5-0.5B-Instruct-q4f16_1-MLC`) and "Code helper" (`Qwen2.5-Coder-1.5B-Instruct-q4f16_1-MLC`), each pointing at its starter template; nothing for other users | `registrationCreatesStarterKit` (`StarterKitTest`) |
| Starter-kit creation fails (e.g. the database refuses a write) | registration still answers with the user (201); the account works; the failure is logged | `registrationSurvivesStarterKitFailure` (`StarterKitTest`) |
| Starter-kit settings refer to an unknown template key or an undeclared variable | the application refuses to start (`IllegalStateException`) | `unknownTemplateKeyIsRejected`, `undeclaredVariableIsRejected` (`StarterKitPropertiesTest`) |

All errors are a `ProblemDetail` (`application/problem+json`): 404 from `NotFoundException` (`ConfigurationNotFoundException`, `TemplateNotFoundException`), 400 from `BadRequestException` (`UnknownVariablesException`) or `@Valid`, 409 from `OptimisticLockingFailureException` (stale version) or `ConflictException` (`TemplateInUseException`).

## Starter kit

At registration, `AuthService` publishes an `auth.UserRegistered` event after the user is saved. `configuration.StarterKit` listens to it (synchronously, same thread) and creates the templates and configurations listed under `myassistant.starter-kit` in `application.yml` (bound to `configuration.StarterKitProperties`, validated at startup) through `TemplateService` and `ConfigurationService`, so the starter items pass exactly the same rules as the user's own. `auth` therefore depends on neither `template` nor `configuration` (ArchUnit rule).

A failure while creating the kit is caught and logged by the listener: the account is what matters, so the registration still succeeds (MongoDB runs without transactions here; a half-created kit is left as it is, and the user can delete or create items). Model ids are real WebLLM prebuilt ids (`@mlc-ai/web-llm` 0.2.88 `prebuiltAppConfig`).

## Not yet decided

- Two configurations of the same user may have the same name (a duplicate of "X (copy)" is "X (copy) (copy)").
