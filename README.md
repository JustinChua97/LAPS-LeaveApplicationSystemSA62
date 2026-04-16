# ca_laps_team4 LAPS – Leave Application Processing System

ISS NUS SA62 Course Project | Spring Boot + PostgreSQL

---
##Contributors


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

Two workflows run automatically:

| Workflow | Trigger | What it does |
|---|---|---|
| **CI** (`ci.yml`) | Pull request to `main`, manual | Builds, runs all tests against a PostgreSQL container, runs Semgrep SAST scan |
| **CD** (`deploy.yml`) | After CI passes on `main`, manual dispatch | Downloads tested JAR from CI run (or builds on manual dispatch), deploys to AWS EC2 via SSH, restarts the `laps` systemd service, health-checks the app |

### Required GitHub Secrets

Go to **Settings → Secrets and variables → Actions** and add:

| Secret | Description |
|--------|-------------|
| `DB_URL` | PostgreSQL JDBC URL (e.g. `jdbc:postgresql://localhost:5432/lapsdb`) |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `MAIL_HOST` | SMTP host |
| `MAIL_PORT` | SMTP port |
| `MAIL_USERNAME` | SMTP username |
| `MAIL_PASSWORD` | SMTP password |
| `SEED_USER_PASSWORD` | Password assigned to all seeded test accounts |
| `EC2_HOST` | **Public DNS** of the EC2 instance (e.g. `ec2-32-195-62-44.compute-1.amazonaws.com`) — use DNS, not IP address |
| `EC2_USER` | SSH user on EC2 (e.g. `ec2-user`) |
| `EC2_SSH_PRIVATE_KEY` | Private key (PEM) for SSH access to EC2 |
| `EC2_KNOWN_HOST` | EC2 host public key for `known_hosts` — run `ssh-keyscan <EC2_HOST>` (without `-H`) to get both ecdsa and ed25519 keys, paste both lines |
| `SEMGREP_APP_TOKEN` | Semgrep API token for SAST scanning |

### CD Deploy Behaviour

- **Automatic:** triggers after CI passes on `main` and reuses the JAR artifact built and tested by CI
- **Manual:** trigger via `workflow_dispatch` — builds the JAR fresh from the current `main` branch
- **Skips deploy** if CI failed or was cancelled
- **Concurrency lock** — if two deploys fire at the same time, the newer one cancels the older

### EC2 Setup (automated by CD workflow)

The CD workflow handles first-time EC2 provisioning automatically on each deploy:
- Writes app environment variables to `/opt/laps/.env` (secrets injected by GitHub Actions)
- Installs PostgreSQL 15 if not present, switches auth to `md5`, creates the `lapsdb` database and app user (idempotent)
- Creates/updates the `laps` systemd service (`EnvironmentFile=/opt/laps/.env`) and enables it
- Deploys the JAR, restarts the service, waits up to 60s for the health check at `http://localhost:8080/login`

**EC2 prerequisites (manual, one-time):**
- Amazon Linux 2023 instance with Java 17 installed: `sudo dnf install -y java-17-amazon-corretto`
- Security group inbound rules: see HTTPS section below
- Get `EC2_KNOWN_HOST` secret:
  ```bash
  ssh-keyscan ec2-32-195-62-44.compute-1.amazonaws.com
  ```
  Copy **both the ecdsa and ed25519 key lines** (without the `# comment` lines) and paste into the GitHub secret

### HTTPS Setup (issue #9)

The CD workflow automatically configures nginx as a TLS-terminating reverse proxy on each deploy using a self-signed certificate. No additional secrets are required — `EC2_HOST` is already used.

**EC2 Security Group rules (update in AWS Console after first HTTPS deploy):**

| Type | Port | Source | Purpose |
| --- | --- | --- | --- |
| SSH | 22 | Your IP | Admin access |
| HTTP | 80 | 0.0.0.0/0 | nginx → 301 redirect to HTTPS |
| HTTPS | 443 | 0.0.0.0/0 | nginx TLS endpoint |
| Custom TCP | 8080 | **Remove or restrict to 127.0.0.1/32** | Spring Boot must NOT be publicly reachable |

> **Important:** Removing public access to port 8080 is a required security step (ASVS V1.9.2). If port 8080 remains open, TLS provides no protection — attackers can bypass nginx entirely.

**Browser self-signed certificate warning:**
Because AWS Academy EC2 instances use a dynamic public DNS that changes between lab sessions, a CA-signed certificate (Let's Encrypt) is not practical. The deploy generates a fresh self-signed certificate on each run. Browsers will display a "Your connection is not private" warning — this is expected. Click **Advanced → Proceed** to access the application. Traffic is fully encrypted; only the CA-issued trust chain is absent.

**Verify HTTPS after deploy:**
```bash
# HTTPS responds with 200 and HSTS header
curl -k -I https://<EC2_HOST>/login

# HTTP redirects to HTTPS (301)
curl -I http://<EC2_HOST>

# Session cookie has Secure and HttpOnly flags
curl -k -c /dev/null -I https://<EC2_HOST>/login | grep Set-Cookie

# Confirm TLS 1.2+ only (requires nmap)
nmap --script ssl-enum-ciphers -p 443 <EC2_HOST>
```

**Certificate details:**

- Location: `/opt/laps/ssl/server.crt` (cert) and `/opt/laps/ssl/server.key` (private key)
- Private key permissions: `chmod 600` (root-only read, ASVS V6.4.1)
- Validity: 365 days from each deploy (regenerated on every deploy)

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


