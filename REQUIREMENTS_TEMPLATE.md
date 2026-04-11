<!-- REQUIREMENTS_TEMPLATE.md -->
<!-- Usage: Copy this file, rename to REQUIREMENTS-<issue-number>.md, and fill in all sections. -->
<!-- AI-PARSE: Each section between SECTION markers is independently extractable by ID.        -->

# Feature Requirements: [TITLE] (Issue #NNN)

<!-- SECTION: metadata -->
## Metadata

- **Issue:** #NNN
- **Author:** @github-handle
- **Date:** YYYY-MM-DD
- **Status:** DRAFT | REVIEWED | APPROVED
- **Sprint:** [sprint name or backlog]
- **Linked PR:** #NNN (fill in when raised)

<!-- END SECTION: metadata -->

---

<!-- SECTION: functional-requirements -->
## Functional Requirements

> Use one FR-N block per distinct behavioural requirement.
> Layer annotations (Controller / Service / Repository) tell the implementation agent exactly where logic lives.

### FR-1: [Short name]

- **Description:** One sentence.
- **Actor:** ROLE_EMPLOYEE | ROLE_MANAGER | ROLE_ADMIN | System
- **Trigger:** What initiates this? (user action, scheduled job, event)
- **Preconditions:** What must be true before this can occur?
- **Main flow:**
  1. [Step — layer: Controller] — e.g., `EmployeeController.applyLeave()` receives `@Valid @ModelAttribute`
  2. [Step — layer: Service] — e.g., `LeaveService.validateLeaveApplication()` applies business rules
  3. [Step — layer: Repository] — e.g., `leaveApplicationRepository.save(entity)`
- **Postconditions:** What is the guaranteed state after success?
- **Exceptions / Alternate flows:**
  - If [condition]: throw `LeaveApplicationException` → caught by `GlobalExceptionHandler`
  - If [condition]: redirect with flash error message

### FR-2: [Short name]

<!-- (copy FR-1 block for each additional requirement) -->

<!-- END SECTION: functional-requirements -->

---

<!-- SECTION: security-requirements -->
## Security Requirements

> Map every requirement to an OWASP ASVS 4.0 control ID.
> Implementation Point must name the exact file and method where the control is enforced.

| ID   | Requirement                                                                 | ASVS Control | Implementation Point                                      |
|------|-----------------------------------------------------------------------------|--------------|-----------------------------------------------------------|
| SR-1 | All endpoints require authentication                                        | V2.1.1       | `SecurityConfig.filterChain()` — `.anyRequest().authenticated()` |
| SR-2 | Access limited to [ROLE_X]; other roles receive 403                         | V4.1.1       | `@PreAuthorize("hasRole('X')")` or `SecurityConfig` path matcher |
| SR-3 | All user-supplied inputs validated server-side before processing            | V5.1.1       | `@Valid` + `BindingResult` at controller; guard in service layer |
| SR-4 | String inputs: max length [N], no HTML tags (`[^<>]*`)                      | V5.3.1       | Bean Validation `@Size`, `@Pattern` on model fields       |
| SR-5 | No sensitive data (passwords, tokens) in logs or error messages             | V7.4.1       | `GlobalExceptionHandler` — no `e.getMessage()` to UI     |
| SR-6 | Database access via parameterized JPA queries only — no string concat SQL   | V5.3.4       | Spring Data JPA / `@Query` with `:params`                |
| SR-7 | SMTP inputs (email addresses) validated before passing to EmailService      | V5.1.3       | `@Email` constraint on `Employee.email` (validated at entity creation) |
| SR-8 | API responses return minimum necessary data (no over-fetching)              | V8.2.1       | DTO projection or filtered response in `LeaveRestController` |
| SR-9 | CSRF token required on all state-changing POST/PUT/DELETE requests          | V4.2.2       | Spring Security CSRF enabled (never call `.csrf().disable()`) |
| SR-10| [Additional requirement specific to this feature]                           | V[X.Y.Z]     | [File and method]                                        |

**ASVS Chapters referenced:**

| Chapter | Area | Key controls in LAPS |
|---------|------|----------------------|
| V2 | Authentication | BCrypt via `BCryptPasswordEncoder`, Spring Security form login |
| V4 | Access Control | Role checks via `@EnableMethodSecurity`, `SecurityConfig` |
| V5 | Validation & Encoding | `@Valid`, Bean Validation, JPA parameterized queries, Thymeleaf auto-escaping |
| V6 | Cryptography | `BCryptPasswordEncoder` — no custom crypto |
| V7 | Error Handling | `GlobalExceptionHandler` — no stack traces to UI |
| V8 | Data Protection | Minimum data returned; no sensitive fields in logs |
| V13 | API | REST endpoints require auth; no anonymous access to `/api/v1` |

<!-- END SECTION: security-requirements -->

---

<!-- SECTION: trust-boundary-analysis -->
## Trust Boundary Analysis

> For each boundary this feature crosses, list the validation controls that apply.
> Sanitizing and continuing is NOT acceptable — invalid input must be rejected outright.

### Boundary: [e.g., Employee → Application]

- **Entry point:** `POST /employee/[path]`
- **Inputs crossing this boundary:** [list of field names]
- **Validation controls:**
  - Type checking: `@Valid` + `BindingResult` at controller layer
  - Business rule: `[ServiceClass.method()]` in service layer
  - Rejection behaviour: `LeaveApplicationException` caught by `GlobalExceptionHandler` → redirect with flash error
- **OWASP Top 10 risk:** A01 Broken Access Control | A03 Injection | A04 Insecure Design (choose applicable)

### Boundary: [e.g., Application → Database]

- **Entry point:** `[RepositoryInterface.method()]`
- **Inputs crossing this boundary:** [JPA entity fields / query parameters]
- **Validation controls:**
  - Parameterized query: Spring Data JPA (no string concatenation in SQL)
  - Entity-level: `@NotNull`, `@Size` on model fields
- **OWASP Top 10 risk:** A03 Injection

### Boundary: [e.g., Application → External Email]

- **Entry point:** `EmailService.send*()`
- **Inputs crossing this boundary:** `employee.email`, leave details for email body
- **Validation controls:**
  - Email format: `@Email` on `Employee.email` (validated at entity creation, not here)
  - Content: no user-controlled HTML in email body; plain text or escaped values only
- **OWASP Top 10 risk:** A03 Injection (SMTP header injection)

<!-- END SECTION: trust-boundary-analysis -->

---

<!-- SECTION: data-flow-diagram -->
## Data Flow Diagram (Text)

```
[Actor: ROLE_X]
  │
  ▼  HTTP [METHOD] /[path]   (CSRF token + session cookie)
[XxxController.method()]
  │  @Valid @ModelAttribute / @RequestParam
  ├─► [Validation boundary 1: Bean Validation + BindingResult]
  │     Reject if invalid → 400 / redirect with flash error
  ▼
[XxxService.method()]
  │  Business rule validation
  ├─► [Validation boundary 2: business logic guards]
  │     Throw LeaveApplicationException if rules violated
  ▼
[XxxRepository.save() / findBy*()]
  │  Spring Data JPA → parameterised SQL
  ▼
[PostgreSQL: lapsdb]
  │
  ▼  (optional — @Async)
[EmailService.send*()]
  │  SMTP → external mail server
  ▼
[Manager / Employee email inbox]
```

<!-- END SECTION: data-flow-diagram -->

---

<!-- SECTION: input-validation-rules -->
## Input Validation Rules

> **Anti-pattern to avoid:** Sanitizing input and continuing (e.g., stripping HTML and saving) is NOT acceptable.
> Invalid input must be **rejected outright** and the operation must not proceed.

| Field | Type | Required | Max Length | Format / Regex | Rejection Behaviour |
|-------|------|----------|------------|----------------|---------------------|
| [fieldName] | `String` | Yes | 500 | No HTML tags (`[^<>]*`) | 400 with field error |
| [fieldName] | `Long` | Yes | N/A | Positive integer | 400 with field error |
| [fieldName] | `LocalDate` | Yes | N/A | ISO-8601, not in past | `LeaveApplicationException` |
| [fieldName] | `Boolean` | No | N/A | true \| false | Default to false if absent |

<!-- END SECTION: input-validation-rules -->

---

<!-- SECTION: test-strategy -->
## Test Strategy

### Unit Tests — `laps/src/test/java/com/iss/laps/`

| Test Class | Test Method | Scenario | Expected Result |
|------------|-------------|----------|-----------------|
| `XxxServiceTest` | `test_[method]_[condition]_[outcome]()` | [description] | [expected exception or return value] |
| `XxxServiceTest` | `test_[method]_[condition]_[outcome]()` | [description] | [expected exception or return value] |

### Integration Tests — MockMvc (`@SpringBootTest`)

| Controller | HTTP | Path | Auth Role | Expected Status | Notes |
|------------|------|------|-----------|-----------------|-------|
| `XxxController` | POST | `/xxx/path` | `ROLE_EMPLOYEE` | 302 redirect | Happy path |
| `XxxController` | POST | `/xxx/path` | Unauthenticated | 302 → `/login` | Security check |
| `XxxController` | POST | `/xxx/path` | No CSRF token | 403 | CSRF check |

### Security Negative Tests

| Test | Input | Expected |
|------|-------|----------|
| HTML injection in [field] | `<script>alert(1)</script>` | HTML escaped in response; not executed |
| Cross-role access: [ROLE_X] accesses [ROLE_Y] endpoint | Valid credentials, wrong role | 403 Forbidden |
| Boundary: [fieldName] exceeds max length | 501-char string | 400 with validation error |
| SQL via [fieldName] | `'; DROP TABLE employees; --` | JPA treats as literal string; query fails safely |

<!-- END SECTION: test-strategy -->
