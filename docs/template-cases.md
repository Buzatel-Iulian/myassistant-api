# Prompt templates: /api/templates

Bodies:

- `POST /api/templates`:
  ```json
  {
    "name": "Language tutor",
    "description": "Corrects and explains mistakes",
    "content": "You are a patient {{language}} tutor. The student's level is {{level}}.",
    "variables": [
      { "name": "language", "label": "Language", "defaultValue": "English" },
      { "name": "level", "label": "Student level", "defaultValue": "beginner" }
    ]
  }
  ```
- `PUT /api/templates/{id}`: the same fields **plus `version`**, the `version` of the template as it was read (`GET` answers it). PUT sends every field.
- `GET /api/templates`, `GET /api/templates/{id}`, `DELETE /api/templates/{id}`: no body.

Every request needs `Authorization: Bearer <token>`; the owner of a template is the token's user (`sub`).

The answer is a `TemplateResponse`: `{ id, version, name, description, content, variables: [{ name, label, defaultValue }], createdAt, updatedAt }`. The owner id is not in it.

Every row is checked by a test. `TemplateControllerTest` (`@WebMvcTest`, the service is a mock, the real `SecurityConfig`) checks the HTTP side, `TemplateServiceTest` (`@MongoPersistenceTest`, a real MongoDB, `TemplateUsage` mocked) the rules, `PromptTemplateTest` (no Spring) the constraints on the stored document.

| Case | Expected | Test |
| --- | --- | --- |
| List my templates | 200, only my templates, sorted by name | `listReturnsMyTemplates` (controller), `listReturnsOnlyMyTemplatesSortedByName` (service) |
| Get one of my templates | 200, the template with its `version` and variables | `getReturnsTemplate` (controller), `getReturnsMyTemplate` (service) |
| Get an unknown id | 404 ProblemDetail "No template with id …" | `unknownTemplateIsNotFound` (controller + service) |
| User B gets, updates or deletes user A's template | 404, the same answer as an unknown id (never 403); A's template is unchanged | `otherUsersTemplateIsNotFound` (service) |
| Create a valid template | 201, `Location: …/api/templates/{id}`, the template; stored with my id as owner, `version` 0, `createdAt` = `updatedAt` | `createReturns201WithLocation` (controller), `createStoresTemplateForOwner` (service) |
| Create without `variables` | 201, `variables` is `[]` | `createWithoutVariablesHasEmptyList` (service) |
| Blank name, name over 80, blank content, content over 8000, description over 500 | 400 with `fieldErrors` for each field; the service is never called | `invalidTemplateIsBadRequest` (controller), `PromptTemplateTest` (entity) |
| Variable name not matching `[a-zA-Z][a-zA-Z0-9_]{0,39}` (starts with a digit, has a space, over 40 characters, missing) | 400 with `fieldErrors["variables[i].name"]`; the service is never called | `invalidVariableNameIsBadRequest` (controller), `PromptTemplateTest` (entity) |
| More than 20 variables | 400 with `fieldErrors.variables`; the service is never called | `tooManyVariablesIsBadRequest` (controller), `PromptTemplateTest` (entity) |
| Exactly 20 variables | accepted | `twentyVariablesAreAccepted` (service) |
| `content` uses a `{{placeholder}}` that is not declared in `variables` | 400 ProblemDetail, `detail` names the placeholders (written as `{{name}}`), `unknownPlaceholders` lists them (each once, in order of appearance); nothing is saved | `undeclaredPlaceholderIsBadRequest` (controller), `undeclaredPlaceholderIsRejected` (service) |
| Two variables with the same name | 400 ProblemDetail, `duplicateVariables` lists the names; nothing is saved | `duplicateVariableNamesAreRejected` (service), `undeclaredPlaceholderIsBadRequest` (controller, the shared 400 shape) |
| A declared variable that the content does not use | accepted | `unusedVariableIsAccepted` (service) |
| Update with the current `version` | 200, the new values, `version` + 1, `createdAt` kept, `updatedAt` moved | `updateReturnsTemplate` (controller), `updateChangesTemplateAndVersion` (service) |
| Update without `version` | 400 with `fieldErrors.version`; the service is never called | `updateWithoutVersionIsBadRequest` (controller) |
| Update with a stale `version` | 409 "This item was changed in another tab. Reload it and try again."; the stored template is unchanged | `staleVersionIsConflict` (controller + service) |
| Update with an undeclared placeholder | 400, as on create; nothing is saved | `updateChecksPlaceholdersToo` (service) |
| Delete my template, no configuration uses it | 204; it is gone | `deleteAnswersNoContent` (controller), `deleteRemovesTemplate` (service) |
| Delete a template that configurations still use | 409 ProblemDetail, `configurations` lists their names; the template is kept | `deleteInUseIsConflict` (controller + service) |
| Any template endpoint without a token | 401 ProblemDetail; the service is never called | `missingTokenIsUnauthorized` (controller) |

All errors are a `ProblemDetail` (`application/problem+json`). The 404 and 409 come from `NotFoundException`/`ConflictException`, the stale `version` from `OptimisticLockingFailureException`, the placeholder 400 from `BadRequestException` (all in `shared.error`, answered by `GlobalExceptionHandler`).

## Placeholders

A placeholder is `{{` + one or more characters that are not `{` or `}` + `}}`, taken exactly as written: `{{ language }}` (with spaces) is the placeholder ` language `, which no variable name can match, so it is reported as unknown. The web client renders exactly `{{name}}`.

## Who uses a template

The "delete in use" check asks a `TemplateUsage` port (an interface in the `template` package: `configurationNamesUsing(ownerId, templateId)`). The `configuration` feature implements it in Phase 5. Until then there is no implementation, and nothing uses a template.

## Not yet decided

- Two templates of the same user may have the same name.
- _(Phase 5)_ The sort now ignores letter case (`alpha`, `Beta`, `zeta`): `listSortIgnoresLetterCase` in `TemplateServiceTest`.
- `label` and `defaultValue` of a variable are optional (≤ 80 and ≤ 1000 characters); the web client shows the name when there is no label.
