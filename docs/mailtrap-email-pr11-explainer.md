# Mailtrap Sandbox Email Fix — PR #11 Explainer

> **Commits:** `f722357` fix: activate local Spring profile and add Mailtrap SMTP trust (fixes #10)
>             `7f887d3` fix: force-load lazy JPA associations before @Async email hand-off (fixes #10)
> **Files changed:** 3 files — `laps/.mvn/jvm.config`, `application.properties`, `LeaveService.java`

---

## Why Emails Were Not Being Delivered

LAPS sends three types of email notification via `EmailService`:

| Method | Triggered by | Recipient |
|---|---|---|
| `sendLeaveApplicationNotification()` | Employee applies for leave | Employee's manager |
| `sendLeaveApprovalNotification()` | Manager approves leave | Employee |
| `sendLeaveRejectionNotification()` | Manager rejects leave | Employee |

All three methods are annotated `@Async`, so they run in a background thread pool managed by Spring's `AsyncConfig`. Exceptions are caught inside each method and logged at `WARN` only — they never surface to the UI or abort the main transaction.

Three separate root causes prevented any email from reaching the Mailtrap sandbox inbox.

---

## Root Cause 1 — Local Spring Profile Never Activated

### What happened

`application-local.properties` holds the Mailtrap SMTP credentials for local development:

```properties
MAIL_HOST=sandbox.smtp.mailtrap.io
MAIL_PORT=2525
MAIL_USERNAME=...
MAIL_PASSWORD=...
```

Spring Boot only loads `application-local.properties` when the `local` profile is active. Without it, `application.properties` tries to resolve `${MAIL_HOST}`, `${MAIL_PORT}` etc. from OS environment variables — which are not set in a typical local dev shell.

There was no mechanism to activate the `local` profile automatically. A developer had to remember to pass `-Dspring.profiles.active=local` on every `mvn spring-boot:run` invocation.

### The fix

**`laps/.mvn/jvm.config`** (new file):

```
-Dspring.profiles.active=local
```

Maven reads `.mvn/jvm.config` and passes its contents as JVM arguments to every `mvn` command in that module. This activates the `local` profile automatically on every `mvn spring-boot:run`, `mvn test` (local), and `mvn package` run without any developer action.

**Why this does not affect CI or EC2:**
- The CI workflow (`ci.yml`) explicitly passes `-Dspring.profiles.active=ci`, which overrides `.mvn/jvm.config`.
- On EC2, the app runs as `java -jar laps.jar` via systemd — `.mvn/jvm.config` is a Maven build-time mechanism and has no effect on the runtime JVM.

---

## Root Cause 2 — STARTTLS Handshake Failing Silently

### What happened

Mailtrap sandbox requires STARTTLS on port 2525. `application.properties` enabled STARTTLS:

```properties
spring.mail.properties.mail.smtp.starttls.enable=true
```

However, JavaMail's STARTTLS implementation performs a TLS handshake and, by default, validates the server's certificate against the JVM's default trust store. Mailtrap's sandbox certificate is not in the default JVM trust store, so the handshake failed silently inside the `@Async` thread — only a `WARN` log was emitted, invisible unless actively monitored.

### The fix

Two properties were added to **`application.properties`**:

```properties
# Mailtrap sandbox STARTTLS: trust this host explicitly (ASVS V9.2.1)
spring.mail.properties.mail.smtp.ssl.trust=${MAIL_SMTP_SSL_TRUST:sandbox.smtp.mailtrap.io}

# Refuse cleartext fallback if server does not advertise STARTTLS (ASVS V9.1.2)
spring.mail.properties.mail.smtp.starttls.required=true
```

**`mail.smtp.ssl.trust`** tells JavaMail to trust the named host for STARTTLS without requiring it to be in the JVM trust store. It is scoped to a specific hostname (not wildcard `*`) to avoid trusting unintended servers. The value is configurable via the `MAIL_SMTP_SSL_TRUST` environment variable so a different SMTP provider can be used in future without a code change.

**`mail.smtp.starttls.required=true`** prevents JavaMail from falling back to a cleartext connection if the server does not advertise STARTTLS. This is a defence-in-depth control — the email send fails loudly rather than transmitting credentials and message content in plaintext.

These properties apply on both local dev (via `application-local.properties` credentials) and EC2 (via GitHub Secrets env vars).

---

## Root Cause 3 — JPA Lazy-Loading After Transaction Close

### What happened

This was the final and most subtle bug. After root causes 1 and 2 were fixed, live EC2 logs showed:

```
WARN [task-2] com.iss.laps.service.EmailService : Failed to send leave application notification email
org.hibernate.exception.GenericJDBCException: JDBC exception executing SQL
  [select ... from employees where id=?]
  [This statement has been closed.]
```

The error occurred before any SMTP connection was attempted.

### Why it happened

The sequence of events in `LeaveService.applyLeave()`:

```
1. @Transactional method starts → Hibernate Session opens
2. leaveAppRepo.save(application) → LeaveApplication saved; employee & leaveType set
3. emailService.sendLeaveApplicationNotification(saved) → @Async: task submitted to thread pool
4. @Transactional method returns → Hibernate Session CLOSES, JDBC connection returned to pool
5. [async thread starts] → tries to call application.getEmployee().getManager()
6. employee.manager is FetchType.LAZY → Hibernate tries to issue SQL
7. The original Session/Statement is closed → GenericJDBCException
```

The root cause: `Employee.manager` is declared `FetchType.LAZY` in the `Employee` model. When the `@Async` thread runs in step 5, the Hibernate session that loaded the `LeaveApplication` has already closed in step 4. Hibernate cannot issue the lazy-load SQL against a closed session.

The same problem affected `approveLeave()` and `rejectLeave()` — both call `findById()` which loads `LeaveApplication` lazily, then hand the entity to an `@Async` email method.

Note: `LeaveApplication.leaveType` is `FetchType.EAGER` and was not affected. `LeaveApplication.employee` is `FetchType.LAZY` and was also affected.

### The fix

A private helper method `initEmailAssociations(LeaveApplication app)` was added to **`LeaveService`**:

```java
private void initEmailAssociations(LeaveApplication app) {
    Employee emp = app.getEmployee();
    emp.getName();
    emp.getEmail();
    Employee mgr = emp.getManager();
    if (mgr != null) {
        mgr.getName();
        mgr.getEmail();
    }
    app.getLeaveType().getName();
}
```

By accessing these fields *inside* the `@Transactional` method (while the Hibernate session is still open), Hibernate issues the lazy-load SQL and stores the results in the entity's in-memory state. After the session closes and the `@Async` thread runs, all values are already in memory — no session is needed.

The helper is called at all three email notification sites, immediately before the `emailService.*` call:

```java
// applyLeave()
initEmailAssociations(saved);
emailService.sendLeaveApplicationNotification(saved);

// approveLeave()
initEmailAssociations(application);
emailService.sendLeaveApprovalNotification(application);

// rejectLeave()
initEmailAssociations(application);
emailService.sendLeaveRejectionNotification(application);
```

**Why not change fetch types to EAGER?** Changing `Employee.manager` or `LeaveApplication.employee` to `EAGER` would load those associations on *every* query that fetches those entities — including list pages, reports, and the movement register — causing unnecessary database joins across the entire application. The `initEmailAssociations()` approach loads only what is needed, only when an email is about to be sent.

**Why not open a new transaction in the async thread?** Annotating `EmailService` methods with `@Transactional` would open a new session in the async thread, but this creates a risk of reading stale data (the session would see a snapshot from before the save committed) and couples the email concern to the data access concern. The force-load approach is simpler and safer.

---

## Data Flow After All Three Fixes

```
Employee submits leave application
  → LeaveService.applyLeave()  [@Transactional — session open]
      → validateLeaveApplication()
      → leaveAppRepo.save()
      → initEmailAssociations(saved)       ← force-loads employee, manager, leaveType
      → emailService.sendLeaveApplicationNotification(saved)  [@Async — task queued]
  ← session closes, transaction commits

  [async thread pool picks up task]
  → EmailService.sendLeaveApplicationNotification()
      → all fields already in memory — no session needed
      → SimpleMailMessage assembled
      → JavaMailSender.send()
          → STARTTLS handshake to sandbox.smtp.mailtrap.io:2525
              ssl.trust=sandbox.smtp.mailtrap.io  ← Root Cause 2 fix
              starttls.required=true
          → credentials from ${MAIL_USERNAME} / ${MAIL_PASSWORD}
              local: resolved from application-local.properties via local profile  ← Root Cause 1 fix
              EC2:   resolved from /opt/laps/.env (GitHub Secrets)
      → Email delivered → Mailtrap sandbox inbox ✓
```

---

## Files Changed

| File | Change | Why |
|---|---|---|
| `laps/.mvn/jvm.config` | New — `-Dspring.profiles.active=local` | Auto-activates local profile so `application-local.properties` is loaded without manual flags |
| `laps/src/main/resources/application.properties` | Added `mail.smtp.ssl.trust` and `starttls.required` | Completes Mailtrap STARTTLS handshake; prevents cleartext fallback |
| `laps/src/main/java/com/iss/laps/service/LeaveService.java` | Added `initEmailAssociations()` + 3 call sites | Force-loads lazy JPA associations within the Hibernate session before `@Async` hand-off |

---

## Verification

**Local dev:**
```bash
mvn spring-boot:run          # no extra flags needed
# Log in as employee with a manager assigned
# Apply for leave
# Check Mailtrap sandbox inbox → email should appear
```

**EC2:**
```bash
# After deploying PR #11 to main:
sudo journalctl -u laps -f   # tail live logs
# Apply for leave on the app
# Expect: INFO [task-N] com.iss.laps.service.EmailService : Email sent to <manager-email>: Leave Application from ...
# Check Mailtrap sandbox inbox
```

**AWS Security Group (infrastructure — verify manually):**
- EC2 outbound rule must allow TCP port 2525 to `sandbox.smtp.mailtrap.io`
- Confirmed: existing "All traffic → 0.0.0.0/0" outbound rule covers port 2525
