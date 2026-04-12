# LAPS — Claude Code Project Rules

## Required Workflow — Follow Before Writing Any Code

Every coding task must follow these steps in order:

1. **Reference a GitHub issue.** Run `gh issue view <N>` and read all fields before proceeding.
   - If no issue exists, ask the user to create one using the feature request template.
2. **Run `/plan <N>`** to produce an implementation plan from the issue.
   - The plan must cover: scope, affected files, security mapping, sequenced tasks, edge cases, human review checkpoints.
3. **Wait for explicit user approval** of the plan before writing any code.
4. **Check out a feature branch** before writing any code:

   ```bash
   git checkout -b feat/issue-<N>-<short-description>
   ```

   Never implement directly on `main`.
5. **Implement** following the approved plan. Run `/security-review` on staged changes before each commit.
6. **Commit message must reference the issue:** `feat: description (closes #N)` or `fix: description (fixes #N)`.
7. **Open a Pull Request** after all commits are pushed:

   ```bash
   gh pr create --base main --title "feat: <description> (closes #N)"
   ```

   The PR body must summarize what changed, reference the issue, and list manual verification steps.

Never implement features beyond the issue scope. If a task is ambiguous, ask — do not assume.

---

## Security Rules

These apply to every change, regardless of how small.

### Input Validation
- All user inputs must be validated **server-side**. Client-side validation is never sufficient on its own.
- **Reject invalid input outright.** Sanitizing and continuing (e.g., stripping HTML and saving) is NOT acceptable.
- Use `@Valid` + `BindingResult` at the controller boundary.
- Apply a second validation pass in the service layer (`LeaveService.validateLeaveApplication()`-style guards).
- Bean Validation constraints (`@NotNull`, `@Size`, `@Pattern`, `@Email`) belong on model fields.

### Database
- All database access must use **Spring Data JPA parameterized queries**. No string-concatenated SQL.
- Never use `@Query` with manually concatenated user input.

### Access Control
- Every new endpoint requires a role check — either via `SecurityConfig` path matchers or `@PreAuthorize`.
- CSRF protection must remain enabled. Never call `.csrf().disable()`.
- Managers may only act on their direct subordinates — verify `manager_id` in service layer before any approval/rejection.

### Secrets and Logging
- No passwords, tokens, or secrets in log statements.
- `GlobalExceptionHandler` must catch exceptions — never expose stack traces or `e.getMessage()` to the UI.
- Credentials via environment variables only; never hardcoded.

### Frontend (Thymeleaf)
- Use `th:text=` (auto-escaped) for user-controlled content. Never use `th:utext=` for user input.
- Do not echo raw user input back in error messages without escaping.

### Dependencies
- New `pom.xml` dependencies require a brief justification comment and a check that the library is actively maintained.

---

## Architecture Constraints

- **Controller layer:** Handles HTTP, calls `@Valid`, delegates to service. No business logic here.
- **Service layer:** All business rules and validation live here. Marked `@Transactional` for mutations.
- **Repository layer:** Spring Data JPA only. No service logic here.
- **Email:** All email sends are `@Async` in `EmailService`. Failures are logged but must not abort the main transaction.
- **Roles:** `ROLE_ADMIN > ROLE_MANAGER > ROLE_EMPLOYEE`. Hierarchy is enforced in `SecurityConfig` and `@PreAuthorize`.

---

## Planning Checklist (required output of `/plan`)

- **Scope** — what changes; what explicitly does NOT change
- **Affected files** — every file to be created or modified, with the method-level change
- **Security mapping** — each change mapped to an SR-N requirement from the issue and an OWASP ASVS control
- **Sequenced tasks** — ordered steps with file:method references
- **Edge cases** — what can go wrong and how it is handled
- **Human review checkpoints** — where to pause for confirmation before continuing

---

## Commit Rules

- Reference the issue in every commit: `closes #N` or `fixes #N`
- `/security-review` must return APPROVED before committing
- Do not use `--no-verify` to bypass hooks
- Do not amend published commits — create new commits instead
