<!--
  AUTO-GENERATED FILE — DO NOT EDIT.
  Source: AGENTS.md
  Regenerate: sh scripts/sync-agent-instructions.sh
-->

# Agent instructions — MyAssistant API

`myassistant-api` is the REST API of MyAssistant: a Spring Boot 4 application on Java 25, built with Gradle (Kotlin DSL + version catalog `gradle/libs.versions.toml`), storing data in MongoDB. It holds user accounts (JWT authentication), prompt templates and LLM configurations ("assistants"). **It never sees prompts or answers: inference runs in the browser.**

This project is built **step by step, in phases**. Read the rules below before proposing or writing anything.

## 1. Requirements are the single source of truth

- **`docs/requirements.md` in the wrapper repository (`myassistant`) is the single source of truth.** It defines the phases, what is in scope now and what is deferred. `docs/architecture.md` there holds the data model and the endpoint table.
- If a conversation conflicts with `docs/requirements.md`, **the file wins** until it is updated.
- When a decision changes, update `docs/requirements.md` **in the same step** as the code. A deferred requirement is moved (to a later phase, *Later* or *Non-goals*), never silently dropped.
- **One new concept per phase.** Do not implement future phases in advance, and do not add dependencies, classes or configuration a later phase will need.

## 2. How we work: tests first

- The behaviour of a feature is written down first as a **cases table** (`docs/<feature>-cases.md`), then as **tests written by hand** (`src/test/**`), committed before the implementation.
- The coding agent writes the code in `src/main/**` that makes those tests pass. **It does not edit tests** (`.claude/settings.json` denies `Edit(src/test/**)`).
- **Write the least code that makes the tests pass.** No extra endpoints, no extra classes.
- When done, run `./gradlew build` and report the result. The build needs Docker: persistence tests start MongoDB in Testcontainers.
- Test kinds: `@WebMvcTest` for controllers (service mocked); `@MongoPersistenceTest` (in `src/test/java/com/myassistant`) for repositories and services against a real MongoDB (`TestcontainersConfiguration`, entity validation included); `ArchitectureTest` (ArchUnit) for the dependency rules below.

## 3. Code rules

- Base package `com.myassistant`, **organised by feature**: `auth`, `template`, `configuration`. Each feature has its entity, controller, service, repository, mapper, exceptions and a `dto/` package. `shared.error` holds the base exceptions and `GlobalExceptionHandler`; `shared.security` holds `CurrentUser`; `config` holds the Spring configuration.
- **Controllers only call their own service.** Services return DTOs built by the feature's mapper; **entities never leave the service.**
- `configuration` may call `TemplateService`; **`template` never depends on `configuration`.** `ConfigurationService` reads templates through `TemplateService`, never `TemplateRepository`. ArchUnit enforces these rules.
- **Entities import nothing from Spring Web.** Validation uses Jakarta Bean Validation annotations (`@NotBlank`, `@Size`, `@Pattern`, ...) on DTOs (`@Valid` → 400) and on entities (validated again before every save), not hand-written `if ... throw` checks.
- **Business errors are exceptions extending `NotFoundException` (404) or `ConflictException` (409).** `GlobalExceptionHandler` turns them into a `ProblemDetail` (`application/problem+json`); a `ConflictException`'s `properties()` are added to it. **No `try/catch` in controllers.**
- Error shapes the web client relies on: invalid `@Valid` body or invalid entity before save → 400 with `fieldErrors` (`{ "<field>": "<message>" }`); stale `@Version` → 409 *"This item was changed in another tab. Reload it and try again."*; duplicate unique key → 409 (the duplicated value is never echoed).
- **Constructor injection via Lombok `@RequiredArgsConstructor`. No field injection.**
- **Every query is scoped by the owner id.** An item that exists but belongs to someone else is answered with **404, never 403**, so the API does not reveal that it exists.
- Profiles: `application.yml` (shared), `application-local.yml` (`make api-dev`, MongoDB on `localhost`), `application-docker.yml` (compose, host from `SPRING_MONGODB_HOST`).
- Use DTO records for requests and responses. Concurrent edits are handled with `@Version` (stale update → 409).
- Document endpoints for Swagger (`@ApiResponse` for 400/401/404/409 with `application/problem+json`).

## 4. Secrets and privacy

- The only secret is `MYASSISTANT_JWT_SECRET` (JWT signing key). It comes from the environment (the wrapper's gitignored `.env`). **Never commit it, never log it, never put it in a doc or an error message.** Fail fast at startup if it is shorter than 32 bytes.
- Passwords are hashed with BCrypt and never returned.
- Do not log request bodies.

## 5. External integrations

Every external integration (MongoDB, ...) is documented in the wrapper's `docs/<integration>/README.md`, from `docs/_template/README.md`, in the same step that adds it. Only variable names, never real values.

## 6. Language and commits

- Code, identifiers, comments, docs and commit messages are in **English**. Comments explain **why**, never what.
- Do not commit anything unless the user asks.

## 7. These instruction files are generated

`AGENTS.md` is the canonical file. `CLAUDE.md` and `.github/copilot-instructions.md` are **generated copies** — do not edit them.

To change conventions: edit `AGENTS.md`, then run

```sh
sh scripts/sync-agent-instructions.sh
```

`sh scripts/sync-agent-instructions.sh --check` verifies the copies are in sync and exits non-zero if they are not.
