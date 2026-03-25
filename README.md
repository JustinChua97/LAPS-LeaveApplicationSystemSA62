# ca_laps_team4 LAPS – Leave Application Processing System

ISS NUS SA62 Course Project | Spring Boot + PostgreSQL

---

## Quick Start (Local Development)

### Prerequisites
- Java 17+
- Maven 3.9+
- PostgreSQL 14+ running locally

### 1. Database Setup
```sql
CREATE DATABASE lapsdb;
```

### 2. Configure Local Credentials

Create `laps/application-local.properties` (gitignored — never committed):
```properties
DB_URL=jdbc:postgresql://localhost:5432/lapsdb
DB_USERNAME=<your-db-username>
DB_PASSWORD=<your-db-password>
MAIL_HOST=sandbox.smtp.mailtrap.io
MAIL_PORT=2525
MAIL_USERNAME=<your-mailtrap-username>
MAIL_PASSWORD=<your-mailtrap-password>
SEED_USER_PASSWORD=<password-for-test-accounts>
```

### 3. Run
```bash
cd laps
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

**VSCode:** Use the "LAPS (local)" launch config in `.vscode/launch.json` (F5).

Open: http://localhost:8080

### Test Accounts
All accounts use the password set in `SEED_USER_PASSWORD`.

| Username | Role | Description |
|---|---|---|
| `admin` | Admin | System administrator |
| `mgr.chen` | Manager | Manager with 2 subordinates |
| `mgr.lim` | Manager | Manager with 2 subordinates |
| `emp.tan` | Employee | Under mgr.chen (Administrative) |
| `emp.kumar` | Employee | Under mgr.chen (Professional) |
| `emp.ali` | Employee | Under mgr.lim (Administrative) |
| `emp.sarah` | Employee | Under mgr.lim (Professional) |

---

## CI/CD (GitHub Actions)

The pipeline runs on every push to `main` or `feature-*` branches and on pull requests to `main`.

It spins up a PostgreSQL 14 service container, builds the app, and runs all tests against a real database.

### Required GitHub Secrets

Go to **Settings → Secrets and variables → Actions** and add:

| Secret | Description |
|--------|-------------|
| `DB_URL` | PostgreSQL connection URL (e.g. `jdbc:postgresql://localhost:5432/lapsdb`) |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `MAIL_HOST` | SMTP host |
| `MAIL_PORT` | SMTP port |
| `MAIL_USERNAME` | SMTP username |
| `MAIL_PASSWORD` | SMTP password |
| `SEED_USER_PASSWORD` | Password assigned to all seeded test accounts |

### Running Tests Locally Against PostgreSQL
```bash
export DB_URL=jdbc:postgresql://localhost:5432/lapsdb
export DB_USERNAME=<your-db-username>
export DB_PASSWORD=<your-db-password>
export SEED_USER_PASSWORD=<your-seed-password>
mvn test -Dspring.profiles.active=ci
```

---

## Architecture

```
Controller → Service → Repository → PostgreSQL
     ↓
Thymeleaf Views (Spring Security-protected)
REST API (/api/v1/**)
```

### Layers
- **Controller** – Spring MVC + REST (`/employee`, `/manager`, `/admin`, `/api/v1`)
- **Service** – Business logic, leave validation, email notifications
- **Repository** – Spring Data JPA
- **Model** – JPA entities (Employee, LeaveApplication, LeaveType, LeaveEntitlement, CompensationClaim, PublicHoliday)

---

## Features

### Mandatory ✅
- [x] 3 roles: Admin, Manager, Employee
- [x] 3 leave types: Annual, Medical, Compensation
- [x] Login with Spring Security (two entry points redirect to same form)
- [x] Leave application submission with full validation
- [x] Annual leave: ≤14 days excludes weekends/PH; >14 days counts all
- [x] Medical leave: max 60 days/year
- [x] View personal leave history (paginated)
- [x] View/update/delete/cancel leave application
- [x] Manager: view applications for approval (grouped by subordinate)
- [x] Manager: approve/reject with mandatory comment on reject
- [x] State machine: Applied → Approved/Rejected/Updated/Deleted/Cancelled

### Optional ✅
- [x] **Administration** – CRUD employees, roles, leave types, public holidays, entitlements
- [x] **Spring Security** – Role-based access, BCrypt passwords, CSRF protection
- [x] **Compensation Leave Management** – Claim overtime, manager approval, ledger tracking
- [x] **Reporting** – Leave reports by date range/type, CSV export
- [x] **Movement Register** – All employees on leave for selected month (all users)
- [x] **Pagination** – Configurable page size (10/20/25)
- [x] **REST API** – `/api/v1/leaves/my`, `/api/v1/movement`, `/api/v1/leave-types`
- [x] **Email notifications** – On apply, approve, reject (async, configurable SMTP)

---

## Email Configuration

Email notifications are sent asynchronously (`@Async`) on leave apply, approve, and reject.
Failures are non-fatal — the app logs a warning and continues.

Configure via environment variables (see secrets table above). For local dev, Mailtrap sandbox is recommended — emails are intercepted and never reach real inboxes.

---

## Diagrams
- `diagrams/class-diagram.puml` – Class diagram (PlantUML)
- `diagrams/erd.puml` – Entity Relationship Diagram (PlantUML)

Render with: https://www.plantuml.com/plantuml/uml/ or IntelliJ PlantUML plugin
