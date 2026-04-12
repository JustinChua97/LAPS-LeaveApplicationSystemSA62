# LAPS — Leave Application Processing System

## Overview

**LAPS** is a full-stack enterprise web application built as a coursework project for ISS NUS (SA62). It handles leave management for employees, managers, and administrators with role-based access control, automated workflows, email notifications, and a stateless REST API secured with JWT.

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│             NETWORK EDGE (EC2)                                   │
│  nginx — TLS termination (port 443), HTTP→HTTPS redirect (80)   │
│  Self-signed cert, TLS 1.2/1.3, HSTS, X-Frame-Options           │
└──────────────────────────┬───────────────────────────────────────┘
                           │ proxy_pass http://127.0.0.1:8080
┌──────────────────────────▼───────────────────────────────────────┐
│             PRESENTATION LAYER                                   │
│  Thymeleaf Templates (HTML/Bootstrap 5) + REST API (/api/v1)    │
└──────────────────────────┬───────────────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────────────┐
│             SECURITY LAYER                                       │
│  Spring Security 6 — two ordered SecurityFilterChain beans:      │
│    @Order(1) apiFilterChain  — /api/**  — JWT Bearer, STATELESS  │
│    @Order(2) webFilterChain  — /**      — Form login, sessions   │
│  JwtAuthenticationFilter (OncePerRequestFilter)                  │
└──────────────────────────┬───────────────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────────────┐
│             CONTROLLER LAYER                                     │
│  AuthController | EmployeeController | ManagerController         │
│  AdminController | MovementController                            │
│  REST: JwtAuthController | LeaveRestController                   │
└──────────────────────────┬───────────────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────────────┐
│             SERVICE LAYER                                        │
│  LeaveService | EmployeeService | AdminService                   │
│  EmailService | CustomUserDetailsService | JwtService            │
└──────────────────────────┬───────────────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────────────┐
│             REPOSITORY LAYER (Spring Data JPA)                   │
│  LeaveApplicationRepo | EmployeeRepo | EntitlementRepo           │
│  PublicHolidayRepo | LeaveTypeRepo | CompensationClaimRepo       │
└──────────────────────────┬───────────────────────────────────────┘
                           │
┌──────────────────────────▼───────────────────────────────────────┐
│             PostgreSQL 14+  (Hibernate ORM)                      │
└──────────────────────────────────────────────────────────────────┘
```

**Pattern:** MVC + Service Layer
**Design principles:** Separation of concerns, RBAC, `@Transactional` consistency, `@Async` email

---

## Tech Stack

| Category | Technology | Purpose |
| --- | --- | --- |
| Framework | Spring Boot 3.2.3 | App framework, DI |
| Security | Spring Security 6 | Auth, RBAC, CSRF |
| JWT | JJWT 0.12.6 | Stateless REST API auth (issue #6) |
| ORM | Spring Data JPA + Hibernate | DB abstraction |
| View | Thymeleaf 3 | Server-side rendering |
| Database | PostgreSQL 14+ | Persistence |
| Build | Maven 3.9+ | Build and dependencies |
| Runtime | Java 17 | Language |
| UI | Bootstrap 5.3.2 (WebJar) | Responsive CSS |
| CSV | OpenCSV 5.9 | Report export |
| Email | Spring Mail (SMTP) | Async notifications |
| Testing | JUnit 5 + MockMvc + H2 | Unit and integration tests |
| SAST | Semgrep | Security scanning in CI |
| Reverse proxy | nginx | TLS termination, HTTP→HTTPS redirect (issue #9) |

---

## Project Structure

```
ca_laps_team4/
├── CLAUDE.md                       Project rules and required workflow for Claude Code
├── AGENTS.md                       Agent guidance
├── scripts/
│   └── setup-nginx-https.sh        EC2 provisioning: nginx + self-signed TLS cert (issue #9)
├── docs/
│   ├── CODEBASE.md                 This file
│   ├── jwt-auth-pr8-explainer.md   JWT implementation deep-dive
│   └── https-nginx-pr9-explainer.md  HTTPS nginx setup deep-dive
└── laps/
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/com/iss/laps/
        │   │   ├── LapsApplication.java
        │   │   ├── config/
        │   │   │   ├── SecurityConfig.java       Two-chain security: web + API
        │   │   │   ├── JwtConfig.java            @ConfigurationProperties for JWT
        │   │   │   ├── AsyncConfig.java          @Async thread pool
        │   │   │   └── DataInitializer.java      Seed accounts on startup
        │   │   ├── controller/
        │   │   │   ├── AuthController.java       /login, /logout
        │   │   │   ├── EmployeeController.java   /employee/**
        │   │   │   ├── ManagerController.java    /manager/**
        │   │   │   ├── AdminController.java      /admin/**
        │   │   │   ├── MovementController.java   /movement/**
        │   │   │   ├── GlobalExceptionHandler.java
        │   │   │   └── rest/
        │   │   │       ├── JwtAuthController.java    POST /api/v1/auth/token
        │   │   │       └── LeaveRestController.java  GET /api/v1/leaves/**, /movement, /leave-types
        │   │   ├── dto/
        │   │   │   ├── AuthRequest.java          JWT login request (@Valid, @Size)
        │   │   │   └── AuthResponse.java         JWT token response
        │   │   ├── model/                        JPA entities
        │   │   │   ├── Employee.java             id, username, password(BCrypt), role, manager_id
        │   │   │   ├── LeaveApplication.java     leave record + status state machine
        │   │   │   ├── LeaveEntitlement.java     per-employee, per-type, per-year balance
        │   │   │   ├── LeaveType.java            Annual / Medical / Compensation
        │   │   │   ├── CompensationClaim.java    overtime claim → 0.5 day credit
        │   │   │   ├── PublicHoliday.java        SG holidays (pre-seeded)
        │   │   │   ├── Role.java                 ROLE_EMPLOYEE / ROLE_MANAGER / ROLE_ADMIN
        │   │   │   ├── LeaveStatus.java          APPLIED/APPROVED/REJECTED/UPDATED/DELETED/CANCELLED
        │   │   │   └── Designation.java          ADMINISTRATIVE / PROFESSIONAL / SENIOR_PROFESSIONAL
        │   │   ├── repository/                   Spring Data JPA interfaces (parameterized queries only)
        │   │   ├── security/
        │   │   │   ├── JwtService.java           Token generation, validation (JJWT 0.12.6)
        │   │   │   └── JwtAuthenticationFilter.java  OncePerRequestFilter for /api/**
        │   │   ├── service/
        │   │   │   ├── LeaveService.java         Core business logic + @Transactional
        │   │   │   ├── EmployeeService.java
        │   │   │   ├── AdminService.java
        │   │   │   ├── EmailService.java         @Async notifications
        │   │   │   ├── CustomUserDetailsService.java
        │   │   │   └── (no JwtService here — lives in security/)
        │   │   ├── util/
        │   │   │   ├── LeaveCalculator.java      Duration rules by leave type
        │   │   │   └── SecurityUtils.java        getCurrentEmployee() from SecurityContext
        │   │   └── exception/
        │   │       ├── LeaveApplicationException.java
        │   │       └── ResourceNotFoundException.java
        │   └── resources/
        │       ├── application.properties        Production config (env var placeholders)
        │       ├── application-local.properties  Local dev overrides (gitignored)
        │       ├── sql/data.sql                  Seed data
        │       ├── static/css/laps.css
        │       ├── static/js/laps.js
        │       └── templates/                    Thymeleaf HTML
        │           ├── common/                   layout, navbar, fragments
        │           ├── auth/                     login
        │           ├── employee/                 dashboard, leaves, apply, view
        │           ├── manager/                  dashboard, approvals, team
        │           ├── admin/                    employees, leave-types, holidays, reports
        │           └── error/                    access-denied, generic error
        └── test/java/com/iss/laps/
            ├── LapsApplicationTests.java
            ├── LeaveServiceTest.java
            ├── LeaveCalculatorTest.java
            ├── JwtServiceTest.java               Unit tests — no Spring context
            └── JwtAuthControllerTest.java        Integration tests — MockMvc + H2
```

---

## Data Model

### Core Entities

**Employee**
- Fields: `id`, `username`, `password` (BCrypt), `name`, `email`, `role`, `designation`, `active`, `manager_id`
- Self-referencing FK for manager hierarchy
- Roles: `ROLE_EMPLOYEE`, `ROLE_MANAGER`, `ROLE_ADMIN`
- Designations: `ADMINISTRATIVE` (14d annual), `PROFESSIONAL` (18d), `SENIOR_PROFESSIONAL` (21d)

**LeaveApplication**
- Fields: `id`, `employee_id`, `leave_type_id`, `start_date`, `end_date`, `duration`, `reason`, `status`, `manager_comment`, `half_day`, `half_day_type`
- Status state machine:

```
APPLIED ──approve──> APPROVED ──cancel──> CANCELLED
   │  └──reject──>  REJECTED
   │
   └──update──> UPDATED ──approve/reject──> same

APPLIED/UPDATED/REJECTED ──delete──> DELETED
```

**LeaveEntitlement** — Per employee, per leave type, per year (`total_days`, `used_days`)

**LeaveType** — `Annual`, `Medical`, `Compensation` with `max_days_per_year`, `half_day_allowed`

**PublicHoliday** — Singapore 2025 holidays pre-seeded

**CompensationClaim** — Overtime claim (≥4 hrs = 0.5 day). Status: `PENDING → APPROVED/REJECTED`

---

## Leave Duration Rules (`LeaveCalculator`)

| Type | Rule |
| --- | --- |
| Annual ≤14 days | Excludes weekends + public holidays |
| Annual >14 days | All calendar days counted |
| Medical | Excludes weekends only |
| Compensation | 4 hrs overtime = 0.5 day credit |

---

## Security Architecture

### Authentication — Two Parallel Chains

Spring Security is configured with two ordered `SecurityFilterChain` beans in `SecurityConfig.java`:

| Bean | `@Order` | Matches | Session policy | CSRF | Auth mechanism |
| --- | --- | --- | --- | --- | --- |
| `apiFilterChain` | 1 (first) | `/api/**` | STATELESS | disabled (no cookies) | JWT Bearer token |
| `webFilterChain` | 2 (second) | `/**` | Session-based | enabled | Form login |

### JWT Flow (REST API)

```
POST /api/v1/auth/token  {"username": "...", "password": "..."}
  → JwtAuthController → AuthenticationManager → BCrypt verify
  → JwtService.generateToken() → HS256-signed JWT (15 min TTL)
  ← {"accessToken": "...", "tokenType": "Bearer", "expiresIn": 900}

GET /api/v1/leaves/my  Authorization: Bearer <token>
  → JwtAuthenticationFilter → JwtService.validateToken()
  → CustomUserDetailsService.loadUserByUsername()  ← roles from DB, not token
  → SecurityContextHolder set → LeaveRestController
```

### HTTPS / TLS (nginx)

All production traffic is TLS-terminated at nginx before reaching Spring Boot:

```
Internet → TCP:443 → nginx (TLS 1.2/1.3, self-signed cert) → TCP:8080 → Spring Boot
Internet → TCP:80  → nginx → 301 https://...
TCP:8080 from internet → EC2 Security Group blocks (manual step)
```

Key properties in `application.properties`:
- `server.forward-headers-strategy=NATIVE` — trusts `X-Forwarded-*` headers from nginx
- `server.servlet.session.cookie.secure=true` — `JSESSIONID` only sent over HTTPS

---

## REST API

**Base URL:** `/api/v1`

| Method | Endpoint | Auth | Description |
| --- | --- | --- | --- |
| POST | `/auth/token` | Public | Issue JWT access token (issue #6) |
| GET | `/leaves/my` | Bearer JWT or session | Current user's leave history |
| GET | `/leaves/{id}` | Bearer JWT or session | Specific leave application |
| GET | `/leaves/entitlements` | Bearer JWT or session | Current user's leave balance |
| GET | `/movement?year=&month=` | Bearer JWT or session | Movement register |
| GET | `/leave-types` | Bearer JWT or session | All active leave types |

**Web routes** (`/employee/**`, `/manager/**`, `/admin/**`) use form login + session cookies only.

---

## Business Logic Highlights

**`LeaveService.java`** (core, all mutations `@Transactional`):
- `applyLeave()` — Validate, calculate duration, save, notify manager
- `approveLeave()` — Verify `manager_id` matches, deduct entitlement, send notification
- `rejectLeave()` — Mandatory comment required, verify `manager_id`
- `cancelLeave()` — Restore entitlement
- `validateLeaveApplication()` — Checks: valid dates, no overlaps, sufficient balance, medical cert requirement for >2 days

**`EmailService.java`** — All notifications are `@Async`; failures are logged but non-fatal. Email links are built from `app.base-url` (defaults to `${EC2_HOST:localhost}`).

**`JwtService.java`** — Token generation and validation. Roles are re-loaded from DB on each authenticated API request — token claims are not trusted for authorization (ASVS V4.1.1).

---

## CI/CD Pipeline

### CI (`.github/workflows/ci.yml`) — Triggered on PRs to `main`

1. Compile with Java 17
2. Run tests against H2 in-memory DB (`spring.profiles.active=test`) — no secrets required
3. Upload JUnit reports + JAR artifact (retained 5 days)
4. Semgrep SAST scan (parallel job)

### CD (`.github/workflows/deploy.yml`) — Triggered after CI passes on `main`

1. Download tested JAR artifact from CI run
2. SSH to EC2 (Amazon Linux 2023)
3. Write env vars to `/opt/laps/.env` (chmod 600)
4. Idempotent PostgreSQL 15 setup (`lapsdb` DB + app user)
5. Create/update `laps.service` systemd unit
6. **Configure nginx HTTPS** — upload and run `scripts/setup-nginx-https.sh` with `EC2_HOST`
7. Deploy JAR, restart `laps` service
8. Health check: `curl http://localhost:8080/login` (internal, loopback)

---

## Configuration Profiles

| Profile | Use case | DB | Secrets needed |
| --- | --- | --- | --- |
| `local` | Local development | Local PostgreSQL | `application-local.properties` (gitignored) |
| `test` | CI / unit tests | H2 in-memory | None — hardcoded test values |
| default | Production (EC2) | EC2 PostgreSQL | GitHub Actions secrets → `/opt/laps/.env` |

---

## Security Controls Summary

| Control | Implementation |
| --- | --- |
| Password hashing | BCrypt (strength 10) via `BCryptPasswordEncoder` |
| CSRF protection | Enabled on web chain; disabled on API chain (stateless) |
| RBAC | `@EnableMethodSecurity` + `@PreAuthorize`; path matchers in `SecurityConfig` |
| JWT auth | JJWT 0.12.6, HS256, 15-min TTL, `alg:none` rejected by default |
| TLS encryption | nginx, TLS 1.2/1.3, Mozilla Intermediate cipher suite |
| HSTS | `Strict-Transport-Security: max-age=31536000; includeSubDomains` |
| Session cookie | `Secure` + `HttpOnly` flags |
| Clickjacking | `X-Frame-Options: SAMEORIGIN` |
| MIME sniffing | `X-Content-Type-Options: nosniff` |
| Secrets management | Env vars only — never hardcoded; GitHub Secrets → EC2 `.env` |
| SAST | Semgrep on every CI run |
| SQL injection | Spring Data JPA parameterized queries only |
| XSS | Thymeleaf `th:text=` auto-escaping; `th:utext=` prohibited for user input |
| Error disclosure | `GlobalExceptionHandler` — no stack traces or `e.getMessage()` to UI |

---

## Test Seed Accounts

Seeded by `DataInitializer.java` (idempotent at startup). Password set via `SEED_USER_PASSWORD` env var (or `test-seed-password` in test profile).

| Username | Role | Manager |
| --- | --- | --- |
| `admin` | Admin | — |
| `mgr.chen` | Manager | — |
| `mgr.lim` | Manager | — |
| `emp.tan` | Employee | mgr.chen |
| `emp.kumar` | Employee | mgr.chen |
| `emp.ali` | Employee | mgr.lim |
| `emp.sarah` | Employee | mgr.lim |

---

## Further Reading

- [jwt-auth-pr8-explainer.md](jwt-auth-pr8-explainer.md) — JWT implementation: filter chain, token flow, test strategy
- [https-nginx-pr9-explainer.md](https-nginx-pr9-explainer.md) — HTTPS: nginx config, self-signed cert rationale, verification commands
