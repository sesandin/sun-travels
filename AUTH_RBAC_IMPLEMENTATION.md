# Authentication and RBAC implementation

Implemented on 15 October 2026 for the existing Sun Travels Spring Boot and Angular application. Authentication is database backed, sessions are server side, CSRF remains enabled, and backend authorization is authoritative.

## Outcome

- Spring Security authenticates the existing MySQL `user` table through a repository-backed `UserDetailsService` and `BCryptPasswordEncoder`.
- Only `ACTIVE` users can authenticate. Unknown users, wrong passwords and inactive users receive the same 401 response.
- Successful authentication rotates an existing session ID and stores the security context in the HTTP session. Logout invalidates it.
- The authenticated principal erases its BCrypt hash after verification, before the security context is retained in the session.
- `JSESSIONID` is HttpOnly and SameSite=Lax. Set `SESSION_COOKIE_SECURE=true` only when the backend is served over HTTPS; local HTTP development uses false.
- State-changing requests require the `X-XSRF-TOKEN` header matching the readable `XSRF-TOKEN` cookie. CSRF is not disabled for login, logout or business APIs.
- Angular restores the safe profile through `/api/auth/me`, keeps it only in memory, has no role switcher, and stores neither passwords nor session identifiers in web storage.
- The Angular development server proxies same-origin `/api` requests to Spring Boot. Production hosting must likewise route `/api` to the backend on the same origin, or explicitly review the credentialed CORS deployment.
- No schema, reservation, closure, pricing or contract-lifecycle behavior was changed. No SQL dump was imported and no commit or push was made.

The older accessible SQL export does not include the new `user` table. Startup with `ddl-auto=validate` therefore depends on the manually created table matching the specification in the task. The application does not create or alter it.

## Authentication API

| Method | Endpoint | Access | Purpose |
| --- | --- | --- | --- |
| GET | `/api/auth/csrf` | Public | Creates/refreshes the CSRF cookie and returns its header name/token for non-Angular clients. |
| POST | `/api/auth/login` | Public plus valid CSRF | Authenticates `{ "username": "...", "password": "..." }` and creates/rotates the session. |
| GET | `/api/auth/me` | Authenticated | Returns the current safe user profile. |
| POST | `/api/auth/logout` | Authenticated plus valid CSRF | Invalidates the session; returns 204. |

Successful login and `/me` return only:

```json
{
  "userId": 1,
  "username": "admin01",
  "fullName": "Sun Travels Administrator",
  "role": "ADMIN"
}
```

`passwordHash` is never serialized. Login request string rendering masks the password so MVC debug logs cannot print it. Unauthenticated API access returns JSON 401; authenticated access without authority returns JSON 403.

## Permission map

| API | ADMIN | AGENT |
| --- | --- | --- |
| `/api/auth/csrf`, login, me, logout | Yes | Yes |
| `GET /api/search/limits` | Yes | Yes |
| `GET /api/search/filters` | Yes | Yes |
| `POST /api/search/quotations` | Yes | Yes |
| `/api/hotels/**` all reads/writes | Yes | No |
| `/api/room-types/**` all reads/writes | Yes | No |
| `/api/contracts/**` all reads/writes/lifecycle/markup | Yes | No |
| `/api/offerings`, contract offerings, offering rate/status and seasonal-rate APIs | Yes | No |
| Any unlisted `/api/**` endpoint | Denied until explicitly assigned | Denied until explicitly assigned |

`GET /api/search/filters` is a least-privilege replacement for giving AGENT access to administrative Hotel DTOs. It returns ACTIVE hotel `{hotelId,name,city}` options and generic room-type `{roomTypeId,name}` options only. The search engine still performs final eligibility checks.

Future Customer and Reservation APIs should permit ADMIN and AGENT with method-specific rules. Future Hotel Closure administration should be ADMIN-only. Each future endpoint remains denied until added deliberately. Existing HOTEL-first reservation/contract coordination rules remain unchanged.

## Initial account bootstrap

Bootstrap is disabled by default, explicitly enabled, transactional and idempotent. It creates only missing fixed accounts:

- `admin01`, full name `Sun Travels Administrator`, role `ADMIN`, status `ACTIVE`
- `agent01`, full name `Sun Travels Agent`, role `AGENT`, status `ACTIVE`

Existing matching usernames are preserved without changing their names, roles, statuses or hashes. Each missing-account password must contain at least 8 characters, at most 72 UTF-8 bytes, one uppercase English letter (`A-Z`), one lowercase English letter (`a-z`), one digit (`0-9`) and one printable ASCII special character such as `@`, `#`, `$`, `!`, `%` or `&`. Whitespace does not satisfy the special-character requirement. Values are BCrypt encoded before insertion. If a required password is missing, blank or noncompliant, initialization fails instead of generating a password. These complexity rules apply only to initial bootstrap; ordinary login delegates password verification to Spring Security against the stored BCrypt hash. A transaction failure rolls back the bootstrap run.

Required environment variables:

| Name | Purpose |
| --- | --- |
| `DB_USERNAME` | Existing MySQL login used by Spring Boot. |
| `DB_PASSWORD` | Existing MySQL login password. |
| `AUTH_BOOTSTRAP_ENABLED` | Set to `true` for the one initialization run; normally `false` or absent. |
| `AUTH_BOOTSTRAP_ADMIN_PASSWORD` | Your chosen password for `admin01`. |
| `AUTH_BOOTSTRAP_AGENT_PASSWORD` | Your chosen password for `agent01`. |
| `SESSION_COOKIE_SECURE` | `false` for local HTTP; `true` only behind production HTTPS. |

No password values or password hashes are included in source code or this report.

## Windows IntelliJ IDEA setup

1. Confirm the manually created `user` table has exactly the stated columns, constraints, InnoDB engine and compatible collation. Do not let Hibernate create it.
2. In IntelliJ IDEA open **Run > Edit Configurations**.
3. Select the Spring Boot configuration for `com.suntravels.SuntravelsBackendApplication`, or create one with **Add new configuration > Spring Boot** and choose that main class.
4. In **Environment variables**, open the editor and add `DB_USERNAME` and `DB_PASSWORD` for the existing database account.
5. Add `AUTH_BOOTSTRAP_ENABLED=true`.
6. Add `AUTH_BOOTSTRAP_ADMIN_PASSWORD` and enter your chosen `admin01` password as its value. Add `AUTH_BOOTSTRAP_AGENT_PASSWORD` and enter your chosen `agent01` password as its value. Do not put either value in Program arguments, VM options, source files or screenshots.
7. Add `SESSION_COOKIE_SECURE=false` for local `http://localhost` development.
8. Run the backend once. It validates the schema, checks each username, creates only missing accounts, and logs account names/roles without passwords or hashes. If startup fails, correct the table or environment configuration; do not enable schema generation.
9. In MySQL Workbench verify creation without displaying hashes:

```sql
SELECT user_id, username, full_name, role, status, created_at,
       password_hash LIKE '$2%' AS bcrypt_encoded
FROM user
WHERE username IN ('admin01', 'agent01');
```

   Expect two `ACTIVE` rows with their assigned roles and `bcrypt_encoded = 1`.
10. Stop the backend. In the same IntelliJ run configuration set `AUTH_BOOTSTRAP_ENABLED=false` or remove it, and remove both bootstrap password variables. Keep `DB_USERNAME`, `DB_PASSWORD` and the local cookie setting. Restarting never deletes the users.
11. Start the backend normally from IntelliJ.
12. Open an IntelliJ terminal in `frontend` and run `npm.cmd start`. The checked-in `proxy.conf.json` routes `/api` to `http://localhost:8080`.
13. Browse to `http://localhost:4200/login`. Sign in as `admin01` with the ADMIN password you chose; verify the administration links and a protected write. Sign out, then sign in as `agent01`; verify Search & Quotations is available and administration links/routes are blocked.

## Files

Backend additions:

- `config/SecurityConfig.java`, `config/StaffAccountBootstrap.java`
- `controller/AuthController.java`
- `dto/LoginRequest.java`, `dto/AuthenticatedUserResponse.java`, `dto/QuotationSearchFilters.java`
- `entity/User.java`, `entity/UserRole.java`, `entity/UserStatus.java`
- `repository/UserRepository.java`
- `security/DatabaseUserPrincipal.java`, `security/DatabaseUserDetailsService.java`, `security/JsonAuthenticationEntryPoint.java`, `security/JsonAccessDeniedHandler.java`
- `security/AuthenticationSecurityTest.java`, `config/StaffAccountBootstrapTest.java`

Backend modifications: `pom.xml`, `application.properties`, `WebConfig.java`, `SecurityConfig`-related search controller/service tests and the search service/controller for safe filter options.

Frontend additions:

- `proxy.conf.json`
- `models/auth.model.ts`
- `services/auth-state.ts`, `auth.service.ts`, `auth.interceptor.ts` and auth service tests
- `auth.guards.ts` and guard tests
- `features/auth/login.ts`, `login.html`, `login.scss` and login tests

Frontend modifications: application config/routes/shell/styles/tests; all API services now use same-origin `/api`; search consumes safe filter options; existing feature specs use the same-origin URLs.

## Verification

- Focused Spring Security/bootstrap tests: 9 passed.
- Final database-independent backend package run: 218 tests passed, zero failures/errors, compilation and executable Spring Boot JAR repackaging passed. This includes all existing service/controller tests and the new auth, RBAC, CSRF, bootstrap and safe-filter tests.
- Full backend suite: 248 discovered; 218 passed; zero assertion failures; one application-context error; 29 opt-in MySQL integration tests skipped. The context error is environmental: `DB_USERNAME`/`DB_PASSWORD` are unset, so Hibernate cannot obtain MySQL metadata. Consequently live `user` mapping validation and database login/bootstrap are not verified here.
- Angular: 108 tests passed across 15 files, including login, session restoration, logout, CSRF header integration, guards, ADMIN/AGENT navigation and 401/403 messages.
- Angular production build passed.
- Git whitespace checks passed for tracked and added files.

The MySQL full-suite count above reflects the final test inventory; existing opt-in contract/offering/search integration tests remain disabled unless their disposable-database flags and credentials are deliberately supplied.

## Manual security checks still required

Use a controlled database and both real accounts to verify `ddl-auto=validate`, BCrypt login, inactive-user rejection, session restoration after refresh, session ID rotation, CSRF rejection with missing/altered headers, logout invalidation and idle/expired-session UX. Verify ADMIN can complete every existing management operation and AGENT receives 403 from direct management API calls even if frontend guards are bypassed. Inspect browser cookies: local `JSESSIONID` must be HttpOnly and SameSite=Lax; production HTTPS must additionally use Secure. Confirm no password appears in application logs at the configured production log level.

Public registration, password recovery, staff management, Customer/Reservation CRUD and Hotel Closure workflows remain deferred. HTTPS termination and production same-origin routing are deployment responsibilities; local HTTP does not provide transport encryption.
