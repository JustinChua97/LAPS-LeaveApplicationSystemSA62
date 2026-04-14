# ECR + Docker Compose Deployment — PR #34 Explainer

> **Issue:** [#34 — Containerise LAPS and deploy via Amazon ECR + Docker Compose on EC2](https://github.com/zctiong-iss/ca_laps_team4/issues/34)
> **Files changed:** 14 files, +618 lines / −225 lines

---

## Why This Change Was Made

The previous deployment pipeline built a JAR on the GitHub Actions runner, copied it to EC2 via SCP, and managed the process with a systemd unit file. This approach had three problems:

1. **Not reproducible.** Each deploy rebuilt the JAR from source on the runner. A dependency version bump between two runs of the same commit could produce different binaries.
2. **Environment-coupled.** The EC2 instance needed Java, PostgreSQL, and nginx installed and configured manually. A fresh instance required manual setup steps before the first deploy.
3. **Fragile host state.** The systemd service, nginx config, and PostgreSQL data all lived directly on the EC2 host with no isolation between components.

Containerising the app solves all three: the image is built once, pushed to ECR tagged with the exact git SHA, and every environment runs the same artifact.

---

## What Changed

### New files

| File | Purpose |
|------|---------|
| `Dockerfile` | Multi-stage build: Maven + JDK 17 builds the JAR; `eclipse-temurin:17-jre-alpine` runs it as a non-root user |
| `docker-compose.yml` | Declares three services: `postgres`, `laps-app`, `nginx` |
| `docker/nginx/nginx.conf.template` | nginx config with `${NGINX_HOST}` placeholder; rendered at deploy time via `envsubst` |

### Modified files

| File | Change |
|------|--------|
| `infra/terraform/` | Stripped to a single `aws_ecr_repository` resource (IMMUTABLE tags, scan on push); all EC2 Terraform removed |
| `.github/workflows/ci.yml` | Added `docker-push` job: Terraform apply → ECR login → `docker build` → `docker push :<git-sha>` |
| `.github/workflows/deploy.yml` | Full rewrite: Docker/Compose install, legacy service stop, env file, cert gen, nginx config render, `docker compose up --wait` |
| `.github/workflows/terraform-destroy.yml` | Removed EC2-specific `TF_VAR_*` vars |
| `scripts/setup-nginx-https.sh` | Stripped to cert generation only — nginx now runs as a container |

---

## Architecture After This PR

```
EC2 instance
├── Docker Engine
│   └── laps-net (bridge network)
│       ├── laps-postgres-1  (postgres:16-alpine)   — port 5432, internal only
│       ├── laps-app-1       (ECR image :<git-sha>)  — port 8080, internal only
│       └── laps-nginx-1     (nginx:1.27-alpine)     — ports 80/443 → host
│
├── /opt/laps/.env           — app secrets (DB credentials, JWT secret, mail)
├── /opt/laps/nginx/nginx.conf  — rendered from docker/nginx/nginx.conf.template
├── /opt/laps/ssl/           — self-signed TLS cert (generated per deploy)
└── ECR                      — private image registry, one image per git SHA
```

**Startup order enforced by Compose:**
`postgres` healthy → `laps-app` starts → `laps-app` healthy → `nginx` starts

---

## How the CI/CD Pipeline Works Now

### CI workflow (`ci.yml`) — triggers on push to `main`

```
build-and-test  ──┐
                  ├── docker-push: terraform apply (ECR) → docker build → docker push :<sha>
semgrep-sast   ──┘
```

The image is tagged with the 40-character git SHA (`github.sha`). ECR tag mutability is `IMMUTABLE` — a SHA tag can never be overwritten.

### CD workflow (`deploy.yml`) — triggers automatically when CI succeeds on `main`

The `workflow_run` trigger fires after CI completes. It receives `github.event.workflow_run.head_sha` — the exact SHA that CI just pushed to ECR — so the deploy always pulls the image that was tested in the same pipeline run.

```
CI succeeds on main
  └── deploy.yml (workflow_run trigger, branches: [main])
        ├── Install Docker + Compose plugin (idempotent)
        ├── Stop legacy systemd laps + nginx services
        ├── Write /opt/laps/.env
        ├── Generate TLS cert (setup-nginx-https.sh)
        ├── Render nginx config (envsubst < nginx.conf.template)
        ├── Copy docker-compose.yml to EC2
        ├── export LAPS_IMAGE=<ecr-url>:<sha>
        ├── docker compose pull
        └── docker compose up -d --remove-orphans --wait --wait-timeout 180
              ↳ blocks until laps-app healthcheck passes (wget /login)
```

---

## Security Controls

| Control | Where | ASVS |
|---------|-------|------|
| No secrets in Docker image `ENV` | `Dockerfile` | V8.3.4 |
| Spring Boot not reachable on host network | `docker-compose.yml` — `expose` not `ports` for laps-app | V1.14.6 |
| ECR image tags immutable | `infra/terraform/main.tf` — `image_tag_mutability = "IMMUTABLE"` | V10.3.2 |
| ECR scan on push | `infra/terraform/main.tf` — `scan_on_push = true` | V1.14.6 |
| Image provenance tied to git SHA | `ci.yml` docker-push job | V10.3.2 |
| TLS 1.2/1.3 only, HSTS, no RC4/3DES | `docker/nginx/nginx.conf.template` | V9.1.1, V9.1.3 |
| Private key `chmod 600` | `scripts/setup-nginx-https.sh` | V6.4.1 |
| AWS credentials short-lived (lab session) | `configure-aws-credentials` action | V2.10.4 |

---

## Key Design Decisions

### Why postgres as a Docker service, not host PostgreSQL?

Running `postgres:16-alpine` as a Compose service means:
- No manual `dnf install postgresql15-server` or `pg_hba.conf` editing on the host
- Data is persisted in the named Docker volume `postgres-data`, which survives container restarts and redeployments
- The Spring Boot app connects to `postgres:5432` (Docker DNS), not `localhost` — this avoids the common mistake of `localhost` resolving to the container itself rather than the host

### Why `docker compose up --wait` instead of a custom health check loop?

The `--wait` flag blocks until every service with a defined `healthcheck` reports healthy. This is simpler, uses the same health definition as `docker ps`, and correctly handles the startup dependency chain (postgres → laps-app → nginx).

### Why `IMMUTABLE` ECR tags?

A mutable tag (e.g. `latest`) can be overwritten. If a push to `latest` coincides with a deploy, the EC2 instance might pull a different image than the one tested by CI. Immutable SHA tags guarantee the exact bytes that passed CI are what gets deployed.

### Why not use an IAM instance profile for ECR pull?

AWS Academy lab environments do not permit `iam:CreateRole` or `iam:AttachRolePolicy`. Short-lived session credentials from the GitHub Actions runner are forwarded over SSH to authenticate the `docker pull` on EC2. These credentials rotate every lab session and are never stored permanently on the instance.
