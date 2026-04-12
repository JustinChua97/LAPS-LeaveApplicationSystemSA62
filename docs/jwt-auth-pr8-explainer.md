# JWT Authentication — PR #8 Explainer

> **PR:** feat: Implement JWT authentication for stateless REST API (issue #6)
> **Merged commits:** `251803a` → `49209ad` (6 commits, 19 files changed)

---

## Why JWT Was Added

LAPS already had form-login (cookie + session) for the Thymeleaf UI. PR #8 adds a **parallel stateless REST API** so that mobile apps, scripts, or third-party tools can call the same business logic without needing a browser session. The two access paths (web UI and REST API) coexist in the same Spring Boot application using two ordered `SecurityFilterChain` beans.

---

## What Was Built — File by File

### 1. `JwtConfig.java` (new)
**Path:** `laps/src/main/java/com/iss/laps/config/JwtConfig.java`

Binds `app.jwt.*` properties from `application.properties` into a typed Spring bean using `@ConfigurationProperties(prefix = "app.jwt")`.

```java
@Configuration
@ConfigurationProperties(prefix = "app.jwt")
public class JwtConfig {
    private String secret;       // from JWT_SECRET env var
    private long expirationMs;   // 900000 ms = 15 minutes
    // explicit getters/setters (no Lombok — annotation processor was unreliable)
}
```

**Key decision:** Explicit getters/setters instead of Lombok `@Data`. Lombok's annotation processor wasn't running in the local Maven environment (pre-existing issue across the project). Since `@ConfigurationProperties` requires setters to bind, explicit methods were needed.

---

### 2. `JwtService.java` (new)
**Path:** `laps/src/main/java/com/iss/laps/security/JwtService.java`

All JWT cryptographic operations live here. Uses **JJWT 0.12.6** (added to `pom.xml`).

**Token generation:**
```java
public String generateToken(UserDetails userDetails) {
    return Jwts.builder()
        .subject(userDetails.getUsername())    // "sub" claim = username only
        .claim("roles", roles)                 // Spring authority strings
        .issuedAt(new Date(now))
        .expiration(new Date(now + jwtConfig.getExpirationMs()))
        .signWith(signingKey(), Jwts.SIG.HS256) // algorithm pinned
        .compact();
}
```

**Token validation:**
```java
public boolean validateToken(String token) {
    try {
        parseClaims(token);  // verifies signature + exp in one call
        return true;
    } catch (JwtException | IllegalArgumentException e) {
        return false;        // never throws — callers get a uniform 401
    }
}
```

**Key decisions:**
- Algorithm pinned to `HS256` — JJWT 0.12.x rejects `alg:none` by default (ASVS V3.5.2).
- `parseClaims()` calls `parseSignedClaims()` which automatically checks `exp` (ASVS V3.5.3).
- No PII (email, full name) in token payload — only username and role strings (ASVS V2.1.1).
- Signing key is derived from `jwtConfig.getSecret()` which reads from the `JWT_SECRET` environment variable — never hardcoded.

---

### 3. `JwtAuthenticationFilter.java` (new)
**Path:** `laps/src/main/java/com/iss/laps/security/JwtAuthenticationFilter.java`

A `OncePerRequestFilter` that runs on every `/api/**` request. It extracts, validates, and authenticates a Bearer token before Spring's own authentication filters.

**Request flow inside the filter:**
```
1. Is this the /api/v1/auth/token endpoint? → skip (pass through)
2. Is there an Authorization: Bearer <token> header? → No → 401 JSON
3. Is the token valid (signature + expiry)? → No → 401 JSON
4. Extract username from token
5. Load UserDetails from DB (CustomUserDetailsService) — NOT from token claims
6. Set Authentication in SecurityContext
7. Continue filter chain
```

**Key decisions:**
- **Roles loaded from DB, not from the token** (step 5). Even if an attacker forges a token with `"roles": ["ROLE_ADMIN"]`, the filter discards those claims and re-reads the user's actual role from the database. This enforces ASVS V4.1.1.
- **`@Component` + `FilterRegistrationBean` with `setEnabled(false)`**: The filter is a Spring bean (`@Component`) so it can use dependency injection, but registering it as a `@Component` would cause Spring Boot to auto-register it for ALL requests outside of Spring Security. The `FilterRegistrationBean` with `setEnabled(false)` in `SecurityConfig` prevents that double-registration. `OncePerRequestFilter` also has built-in protection against duplicate execution.
- **Generic 401 JSON on every failure** — no redirect to `/login`, no leaking which validation step failed (ASVS V13.1.3, V7.4.1).

---

### 4. `JwtAuthController.java` (new)
**Path:** `laps/src/main/java/com/iss/laps/controller/rest/JwtAuthController.java`

A single `POST /api/v1/auth/token` endpoint. No authentication required — it's the login endpoint.

```
Request:  POST /api/v1/auth/token
          Content-Type: application/json
          {"username": "emp.tan", "password": "..."}

Response: 200 OK
          {"accessToken": "<jwt>", "tokenType": "Bearer", "expiresIn": 900}
```

**Key decisions:**
- `@Valid` on `@RequestBody AuthRequest` — input validated before any authentication attempt (ASVS V5.1.3). The `AuthRequest` DTO constrains `username` to 50 chars and `password` to 128 chars, both `@NotBlank`.
- `@ExceptionHandler(AuthenticationException.class)` — catches bad credentials, disabled accounts, etc. and returns a **generic 401** without leaking `e.getMessage()` to the caller (ASVS V7.4.1). No username enumeration.
- `@ExceptionHandler(MethodArgumentNotValidException.class)` — returns 400 JSON if validation fails. Without this, the `GlobalExceptionHandler` (which returns a Thymeleaf view) would have been invoked instead, returning 200 HTML to a REST client.

---

### 5. `AuthRequest.java` / `AuthResponse.java` (new DTOs)

**AuthRequest** — the request body with Bean Validation constraints:
```java
@NotBlank @Size(max = 50)  String username;
@NotBlank @Size(max = 128) String password;
```

**AuthResponse** — the token response. Fixed fields prevent accidentally adding PII later:
```java
private final String accessToken;
private final String tokenType = "Bearer";  // hardcoded
private final int    expiresIn = 900;       // hardcoded (matches jwtConfig.expirationMs / 1000)
```

---

### 6. `SecurityConfig.java` (modified — split into two chains)

Before PR #8, there was one `SecurityFilterChain` for everything. After PR #8, there are two ordered chains:

| Bean | `@Order` | Matches | Session | CSRF | Auth mechanism |
|------|----------|---------|---------|------|----------------|
| `apiFilterChain` | 1 (first) | `/api/**` | STATELESS | disabled | JWT Bearer token |
| `webFilterChain` | 2 (second) | everything else | session-based | enabled | Form login |

The API chain is registered first (`@Order(1)`) so Spring Security evaluates it for any `/api/**` request before the web chain gets a chance.

```java
// API chain (stateless, JWT)
http.securityMatcher("/api/**")
    .csrf(AbstractHttpConfigurer::disable)
    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/v1/auth/token").permitAll()
        .anyRequest().authenticated())
    .exceptionHandling(ex -> ex.authenticationEntryPoint(/* 401 JSON */))
    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
```

The web chain is unchanged from before — CSRF stays on, form login stays on.

**One line removed from the web chain:**
```java
// removed: .requestMatchers("/api/**").hasAnyRole("EMPLOYEE", "MANAGER", "ADMIN")
```
The API paths are now fully managed by `apiFilterChain`. Having them in the web chain too would have conflicted.

---

### 7. `application.properties` (modified)

Two additions:
```properties
# Renamed from seed.user.password to prevent SEED_USER_PASSWORD OS env var from
# relaxed-binding over the test profile value (see property naming section below)
app.seed.password=${SEED_USER_PASSWORD}

# JWT secret — must be >= 256 bits — never hardcoded
app.jwt.secret=${JWT_SECRET}
app.jwt.expiration-ms=900000
```

---

### 8. `DataInitializer.java` (modified — property rename only)

```java
// Before:
@Value("${seed.user.password}")

// After:
@Value("${app.seed.password}")
```

See the property naming section below for why.

---

### 9. `deploy.yml` (modified)

Added `JWT_SECRET` to the `.env` file written to EC2 so the running application can read it:
```yaml
JWT_SECRET=${{ secrets.JWT_SECRET }}
```

Also fixed SSH directory permissions (`mkdir -p ~/.ssh && chmod 700 ~/.ssh`) which was causing SSH failures on some runners.

---

## How a Request Is Authenticated — Full Flow

```
Client → POST /api/v1/auth/token (no token required)
  → apiFilterChain matches /api/**
  → JwtAuthenticationFilter.doFilterInternal() sees TOKEN_ENDPOINT → passes through
  → JwtAuthController.issueToken()
      → @Valid validates AuthRequest (username, password)
      → authenticationManager.authenticate() → CustomUserDetailsService loads from DB
      → BCryptPasswordEncoder.matches() verifies password
      → JwtService.generateToken() signs HS256 JWT
  → Response: {"accessToken": "...", "tokenType": "Bearer", "expiresIn": 900}

Client → GET /api/v1/leaves/my
         Authorization: Bearer <token>
  → apiFilterChain matches /api/**
  → JwtAuthenticationFilter.doFilterInternal()
      → not token endpoint → reads Authorization header
      → JwtService.validateToken() → JJWT parseClaims() checks signature + exp
      → JwtService.extractUsername() → gets "emp.tan"
      → CustomUserDetailsService.loadUserByUsername("emp.tan") → loads from DB
      → Sets UsernamePasswordAuthenticationToken in SecurityContextHolder
  → Spring Security authorizeHttpRequests → .authenticated() → passes
  → LeaveRestController.getMyLeaves()
      → SecurityUtils.getCurrentEmployee() reads from SecurityContext
      → returns only that employee's leaves
```

---

## Property Naming — Why `app.seed.password` Instead of `seed.user.password`

Spring Boot's **relaxed binding** maps OS environment variable names to Spring property names:

```
SEED_USER_PASSWORD  →  seed.user.password
APP_SEED_PASSWORD   →  app.seed.password
```

The CI workflow sets `SEED_USER_PASSWORD` as an OS environment variable. OS environment variables sit at **position 5** in Spring Boot's property source order, which is higher than config files (position 3). So `SEED_USER_PASSWORD=<real-secret>` was overriding `seed.user.password=test-seed-password` in `application-test.properties`, causing authentication failures in tests.

By renaming the property to `app.seed.password`, the `SEED_USER_PASSWORD` OS env var no longer relaxed-binds to it. `application-test.properties` then provides the value without interference.

---

## CI Changes — Secrets-Free Tests

Before PR #8, CI ran:
```yaml
run: mvn -B verify -Dspring.profiles.active=ci
env:
  DB_URL: ${{ secrets.DB_URL }}
  SEED_USER_PASSWORD: ${{ secrets.SEED_USER_PASSWORD }}
  JWT_SECRET: ${{ secrets.JWT_SECRET }}
  # ... more secrets
```

After PR #8:
```yaml
run: mvn -B verify -Dspring.profiles.active=test
# no env: block — no secrets needed
```

The `test` Spring profile (`application-test.properties`) is fully self-contained:
- Uses H2 in-memory database (no PostgreSQL service needed)
- Hardcoded JWT secret: `test-secret-key-for-unit-tests-only-32bytes!!`
- Hardcoded seed password: `test-seed-password`

This means CI works on **fork PRs** where GitHub Actions secrets are unavailable, and test results are deterministic regardless of what secrets are configured in the repository.

---

## Tests Added

### `JwtServiceTest.java` — Unit tests (no Spring context)

Constructs `JwtService` directly with a fixed secret. Tests:

| Test | What it verifies |
|------|-----------------|
| `generateToken_containsSubjectAndRoles` | Token claims contain username and role |
| `validateToken_validToken_returnsTrue` | Happy path — valid token passes |
| `validateToken_expiredToken_returnsFalse` | Expired `exp` → rejected |
| `validateToken_tamperedSignature_returnsFalse` | Mutated signature byte → rejected |
| `validateToken_algNone_returnsFalse` | `alg:none` attack → rejected by JJWT |
| `validateToken_wrongSecret_returnsFalse` | Foreign-key token → rejected |
| `extractUsername_returnsSubject` | `sub` claim extracted correctly |

**Tampered token test note:** The HS256 signature is 32 bytes = 43 base64url characters. The 43rd character only encodes 4 significant bits (2 are zero-padding). Flipping it between `'a'` and `'b'` changes a bit that is zero-padded and doesn't affect the decoded bytes — JJWT accepts it as valid. The test therefore changes a character in the **middle** of the signature segment (full 6 bits encoded), which reliably corrupts the decoded HMAC.

### `JwtAuthControllerTest.java` — Integration tests (full Spring context, H2)

Uses `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")`. The `seedPassword` is injected via `@Value("${app.seed.password}")` from `application-test.properties` so the test always authenticates with the same password DataInitializer used to seed the database.

| Test | AC covered | What it verifies |
|------|-----------|-----------------|
| `validCredentials_return200WithJwtBody` | AC1 | Login returns `accessToken`, `tokenType`, `expiresIn` |
| `validToken_allowsGetMyLeaves` | AC2 | Valid Bearer token → 200 on protected endpoint |
| `expiredToken_returns401` | AC3 | Expired token → 401 with generic body |
| `tamperedToken_returns401` | AC3 | Mutated token → 401 |
| `noAuthHeader_returns401NotRedirect` | AC4 | No header → 401, not 302 redirect |
| `employeeToken_myLeaves_returnsOwnLeavesOnly` | AC5 | Employee sees their own leaves |
| `wrongPassword_returns401Generic` | AC7 | Bad password → generic 401 (no user enumeration) |
| `unknownUsername_returnsSame401AsWrongPassword` | AC7 | Unknown user → same 401 body as bad password |
| `tokenEndpoint_noCSRF_succeeds` | AC8 | Token endpoint works without CSRF token (stateless chain) |
| `algNoneToken_returns401` | SEC-NEG | `alg:none` attack → 401 |
| `wrongSecretToken_returns401` | SEC-NEG | Forged token signed with wrong key → 401 |
| `webChain_postWithoutCsrf_returns403` | SEC-NEG | Web chain still enforces CSRF |

---

## JJWT Dependency Choice

Three artifacts added to `pom.xml` (version 0.12.6):

```xml
<!-- JJWT 0.12.x — JWT generation and validation for stateless REST API auth (issue #6).
     Chosen because: actively maintained, enforces algorithm whitelisting by default,
     rejects alg:none out of the box. Do NOT substitute java-jwt (Auth0) without
     pinning the algorithm explicitly. -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>          <!-- compile-time API -->
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>         <!-- runtime implementation -->
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>      <!-- JSON serialization via Jackson -->
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

The API / impl split is intentional: production code only compiles against `jjwt-api`; the implementation details (parsing, signing) are swappable at the runtime layer.

---

## ASVS Controls Implemented

| Control | Where enforced |
|---------|---------------|
| V2.1.1 — No PII in tokens | `JwtService.generateToken()` — only `sub` + role strings |
| V3.5.2 — alg:none rejected | JJWT 0.12.x default; `Jwts.SIG.HS256` pinned |
| V3.5.3 — Token expiry validated | `parseSignedClaims()` checks `exp` automatically |
| V4.1.1 — Roles from DB, not token | `JwtAuthenticationFilter` reloads from `CustomUserDetailsService` |
| V5.1.3 — Server-side input validation | `@Valid` on `AuthRequest` DTO at controller boundary |
| V6.4.1 — Secret from env var | `JWT_SECRET` env var → `JwtConfig.secret` — never in source |
| V7.4.1 — No sensitive data in error responses | Generic 401 body everywhere; `e.getMessage()` never exposed |
| V13.1.3 — No redirect on API auth failure | `authenticationEntryPoint` writes 401 JSON, not 302 |
