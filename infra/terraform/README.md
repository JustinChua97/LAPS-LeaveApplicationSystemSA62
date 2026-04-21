# LAPS AWS Academy Terraform — ECR Repository

This folder manages the **ECR private repository** that stores the LAPS Docker image.

The EC2 instance, VPC, RDS database, and all other infrastructure are managed manually
in AWS Academy. IAM constraints in the lab environment prevent full IaC coverage.

## What Terraform Manages

| Resource | Description |
| --- | --- |
| `aws_ecr_repository.laps` | Private ECR repo; tag mutability `IMMUTABLE`; scan on push enabled |

## Prerequisites

- AWS Academy lab started
- AWS credentials exported as environment variables (see GitHub Actions Configuration below)

Do not commit AWS credentials, private keys, Terraform state, or real `terraform.tfvars` files.

## GitHub Actions Configuration

The CI workflow owns Terraform apply for the team (in the `docker-push` job). Configure
these GitHub secrets:

- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`
- `AWS_SESSION_TOKEN`
- `EC2_SSH_PRIVATE_KEY`
- `EC2_HOST` — EC2 public DNS hostname
- `EC2_USER` — SSH user (e.g. `ec2-user`)
- `EC2_KNOWN_HOST` — output of `ssh-keyscan <EC2_HOST>`
- App secrets: `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `SEED_USER_PASSWORD`,
  `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`
- `RDS_ENDPOINT` — RDS endpoint in `hostname:5432` format (from the AWS console)

Configure these GitHub variables:

- `AWS_REGION` — e.g. `us-east-1`

AWS Academy credentials are session-based. Refresh the three AWS credential secrets
(`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN`) when the lab session changes.

## CI/CD Flow

```text
CI — docker-push job (on push to main, after tests pass):
  1. configure-aws-credentials (session secrets)
  2. terraform init / fmt-check / validate / apply  → creates ECR repo if not exists
  3. ECR_URL=$(terraform output -raw ecr_repository_url)
  4. docker build -t $ECR_URL:${{ github.sha }} .
  5. docker push $ECR_URL:${{ github.sha }}

CD — deploy job (on push to main, after docker-push succeeds):
  1. SSH to EC2
  2. Install Docker + docker-compose-plugin if not present
  3. Stop legacy systemd laps service if running
  4. aws ecr get-login-password | docker login $ECR_URL
  5. Generate TLS cert (setup-nginx-https.sh)
  6. Render nginx config from docker/nginx/nginx.conf.template via envsubst
  7. Write /opt/laps/.env — DB_URL points to RDS_ENDPOINT secret
  8. Copy docker-compose.rds.yml to EC2 as /opt/laps/docker-compose.yml
  9. LAPS_IMAGE=$ECR_URL:<sha> docker compose pull && docker compose up -d --remove-orphans
  10. Health check: wget http://localhost:8080/login
```

## RDS Setup (one-time, before first deploy)

Create the RDS instance manually in the AWS console:

1. RDS → Create database → Standard create → PostgreSQL 16
2. Template: **Free tier**
3. DB instance identifier: `laps-postgres`
4. Master username / password → set as `DB_USERNAME` / `DB_PASSWORD` GitHub Secrets
5. Initial database name: `lapsdb`
6. VPC: your Academy VPC; Subnet group: create new across 2 AZs
7. VPC security group: create new; add inbound rule — PostgreSQL (5432) from EC2 private IP (`/32`)
8. Public access: **No**
9. After status is **Available**, copy the endpoint → set as `RDS_ENDPOINT` GitHub Secret (`hostname:5432`)

Spring Boot seeds `lapsdb` automatically on first connect via `data.sql`.

## Security Properties

| Property | Value |
| --- | --- |
| Repository visibility | Private |
| Image tag mutability | `IMMUTABLE` — SHA tags cannot be overwritten |
| Scan on push | `true` — ECR Basic Scanning runs on every push |

## Optional Local Inspection

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars
terraform init -backend=false
terraform fmt -check -recursive
terraform validate
```

`terraform.tfvars` is ignored by Git.

## Teardown

To destroy the ECR repository, manually run **IaC - Destroy AWS ECR Terraform**
in GitHub Actions, or run locally:

```bash
cd infra/terraform
terraform destroy
```

This removes only the ECR repository. It does not remove GitHub secrets, workflow
artifacts, EC2 instances, RDS instances, or any other resources.
