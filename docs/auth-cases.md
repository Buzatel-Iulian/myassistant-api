# Accounts and authentication: /api/auth/register, /api/auth/login, /api/users/me

Bodies:

- `POST /api/auth/register`: `{"email": "ana@example.com", "password": "secret-pass", "displayName": "Ana"}`
- `POST /api/auth/login`: `{"email": "ana@example.com", "password": "secret-pass"}`
- `GET /api/users/me`: no body, header `Authorization: Bearer <token>`

The register path is `POST /api/auth/register` (confirmed in Phase 3, as the Phase 0 docs assumed: QA-0-3).

Every row is checked by a test. `AuthControllerTest` (`@WebMvcTest`, the service is a mock) checks the HTTP side, `AuthServiceTest` (`@MongoPersistenceTest`, a real MongoDB) the rules, `SecurityIntegrationTest` (the whole application, a real MongoDB, real tokens) the security filter, and `JwtSecretTest` the signing secret.

| Case | Expected | Test |
| --- | --- | --- |
| Register with valid email + password | 201, the user `{ id, email, displayName }`, never the password or its hash | `registerCreatesUser` (controller + service) |
| The email is stored lower-cased and trimmed; the password is stored as a BCrypt hash | `email` = `ana@example.com` for `" Ana@Example.COM "`; the hash is not the password and matches it | `registerCreatesUser` (service) |
| Register with an existing email, in any letter case | 409 ProblemDetail "An account with this email already exists." | `duplicateEmailIsConflict` (controller + service) |
| Register with invalid email / short password (< 8) / blank display name / missing email | 400 with `fieldErrors`, the service is never called | `invalidRegistrationIsBadRequest` |
| Register with a password longer than 72 bytes (BCrypt's limit, e.g. 40 × `é`) | 400 with `fieldErrors.password`, the service is never called | `passwordLongerThan72BytesIsBadRequest` |
| Login without a password | 400 with `fieldErrors`, the service is never called | `incompleteLoginIsBadRequest` |
| Login with correct credentials, in any email letter case | 200, `{ token, expiresAt }`; the token's `sub` is the user id and it expires after `myassistant.jwt.ttl` (8 h) | `loginReturnsToken`, `loginIgnoresEmailCase` |
| Login with wrong password / unknown email | 401 ProblemDetail, the same detail "Invalid email or password." for both | `wrongCredentialsAreUnauthorized` (controller + service), `wrongPasswordIsUnauthorizedProblemDetail` (integration) |
| `GET /api/users/me` with token | 200, `{ id, email, displayName }` of the token's user | `meReturnsCurrentUser` (controller + service), `realTokenFromLoginOpensMe` (integration) |
| A valid token whose user no longer exists | 404 ProblemDetail | `unknownUserIsNotFound` (service) |
| Any protected endpoint without token / expired token / broken or foreign token | 401 ProblemDetail "A valid login token is required. Log in and try again." with `WWW-Authenticate: Bearer` | `missingTokenIsUnauthorized` (controller + integration), `invalidTokenIsUnauthorized` (controller), `expiredTokenIsUnauthorized`, `tokenSignedWithAnotherKeyIsUnauthorized` (integration) |
| Register, login, Swagger UI, the OpenAPI document and health work without a token | 2xx; the OpenAPI document declares the `bearerAuth` scheme (Swagger's **Authorize** button) | `publicEndpointsNeedNoToken` (integration) |
| The signing secret is shorter than 32 bytes | the application does not start; the error names the setting, never its value, and nothing logs it | `shortSecretIsRefused`, `missingSecretIsRefused`, `secretOf32BytesIsAccepted`, `secretIsNotInToString`, `applicationDoesNotStartWithAShortSecretAndDoesNotLogIt` |

All errors are a `ProblemDetail` (`application/problem+json`), the 401 answers included: they come from `GlobalExceptionHandler`, for the login and for the security filter's "no valid token".

## Errors that are not about authentication (QA-2-1)

Checked in `GlobalExceptionHandlerTest`:

| Case | Expected | Test |
| --- | --- | --- |
| An unexpected exception in a controller | 500 ProblemDetail "Something went wrong. Try again later."; the exception's message is logged, never sent | `unexpectedExceptionIs500WithoutDetails` |
| Spring Security's own exceptions thrown inside a controller | not a 500: `AuthenticationException` → 401, `AccessDeniedException` → 403 | `authenticationExceptionIsNot500`, `accessDeniedIsNot500` |
| Framework 4xx errors (wrong method, wrong content type) | their own 4xx status, still a ProblemDetail | `wrongMethodIsStill405`, `wrongContentTypeIsStill415` |

## Not yet decided

- The 201 of register has no `Location` header: there is no `GET /api/users/{id}`, and a user only ever reads themselves through `/api/users/me`.
- Registration does not log the user in: the client calls login next. (Phase 5 adds the starter kit at registration.)
