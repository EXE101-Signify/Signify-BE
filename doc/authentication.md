# Authentication module

Authentication uses Java 17, the existing Spring Security configuration, JJWT 0.12.6, BCrypt, the shared `ApiResponse` library, and the existing `users`, `account`, and `user_sessions` tables. The storage implementation and its current `/api/storage/images` route are preserved.

## Architecture and dependency flow

```text
fptu.exe202.signify.signifybe
├── auth
│   ├── api
│   │   ├── AuthController.java
│   │   └── dto
│   │       ├── RegisterRequest.java
│   │       ├── LoginRequest.java
│   │       ├── RefreshTokenRequest.java
│   │       ├── LogoutRequest.java
│   │       ├── AuthResponse.java
│   │       ├── TokenResponse.java
│   │       └── UserResponse.java
│   ├── application
│   │   ├── AuthService.java
│   │   ├── JwtService.java
│   │   ├── JwtProperties.java
│   │   ├── AuthResult.java
│   │   ├── TokenPair.java
│   │   └── port/out
│   │       ├── AccountRepository.java
│   │       ├── UserRepository.java
│   │       └── UserSessionRepository.java
│   ├── domain
│   │   ├── Account.java
│   │   ├── User.java
│   │   ├── UserSession.java
│   │   ├── Role.java
│   │   ├── CurrentUser.java
│   │   ├── UserProfile.java
│   │   ├── SessionMetadata.java
│   │   └── exception/AuthException.java
│   └── infrastructure
│       ├── persistence
│       │   ├── JpaAccountRepository.java
│       │   ├── JpaUserRepository.java
│       │   ├── JpaUserSessionRepository.java
│       │   └── JpaAuthPersistence.java
│       └── security/AuthConfiguration.java
├── security
│   ├── JwtAuthenticationFilter.java
│   └── ApiSecurityErrorHandler.java
└── config/SecurityConfig.java
```

`AuthController → AuthService → repository ports ← JpaAuthPersistence → Spring Data JPA → PostgreSQL`.

`JwtAuthenticationFilter → JwtService → CurrentUser → SecurityContext`.

Domain objects are also JPA entities; there are no duplicate `AccountEntity`/`Account` models. The application depends on persistence ports, not Spring Data repository implementations. There is one `SecurityConfig` and no new global exception handler. The filter-chain error handler emits the existing `ApiResponse` format because these errors happen before MVC advice can handle them. This follows [Spring Security's authentication architecture](https://docs.spring.io/spring-security/reference/servlet/authentication/architecture.html).

## API contract

All routes are under `/api/v1/auth`. Requests and responses use JSON.

| Method | Route | Authentication | Result in `data` |
|---|---|---|---|
| POST | `/register` | Public | `{ user, tokens }` |
| POST | `/login` | Public | `{ user, tokens }` |
| POST | `/refresh` | Refresh token in body | Replacement token pair |
| POST | `/logout` | Access token + refresh token in body | No data; session revoked |
| POST | `/logout-all` | Access token | No data; all user sessions revoked |
| GET | `/me` | Access token | Basic user profile |

Successful responses use HTTP 200, consistently with `doc/standard-api-response.md`:

```json
{
  "success": true,
  "status": 200,
  "message": "Login successful",
  "data": {
    "user": {
      "userId": 42,
      "username": "alice",
      "role": "USER",
      "emailVerified": false
    },
    "tokens": {
      "accessToken": "<access-token>",
      "refreshToken": "<refresh-token>",
      "tokenType": "Bearer",
      "accessExpiresAt": 1790000900000,
      "refreshExpiresAt": 1790604800000
    }
  },
  "timestamp": "2026-09-20T00:00:00Z"
}
```

The example abbreviates nullable profile fields. Token expiration values are epoch **milliseconds**, not durations. Refresh responses put the token fields directly inside `data`. Responses containing tokens and `/me` use `Cache-Control: no-store`.

Validation failures return 400 with the existing field error map. Duplicate usernames return 409. Invalid credentials/tokens return 401. Non-active accounts return 403. Missing or invalid bearer credentials on protected routes return a JSON 401 with `WWW-Authenticate: Bearer`. Insufficient roles return 403. No database errors, password hashes, JWT secrets, or tokens appear in error messages.

## Flows

### Registration and login

Registration accepts required `username` and `password`, with optional `email`, `firstName`, and `lastName`. The username is case-sensitive, preserved as supplied, at most 100 characters, and unique. Email is optional and is not made unique because the existing schema has no such rule. Registration always assigns `USER` and `ACTIVE`, regardless of client-supplied role or user ID. User creation, account creation, and initial session creation run in one transaction.

Passwords must be at least eight characters and at most 72 UTF-8 bytes, respecting BCrypt's input limit. Passwords are not trimmed. Login verifies BCrypt, rejects non-active accounts and soft-deleted users, and creates a session. Accounts without a password hash remain compatible with future OAuth login and cannot use password login. Unknown usernames also perform a dummy BCrypt check.

### JWT access tokens

Access JWTs contain `iss=signify-be`, `sub=<BIGINT user ID>`, `role=USER|ADMIN`, `type=access`, `iat`, and `exp`. Only HS256 signed access tokens are accepted by the request filter. Signature, issuer, expiry, issuance time, type, subject, and role are validated using the existing [JJWT library](https://github.com/jwtk/jjwt/tree/0.12.6).

The filter creates a `CurrentUser` principal with `ROLE_USER` or `ROLE_ADMIN`. Use `@PreAuthorize("hasRole('ADMIN')")`, or `hasAuthority('ROLE_ADMIN')`; JWT/database role values themselves have no `ROLE_` prefix. Controllers can inject `@AuthenticationPrincipal CurrentUser user` and use `user.userId()` without parsing JWTs again.

The filter performs no database lookup and uses no HTTP session. A supplied invalid bearer token is rejected even on otherwise public routes; omit an expired access token when calling login or refresh.

### Refresh tokens and logout

Refresh JWTs contain the user ID, `type=refresh`, issuer/timestamps, and a random 256-bit `jti`. They are distinct even when issued to the same user in the same second. Only SHA-256 of the complete signed token is persisted; the raw token is returned at issuance and never saved in the database. A fast hash is appropriate here because these are high-entropy random tokens, not human passwords.

Refresh validates the JWT and then locks the account row and matching session row. It verifies user/account status, ownership, revocation, and server-side expiration. In the same transaction it revokes the old session and inserts a replacement session with a new token hash. Concurrent refreshes of the same token allow only one success. A failed replacement rolls back the old revocation. Login, refresh, logout, and logout-all use the same account-first lock order.

Logout requires an authenticated identity matching the refresh token owner. Revocation is idempotent for a still-valid refresh JWT. Logout-all revokes every session for the authenticated user. Access JWTs remain valid until their configured expiry; logout, account disabling, and role changes do not immediately invalidate already-issued access JWTs. `/me` and refresh do re-check account/user state.

Replay of a revoked refresh token is rejected. There is no token-family tracking or automatic revocation of all sibling sessions. Clients must serialize refresh requests and replace both stored tokens after a successful refresh.

Device names and user-agent strings are bounded display metadata. IP comes from `HttpServletRequest.getRemoteAddr()`. Untrusted `X-Forwarded-For` is ignored. Existing trusted-proxy behavior is not changed.

## Database migration

`db/changelog/v1.1/016-auth-integrity.yaml` adds only necessary integrity and ID generation:

- BIGINT sequences `auth_users_id_seq` and `auth_user_sessions_id_seq`, with allocation size 1 and column defaults.
- On PostgreSQL, each sequence starts above the corresponding existing maximum ID; existing IDs are not rewritten.
- Unique constraints on `account.username` and `user_sessions.refresh_token_hash`.
- An index on `(user_id, revoked_at)` for session revocation.

There was no existing ID generator, entity, or timestamp implementation to reuse. New timestamp values use epoch milliseconds in the existing BIGINT columns. Account IDs reuse their user's ID. OAuth tables and all original changesets remain unchanged. There is no `refresh_tokens` table.

Migration preconditions stop if duplicate usernames or refresh hashes already exist. Existing conflicting data must be resolved deliberately; the migration does not delete or rename records. It runs through the normal Liquibase startup mechanism. Run against a development/staging PostgreSQL database before production; H2 tests do not verify PostgreSQL's sequence reseeding SQL.

## Configuration and running

| Environment variable | Required/default | Meaning |
|---|---|---|
| `JWT_SECRET` | Required | Random secret, at least 32 UTF-8 bytes; interpreted as raw UTF-8, not Base64-decoded |
| `JWT_ACCESS_EXPIRATION` | `900000` | Access lifetime in milliseconds (15 minutes) |
| `JWT_REFRESH_EXPIRATION` | `604800000` | Refresh lifetime in milliseconds (7 days) |
| `RUN_APPLICATION_INTEGRATION_TESTS` | Unset | Set `true` only to opt into the original full application context test with configured services |

Existing property names remain `app.jwt.secret`, `app.jwt.expiration`, and `app.jwt.refresh-expiration`. The previous 24-hour defaults can be retained explicitly with `JWT_ACCESS_EXPIRATION=86400000` and `JWT_REFRESH_EXPIRATION=86400000`. All application instances must share the signing secret; changing it invalidates existing signed tokens.

For a temporary development secret in PowerShell, generate it without printing it:

```powershell
$bytes = New-Object byte[] 48
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
$rng.Dispose()
$env:JWT_ACCESS_EXPIRATION = '900000'
$env:JWT_REFRESH_EXPIRATION = '604800000'
./gradlew.bat bootRun
```

For normal operation, supply a stable secret through the existing environment/secrets mechanism. Keep the existing database, selected storage provider, SMTP and PayOS settings configured. Spring Boot/Gradle do not automatically load the repository's `.env`; use the existing IDE/environment loader. No `.env` file is read or modified by this implementation. Java toolchain remains **17**.

## Example requests

These are POSIX shell examples; Windows users can run them through Git Bash or use `curl.exe` with the equivalent PowerShell quoting. Replace token variables with the latest values returned by the API. Token responses contain credentials: keep them out of logs and source control.

```bash
BASE=http://localhost:8080

# Register; email and names may be omitted.
curl -X POST "$BASE/api/v1/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"a-long-demo-password","email":"alice@example.com","firstName":"Alice"}'

# Login from a device.
curl -X POST "$BASE/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"a-long-demo-password","deviceName":"My laptop"}'

# Set ACCESS_TOKEN and REFRESH_TOKEN locally from data.tokens.
# Refresh: do not send an expired access token in the Authorization header.
curl -X POST "$BASE/api/v1/auth/refresh" \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}"

# Replace ACCESS_TOKEN and REFRESH_TOKEN with the new data fields before continuing.
curl "$BASE/api/v1/auth/me" -H "Authorization: Bearer $ACCESS_TOKEN"

# Authenticated storage request uses the current, unchanged storage route.
curl -X POST "$BASE/api/storage/images" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -F 'file=@avatar.png;type=image/png'

curl -X POST "$BASE/api/v1/auth/logout" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}"

curl -X POST "$BASE/api/v1/auth/logout-all" \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

## Tests and build

```powershell
./gradlew.bat build
./gradlew.bat test --tests '*auth.*'
```

The default suite requires no AWS/R2/Redis/SMTP credentials. Service tests mock repository ports, JWT tests exercise the real JJWT implementation, HTTP tests use the real security filter chain with mocked application services, and persistence tests use H2 with Liquibase. H2 maps TEXT as CLOB, so Hibernate's PostgreSQL-oriented schema type validation is disabled only for these H2 tests; production retains `ddl-auto: validate`.

The original `SignifyBeApplicationTests` loads the full application and therefore requires external service configuration. It is opt-in via `RUN_APPLICATION_INTEGRATION_TESTS=true` and otherwise reported as skipped, rather than contacting an unconfigured database during unit tests.

On this Windows machine, Java's loopback socket failed when it used the short-name system temporary directory. The verified workaround is process-local, with no build file changes:

```powershell
New-Item -ItemType Directory -Force build/tmp | Out-Null
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-17'
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=D:/BuiGiaHuy/CN8/EXE202/signify-be/build/tmp'
./gradlew.bat build
```

## Executed verification

On 2026-09-20, `./gradlew.bat build --console=plain` completed successfully with the Java 17 toolchain and the process-local temporary-directory workaround shown above. All seven Gradle tasks executed in the final run, including compilation, tests, and executable JAR packaging.

| Suite | Passed | Skipped |
|---|---:|---:|
| Auth service/password tests | 15 | 0 |
| JWT generation/validation tests | 10 | 0 |
| MVC/security/response tests | 12 | 0 |
| H2/Liquibase/persistence/concurrency/rollback tests | 6 | 0 |
| Existing storage service tests | 31 | 0 |
| Existing storage provider-selection tests | 8 | 0 |
| Original external-service application-context test | 0 | 1 |
| **Total** | **82** | **1** |

There were zero failures and zero errors. `git diff --check` also passed. The executable artifact is `build/libs/signify-be-0.0.1-SNAPSHOT.jar`; the HTML test report is `build/reports/tests/test/index.html`.

No live AWS/R2/Redis/SMTP services or real credentials were used. No existing application database was modified. PostgreSQL-specific sequence reseeding was not executed against a live PostgreSQL instance; the complete changelog was exercised against H2, where that PostgreSQL-only SQL is skipped. The full application was compiled/packaged, not booted against the user's external services.
## Scope and limitations

- No OAuth provider flow, password reset, email verification, or password change is implemented. The existing OAuth dependency and table are preserved.
- Revoked refresh sessions are retained for audit; no cleanup scheduler is introduced.
- No login throttling or account-lockout policy is introduced; those policies remain a separate concern.
- Storage key ownership and object-level authorization are unchanged. JWT authentication alone does not establish ownership of an image key.
- JWT secret rotation, token-family replay response, and immediate access-token revocation are outside this stateless MVP.

## File inventory

### Created

| File | Purpose |
|---|---|
| `doc/authentication.md` | Architecture, file inventory, API contract, configuration, examples, testing, and limitations. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/api/AuthController.java` | Seven REST endpoints, request validation, authenticated identity, and standard response envelopes. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/api/dto/AuthResponse.java` | Maps registration/login results into user and token DTOs. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/api/dto/LoginRequest.java` | Login and optional device-name input; redacts password-bearing debug output. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/api/dto/LogoutRequest.java` | Bounded session-revocation input with redacted debug output. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/api/dto/RefreshTokenRequest.java` | Bounded refresh-token input with redacted debug output. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/api/dto/RegisterRequest.java` | Registration input validation; redacts password-bearing debug output. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/api/dto/TokenResponse.java` | Bearer token pair and epoch-millisecond expiry DTO; redacted debug output. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/api/dto/UserResponse.java` | Basic profile DTO that never exposes persistence entities or hashes. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/application/AuthResult.java` | Application result combining a profile and token pair. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/application/AuthService.java` | Transactional registration, login, refresh rotation, revocation, and profile lookup. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/application/JwtProperties.java` | Typed configuration under the existing app.jwt prefix; redacts the signing secret. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/application/JwtService.java` | JJWT signing/validation, token-type separation, and SHA-256 refresh hashing. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/application/TokenPair.java` | Provider-independent application token result with redacted debug output. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/application/port/out/AccountRepository.java` | Application port for account lookup, locking, uniqueness checks, and insertion. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/application/port/out/UserRepository.java` | Application port for existing users-table persistence. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/application/port/out/UserSessionRepository.java` | Application port for hash lookup, session locking, persistence, and revocation. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/domain/Account.java` | JPA model of the existing account table with USER default and ACTIVE status. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/domain/CurrentUser.java` | Authenticated principal exposing the user ID and role. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/domain/Role.java` | USER/ADMIN values and consistent ROLE_ authority conversion. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/domain/SessionMetadata.java` | Bounded device, socket IP, and user-agent metadata. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/domain/User.java` | JPA model of the existing users table using BIGINT sequence IDs and soft-deletion checks. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/domain/UserProfile.java` | Safe application profile projection. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/domain/UserSession.java` | JPA model of existing refresh sessions, storing only hashes and revocation metadata. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/domain/exception/AuthException.java` | Safe auth errors extending the existing response-library BaseException. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/infrastructure/persistence/JpaAccountRepository.java` | Spring Data account queries and pessimistic account locks. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/infrastructure/persistence/JpaAuthPersistence.java` | Single adapter implementing the three repository ports, without duplicate entities. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/infrastructure/persistence/JpaUserRepository.java` | Spring Data users-table access. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/infrastructure/persistence/JpaUserSessionRepository.java` | Spring Data session hash lookup, row locks, and bulk revocation. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/infrastructure/security/AuthConfiguration.java` | JWT property registration and injectable UTC clock. |
| `src/main/java/fptu/exe202/signify/signifybe/security/ApiSecurityErrorHandler.java` | Standard ApiResponse JSON for filter-chain authentication and access failures. |
| `src/main/java/fptu/exe202/signify/signifybe/security/JwtAuthenticationFilter.java` | Stateless bearer validation and SecurityContext population, without per-request database access. |
| `src/main/resources/db/changelog/v1.1/016-auth-integrity.yaml` | Necessary uniqueness, session index, and BIGINT sequence migration; preserves existing tables and IDs. |
| `src/test/java/fptu/exe202/signify/signifybe/auth/api/AuthSecurityWebTest.java` | Actual security-chain, controller, validation, CORS, authorization, and response-envelope tests. |
| `src/test/java/fptu/exe202/signify/signifybe/auth/application/AuthServiceTest.java` | Mocked-port tests for passwords, auth flows, invalid sessions, and revocation. |
| `src/test/java/fptu/exe202/signify/signifybe/auth/application/JwtServiceTest.java` | Real JJWT generation, signature/type/expiry validation, hashing, and safe configuration tests. |
| `src/test/java/fptu/exe202/signify/signifybe/auth/infrastructure/persistence/AuthPersistenceTest.java` | H2/Liquibase tests of persistence, rollback, and concurrent registration/refresh behavior. |

### Modified

| File | Change |
|---|---|
| `.gitignore` | Narrow exception for Java `application/port/out` sources so the existing `out/` ignore rule cannot hide repository/storage ports. |
| `build.gradle.kts` | Adds only test dependencies for MVC security tests and H2 persistence tests; Java 17 and existing JJWT dependencies are preserved. |
| `src/main/java/fptu/exe202/signify/signifybe/config/SecurityConfig.java` | Integrates the JWT filter and standard JSON security errors, narrows public auth matchers, retains statelessness/CORS/Swagger access/BCrypt, and removes the unused AuthenticationManager placeholder. |
| `src/main/resources/application.yaml` | Makes the existing JWT lifetime properties environment-configurable with 15-minute/7-day defaults. |
| `src/main/resources/db/changelog/db.changelog-master.yaml` | Includes the new additive authentication migration. |
| `src/test/java/fptu/exe202/signify/signifybe/SignifyBeApplicationTests.java` | Makes the external-service application-context test explicitly opt-in. |

### Existing source newly visible to Git

`src/main/java/fptu/exe202/signify/signifybe/storage/application/port/out/ObjectStorage.java` already existed and is required by the storage module, but was hidden by `.gitignore`. Its contents are unchanged. The ignore-rule correction makes it eligible to include in the repository alongside the new auth ports.

### Unchanged

- Java 17 toolchain and existing runtime dependencies, including JJWT 0.12.6 and OAuth2 Client.
- `config/CorsConfig.java`, `MailConfig.java`, `OpenApiConfig.java`, `PayOSConfig.java`, and `RedisConfig.java`.
- All storage implementation files and existing storage tests.
- `SignifyBeApplication.java` and `doc/standard-api-response.md`.
- All v1.0 Liquibase changesets, the OAuth table, and other business tables.
- `.env` and all credentials.
