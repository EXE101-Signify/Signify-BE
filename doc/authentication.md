# Authentication module

Authentication uses Java 17, the existing Spring Security configuration, JJWT 0.12.6, BCrypt, the shared `ApiResponse` library, and the existing `users`, `account`, and `user_sessions` tables. The storage implementation and its current `/api/storage/images` route are preserved.

## Architecture and dependency flow

```text
auth/
  api/                     Login, refresh, logout and auth/token DTOs
  application/             AuthService, JwtService, TokenService and account/session ports
  domain/                  Account, Role, CurrentUser, UserSession and auth errors
  infrastructure/          Account/session persistence and JWT configuration
user/
  api/                     Registration, current profile and user DTOs
  application/             UserService and UserRepository port
  domain/                  User, UserProfile and registration errors
  infrastructure/          JpaUserPersistence and JpaUserRepository
  mapper/                  UserMapper
security/                  JWT filter and API security error handler
config/SecurityConfig.java
```

`AuthController → AuthService → repository ports ← JpaAuthPersistence → Spring Data JPA → PostgreSQL`.

`JwtAuthenticationFilter → JwtService → CurrentUser → SecurityContext`.

Domain objects are also JPA entities; there are no duplicate `AccountEntity`/`Account` models. The application depends on persistence ports, not Spring Data repository implementations. There is one `SecurityConfig` and no new global exception handler. The filter-chain error handler emits the existing `ApiResponse` format because these errors happen before MVC advice can handle them. This follows [Spring Security's authentication architecture](https://docs.spring.io/spring-security/reference/servlet/authentication/architecture.html).

## API contract

Authentication routes are under `/api/auth`; registration and profiles are under `/api/users`. Requests and responses use JSON.

| Method | Route | Authentication | Result in `data` |
|---|---|---|---|
| POST | `/api/users/register` | Public | `{ user, tokens }` |
| POST | `/api/auth/login` | Public | `{ user, tokens }` |
| POST | `/api/auth/refresh` | Refresh token in body | Replacement token pair |
| POST | `/api/auth/logout` | Access token + refresh token in body | No data; session revoked |
| POST | `/api/auth/logout-all` | Access token | No data; all user sessions revoked |
| GET | `/api/users/me` | Access token | Basic user profile |

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

Access JWTs contain `sub=<BIGINT user ID>`, the persisted account's `username`, `roles=[USER|ADMIN]`, `iat`, `exp`, configured `iss` and `aud`, and a new UUID `jti` for every access token. Existing `role=USER|ADMIN` and `type=access` claims are retained for compatibility. The current database has no user UUID; `sub` deliberately remains the actual database Long ID, serialized as a string. No database migration is required.

Only HS256 signed access tokens are accepted by the request filter. Signature, issuer, expiry, issuance time, type, subject, and role are validated using the existing JJWT library. Audience must include the configured audience when present. Legacy tokens without `aud` remain accepted so existing sessions survive deployment; audience is not mandatory on incoming tokens. The filter then checks active user/account state and uses the current database role for authorization. Token payloads contain no passwords, password hashes, refresh tokens, keys or OTPs.

Example before:
```json
{"sub":"42","role":"USER","type":"access","iat":1758541800,"exp":1758542700,"iss":"signify-be"}
```

Example after (JJWT serializes audience as an array):
```json
{"sub":"42","username":"huybg","roles":["USER"],"role":"USER","type":"access","iat":1758541800,"exp":1758542700,"iss":"signify-be","aud":["signify-client"],"jti":"7f4c8c9a-7e9f-4c3d-9a2b-123456789abc"}
```

`TokenService.issueTokens` passes database identity, username and role to `JwtService.createAccessToken` for registration, login and refresh. The shared builder reuses the existing clock and registered claim setters. `JwtService` construction and `builder` read issuer from `JwtProperties`; `validateAccessToken` checks audience when present. `JwtProperties` requires nonblank issuer and audience. API responses, principal type, logout and refresh encryption/rotation remain unchanged. Changing issuer intentionally invalidates outstanding access and refresh tokens; use the default issuer to preserve them.

`JwtPayloadTest` covers exact payload fields, actual account username/role, unique UUID IDs, timestamps, signature/issuer/audience/expiry/type validation, legacy tokens, refresh encryption/token separation, and blank configuration. Older tests, if restored, need the username argument in `createAccessToken` and issuer/audience arguments in `JwtProperties`.

The filter creates a `CurrentUser` principal with `ROLE_USER` or `ROLE_ADMIN`. Use `@PreAuthorize("hasRole('ADMIN')")`, or `hasAuthority('ROLE_ADMIN')`; JWT/database role values themselves have no `ROLE_` prefix. Controllers can inject `@AuthenticationPrincipal CurrentUser user` and use `user.userId()` without parsing JWTs again.

The filter looks up the account and user on each bearer request and uses no HTTP session. Banned/deleted users are rejected even with an unexpired access token. A supplied invalid bearer token is rejected even on otherwise public routes; omit an expired access token when calling login or refresh. See [user management APIs](user-management.md) for role checks, profile updates and soft deletion.

### Refresh tokens and logout

Refresh tokens are encrypted JWTs (JWE), using direct AES-256-GCM (`alg=dir`, `enc=A256GCM`) through the existing JJWT 0.12.6 library. The encrypted claims contain the user ID, `type=refresh`, issuer/timestamps, and a random 256-bit `jti`. JJWT generates a fresh encryption IV and authentication tag. Only the backend holds the decryption key. Tokens are distinct even when issued to the same user in the same second. Only SHA-256 of the complete encrypted token is persisted; plaintext claims and the token itself are never stored in the database. A fast hash is appropriate here because these are high-entropy random tokens, not human passwords.

Refresh authenticates/decrypts the JWE, validates the claims, and then locks the account row and matching session row. It verifies user/account status, ownership, revocation, and server-side expiration. In the same transaction it revokes the old session and inserts a replacement session with a new token hash. Concurrent refreshes of the same token allow only one success. A failed replacement rolls back the old revocation. Login, refresh, logout, and logout-all use the same account-first lock order.

Logout requires an authenticated identity matching the refresh token owner. Revocation is idempotent for a still-valid refresh JWT. Logout-all revokes every session for the authenticated user. Logout does not invalidate access JWTs before expiry. Banning/deleting the user blocks subsequent bearer requests, and role changes take effect on the next request through the database identity check.

Replay of a revoked refresh token is rejected. There is no token-family tracking or automatic revocation of all sibling sessions. Clients must serialize refresh requests and replace both stored tokens after a successful refresh.

Device names and user-agent strings are bounded display metadata. IP comes from `HttpServletRequest.getRemoteAddr()`. Untrusted `X-Forwarded-For` is ignored. Existing trusted-proxy behavior is not changed.

### Encrypted refresh-token response contract

All responses containing a refresh token use the encrypted representation:

- Register and login: `data.tokens.refreshToken`.
- Refresh: `data.refreshToken`.

The value is a five-part compact JWE (`header..iv.ciphertext.tag` for direct encryption). Only cryptographic metadata appears in the public header; the user ID and other claims are encrypted. This uses [JJWT's built-in JWE support](https://github.com/jwtk/jjwt/tree/0.12.6#json-web-encryption-jwe), with no new encryption library or custom encryption algorithm.

The frontend keeps this opaque string and sends it unchanged as `refreshToken` to refresh/logout. It must not decode it as a three-part signed JWT, decrypt it, or receive the encryption key. JSON field names and the standard API response envelope are unchanged. Access tokens remain signed HS256 JWTs.

Encryption does **not** hide the token string from browser DevTools/Network. The encrypted string is still a bearer credential and can be replayed if stolen. HTTPS remains required in deployment. Token responses continue to use `Cache-Control: no-store`.

Deployment requires the new `JWT_REFRESH_ENCRYPTION_KEY` environment variable. Generate a random 32-byte key, Base64-encode it, and retain it in the backend environment/secrets mechanism. Missing, malformed, or wrong-length keys stop startup with a generic error that does not expose the key. Old signed-but-unencrypted refresh tokens are deliberately rejected; users with those tokens must log in again. Existing access tokens retain their original lifetime. No schema change or deletion of existing sessions is needed for this change.
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
| `JWT_REFRESH_ENCRYPTION_KEY` | Required | Base64 encoding of exactly 32 random bytes; independent AES-256 key, backend only |
| `JWT_ACCESS_EXPIRATION` | `900000` | Access lifetime in milliseconds (15 minutes) |
| `JWT_REFRESH_EXPIRATION` | `604800000` | Refresh lifetime in milliseconds (7 days) |
| `JWT_ISSUER` | `signify-be` | `app.jwt.issuer`; issuer for access and refresh tokens, also required during validation |
| `JWT_AUDIENCE` | `signify-client` | `app.jwt.audience`; audience emitted on access tokens and checked when present |
| `RUN_APPLICATION_INTEGRATION_TESTS` | Unset | Set `true` only to opt into the original full application context test with configured services |

Existing property names remain `app.jwt.secret`, `app.jwt.expiration`, and `app.jwt.refresh-expiration`. The previous 24-hour defaults can be retained explicitly with `JWT_ACCESS_EXPIRATION=86400000` and `JWT_REFRESH_EXPIRATION=86400000`. All application instances must share both secrets. Changing `JWT_SECRET` invalidates existing access tokens; changing `JWT_REFRESH_ENCRYPTION_KEY` invalidates existing refresh tokens. Keep the encryption key independent from the signing secret.

For a temporary development secret in PowerShell, generate it without printing it:

```powershell
$bytes = New-Object byte[] 48
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
$refreshKeyBytes = New-Object byte[] 32
$rng.GetBytes($refreshKeyBytes)
$env:JWT_REFRESH_ENCRYPTION_KEY = [Convert]::ToBase64String($refreshKeyBytes)
$rng.Dispose()
$env:JWT_ACCESS_EXPIRATION = '900000'
$env:JWT_REFRESH_EXPIRATION = '604800000'
./gradlew.bat bootRun
```

For normal operation, supply stable secrets through the deployment environment/secrets mechanism. Keep the existing database, selected storage provider, SMTP and PayOS settings configured. Java toolchain remains **17**.

### Local .env configuration

Spring Boot does not discover a .env file by default. application.yaml explicitly imports
`optional:file:./.env[.properties]` using Spring Boot Config Data, without an extra dependency.
Run from the project root (also set the IDE working directory to the project root).
OS environment variables override the file, including an explicitly empty value.
The file is optional so deployments can provide environment variables without a .env file;
the refresh key itself remains required and startup fails if it is missing or invalid.

The configuration path is:
`JWT_REFRESH_ENCRYPTION_KEY → app.jwt.refresh-encryption-key → JwtProperties.refreshEncryptionKey → JwtService`.
The empty placeholder default `${JWT_REFRESH_ENCRYPTION_KEY:}` intentionally lets JwtService
report its existing clear error for both missing and empty configuration. It is not a usable default key.

The imported file uses Java properties syntax: one KEY=value per line, no shell-style quotes,
no export prefix, and no inline comments after values. Quotes become part of the value.
Backslashes have Java properties escape semantics. Keep .env outside src/main/resources.

Generate a refresh key once in PowerShell 7:

```powershell
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

On Windows PowerShell 5.1, use RandomNumberGenerator.Create() and GetBytes($bytes)
as in the example above if the Fill method is unavailable.
Put the generated result in the Git-ignored root .env, with no surrounding quotes:

```env
JWT_REFRESH_ENCRYPTION_KEY=<generated-base64-key>
```

Do not paste a normal password, reuse JWT_SECRET, generate a new key on every startup,
or commit the generated value. The decoded key must be exactly 32 bytes.
If supplying a process environment variable instead, use
`$env:JWT_REFRESH_ENCRYPTION_KEY = [Convert]::ToBase64String($bytes)`.
Clear a stale process override with `Remove-Item Env:JWT_REFRESH_ENCRYPTION_KEY`
before relying on the .env file, and restart the application/IDE after changing its environment.

Verify from the project root:

```powershell
# Check the file is ignored and not tracked (second command should have no output).
git check-ignore .env
git ls-files -- .env
.\gradlew.bat clean build
.\gradlew.bat bootRun
```

JwtConfigurationTest verifies Config Data import, environment precedence, missing/empty keys,
and access/refresh token round trips. JwtServiceTest covers malformed/wrong-length keys,
AES-256-GCM encryption, tampering, expiry and token-type separation.
A full application startup additionally requires the configured PostgreSQL and other services.
If JDK 17 on Windows fails before the build with `Unable to establish loopback connection`
and a UnixDomainSockets stack trace, use an existing project directory for the temporary socket:

```powershell
$socketDirectory = Join-Path (Get-Location) '.gradle'
New-Item -ItemType Directory -Force -Path $socketDirectory | Out-Null
$env:JAVA_OPTS = '-Djdk.net.unixdomain.tmpdir="' + $socketDirectory + '"'
.\gradlew.bat clean build
.\gradlew.bat bootRun
```

This process-local setting only affects the Gradle launcher; it does not change JWT validation
or the project's Java toolchain.


## Example requests

These are POSIX shell examples; Windows users can run them through Git Bash or use `curl.exe` with the equivalent PowerShell quoting. Replace token variables with the latest values returned by the API. Token responses contain credentials: keep them out of logs and source control.

```bash
BASE=http://localhost:8080

# Register; email and names may be omitted.
curl -X POST "$BASE/api/users/register" \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"a-long-demo-password","email":"alice@example.com","firstName":"Alice"}'

# Login from a device.
curl -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"a-long-demo-password","deviceName":"My laptop"}'

# Set ACCESS_TOKEN and REFRESH_TOKEN locally from data.tokens.
# Refresh: do not send an expired access token in the Authorization header.
curl -X POST "$BASE/api/auth/refresh" \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}"

# Replace ACCESS_TOKEN and REFRESH_TOKEN with the new data fields before continuing.
curl "$BASE/api/users/me" -H "Authorization: Bearer $ACCESS_TOKEN"

# Authenticated storage request uses the current, unchanged storage route.
curl -X POST "$BASE/api/storage/images" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -F 'file=@avatar.png;type=image/png'

curl -X POST "$BASE/api/auth/logout" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}"

curl -X POST "$BASE/api/auth/logout-all" \
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
- JWT secret rotation, token-family replay response, and per-token access revocation are outside this MVP. User banning is enforced on subsequent bearer requests through database state checks.

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
| `src/main/java/fptu/exe202/signify/signifybe/user/api/dto/RegisterRequest.java` | Registration input validation; redacts password-bearing debug output. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/api/dto/TokenResponse.java` | Bearer token pair and epoch-millisecond expiry DTO; redacted debug output. |
| `src/main/java/fptu/exe202/signify/signifybe/user/api/dto/UserResponse.java` | Basic profile DTO that never exposes persistence entities or hashes. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/application/AuthResult.java` | Application result combining a profile and token pair. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/application/AuthService.java` | Transactional login, refresh rotation, and revocation. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/application/JwtProperties.java` | Typed configuration under the existing app.jwt prefix; redacts signing and encryption keys. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/application/JwtService.java` | JJWT access signing, refresh JWE encryption/decryption, token-type separation, and SHA-256 refresh hashing. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/application/TokenPair.java` | Provider-independent application token result with redacted debug output. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/application/port/out/AccountRepository.java` | Application port for account lookup, locking, uniqueness checks, and insertion. |
| `src/main/java/fptu/exe202/signify/signifybe/user/application/port/out/UserRepository.java` | Application port for existing users-table persistence. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/application/port/out/UserSessionRepository.java` | Application port for hash lookup, session locking, persistence, and revocation. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/domain/Account.java` | JPA model of the existing account table with USER default and ACTIVE status. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/domain/CurrentUser.java` | Authenticated principal exposing the user ID and role. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/domain/Role.java` | USER/ADMIN values and consistent ROLE_ authority conversion. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/domain/SessionMetadata.java` | Bounded device, socket IP, and user-agent metadata. |
| `src/main/java/fptu/exe202/signify/signifybe/user/domain/User.java` | JPA model of the existing users table using BIGINT sequence IDs and soft-deletion checks. |
| `src/main/java/fptu/exe202/signify/signifybe/user/domain/UserProfile.java` | Safe application profile projection. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/domain/UserSession.java` | JPA model of existing refresh sessions, storing only hashes and revocation metadata. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/domain/exception/AuthException.java` | Safe auth errors extending the existing response-library BaseException. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/infrastructure/persistence/JpaAccountRepository.java` | Spring Data account queries and pessimistic account locks. |
| `src/main/java/fptu/exe202/signify/signifybe/auth/infrastructure/persistence/JpaAuthPersistence.java` | Adapter implementing account and session repository ports. |
| `src/main/java/fptu/exe202/signify/signifybe/user/infrastructure/persistence/JpaUserRepository.java` | Spring Data users-table access. |
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
