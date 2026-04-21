#!/usr/bin/env bash
# setup-ecs.sh — One-time ECS Fargate infrastructure setup for LAPS — issue #122
#
# Automates all AWS console steps: cert → ACM → security groups → target group
# → ALB → ECS cluster → task definition → ECS service → GitHub secrets.
#
# Usage:
#   export VPC_ID=vpc-xxxxxxxxxxxxxxxxx
#   export SUBNET_IDS="subnet-aaaaaaaaaaaaaaaa,subnet-bbbbbbbbbbbbbbbb"
#   export DB_URL=jdbc:postgresql://<rds-endpoint>/lapsdb
#   export DB_USERNAME=lapsadmin
#   export DB_PASSWORD=yourpassword
#   export JWT_SECRET=yourjwtsecret
#   export SEED_USER_PASSWORD=yourpassword
#   export MAIL_HOST=sandbox.smtp.mailtrap.io
#   export MAIL_PORT=2525
#   export MAIL_USERNAME=yourusername
#   export MAIL_PASSWORD=yourpassword
#   bash scripts/setup-ecs.sh
#
# Prerequisites: aws CLI, jq, gh CLI, openssl — all available in AWS Academy.
# Re-running is safe: existing resources are detected and reused.

set -euo pipefail

# ── Config ───────────────────────────────────────────────────────────────────
AWS_REGION="${AWS_REGION:-us-east-1}"
PREFIX="${PREFIX:-laps}"

# ── Validate required inputs ─────────────────────────────────────────────────
REQUIRED_VARS=(VPC_ID SUBNET_IDS DB_URL DB_USERNAME DB_PASSWORD JWT_SECRET SEED_USER_PASSWORD MAIL_HOST MAIL_PORT MAIL_USERNAME MAIL_PASSWORD)
for var in "${REQUIRED_VARS[@]}"; do
  if [[ -z "${!var:-}" ]]; then
    echo "ERROR: $var is not set. Export it before running this script." >&2
    exit 1
  fi
done

# ── Check dependencies ───────────────────────────────────────────────────────
for cmd in aws jq gh openssl; do
  if ! command -v "$cmd" &>/dev/null; then
    echo "ERROR: $cmd is required but not installed." >&2
    exit 1
  fi
done

ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
ECR_URL="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${PREFIX}"

# Convert comma-separated subnet IDs to JSON array for AWS CLI
SUBNET_JSON=$(echo "$SUBNET_IDS" | tr ',' '\n' | jq -R . | jq -s .)

echo "========================================================"
echo " LAPS ECS Setup — AWS Account: ${ACCOUNT_ID}"
echo " Region: ${AWS_REGION}  VPC: ${VPC_ID}"
echo "========================================================"

# ── Step 1: Self-signed cert → ACM ───────────────────────────────────────────
echo ""
echo "[1/8] Generating self-signed certificate and importing to ACM..."

openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout /tmp/laps-server.key \
  -out    /tmp/laps-server.crt \
  -subj   "/CN=${PREFIX}" \
  -addext "subjectAltName=DNS:${PREFIX}" 2>/dev/null

# Import cert to ACM (idempotent — check if one already exists)
EXISTING_CERT_ARN=$(aws acm list-certificates --region "${AWS_REGION}" \
  --query "CertificateSummaryList[?DomainName=='${PREFIX}'].CertificateArn | [0]" \
  --output text 2>/dev/null || echo "None")

if [[ "$EXISTING_CERT_ARN" == "None" || -z "$EXISTING_CERT_ARN" ]]; then
  CERT_ARN=$(aws acm import-certificate \
    --certificate fileb:///tmp/laps-server.crt \
    --private-key fileb:///tmp/laps-server.key \
    --region "${AWS_REGION}" \
    --query CertificateArn --output text)
  echo "  Imported certificate: ${CERT_ARN}"
else
  # Re-import to refresh the cert
  CERT_ARN=$(aws acm import-certificate \
    --certificate-arn "${EXISTING_CERT_ARN}" \
    --certificate fileb:///tmp/laps-server.crt \
    --private-key fileb:///tmp/laps-server.key \
    --region "${AWS_REGION}" \
    --query CertificateArn --output text)
  echo "  Re-imported existing certificate: ${CERT_ARN}"
fi
rm -f /tmp/laps-server.crt /tmp/laps-server.key

# ── Step 2: Security groups ───────────────────────────────────────────────────
echo ""
echo "[2/8] Creating security groups..."

# ALB security group
ALB_SG_ID=$(aws ec2 describe-security-groups \
  --filters "Name=group-name,Values=${PREFIX}-alb-sg" "Name=vpc-id,Values=${VPC_ID}" \
  --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null || echo "None")

if [[ "$ALB_SG_ID" == "None" || "$ALB_SG_ID" == "null" ]]; then
  ALB_SG_ID=$(aws ec2 create-security-group \
    --group-name "${PREFIX}-alb-sg" \
    --description "LAPS ALB - allow HTTPS from internet" \
    --vpc-id "${VPC_ID}" \
    --query GroupId --output text)
  aws ec2 authorize-security-group-ingress \
    --group-id "${ALB_SG_ID}" \
    --protocol tcp --port 443 --cidr 0.0.0.0/0
  aws ec2 authorize-security-group-ingress \
    --group-id "${ALB_SG_ID}" \
    --protocol tcp --port 80 --cidr 0.0.0.0/0
  echo "  Created ALB SG: ${ALB_SG_ID}"
else
  echo "  Reusing ALB SG: ${ALB_SG_ID}"
fi

# ECS task security group
ECS_SG_ID=$(aws ec2 describe-security-groups \
  --filters "Name=group-name,Values=${PREFIX}-ecs-sg" "Name=vpc-id,Values=${VPC_ID}" \
  --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null || echo "None")

if [[ "$ECS_SG_ID" == "None" || "$ECS_SG_ID" == "null" ]]; then
  ECS_SG_ID=$(aws ec2 create-security-group \
    --group-name "${PREFIX}-ecs-sg" \
    --description "LAPS ECS tasks - allow port 8080 from ALB only" \
    --vpc-id "${VPC_ID}" \
    --query GroupId --output text)
  aws ec2 authorize-security-group-ingress \
    --group-id "${ECS_SG_ID}" \
    --protocol tcp --port 8080 \
    --source-group "${ALB_SG_ID}"
  echo "  Created ECS SG: ${ECS_SG_ID}"
else
  echo "  Reusing ECS SG: ${ECS_SG_ID}"
fi

# ── Step 3: Target group ──────────────────────────────────────────────────────
echo ""
echo "[3/8] Creating target group..."

TG_ARN=$(aws elbv2 describe-target-groups \
  --names "${PREFIX}-tg" \
  --query 'TargetGroups[0].TargetGroupArn' --output text 2>/dev/null || echo "None")

if [[ "$TG_ARN" == "None" || "$TG_ARN" == "null" ]]; then
  TG_ARN=$(aws elbv2 create-target-group \
    --name "${PREFIX}-tg" \
    --protocol HTTP \
    --port 8080 \
    --vpc-id "${VPC_ID}" \
    --target-type ip \
    --health-check-path /login \
    --health-check-interval-seconds 30 \
    --healthy-threshold-count 2 \
    --unhealthy-threshold-count 3 \
    --query 'TargetGroups[0].TargetGroupArn' --output text)
  echo "  Created target group: ${TG_ARN}"
else
  echo "  Reusing target group: ${TG_ARN}"
fi

# ── Step 4: ALB ───────────────────────────────────────────────────────────────
echo ""
echo "[4/8] Creating Application Load Balancer..."

ALB_ARN=$(aws elbv2 describe-load-balancers \
  --names "${PREFIX}-alb" \
  --query 'LoadBalancers[0].LoadBalancerArn' --output text 2>/dev/null || echo "None")

if [[ "$ALB_ARN" == "None" || "$ALB_ARN" == "null" ]]; then
  ALB_ARN=$(aws elbv2 create-load-balancer \
    --name "${PREFIX}-alb" \
    --subnets $(echo "$SUBNET_IDS" | tr ',' ' ') \
    --security-groups "${ALB_SG_ID}" \
    --scheme internet-facing \
    --type application \
    --query 'LoadBalancers[0].LoadBalancerArn' --output text)
  echo "  Created ALB: ${ALB_ARN}"

  # HTTPS listener
  aws elbv2 create-listener \
    --load-balancer-arn "${ALB_ARN}" \
    --protocol HTTPS \
    --port 443 \
    --certificates CertificateArn="${CERT_ARN}" \
    --default-actions Type=forward,TargetGroupArn="${TG_ARN}" > /dev/null

  # HTTP → HTTPS redirect
  aws elbv2 create-listener \
    --load-balancer-arn "${ALB_ARN}" \
    --protocol HTTP \
    --port 80 \
    --default-actions "Type=redirect,RedirectConfig={Protocol=HTTPS,Port=443,StatusCode=HTTP_301}" > /dev/null
  echo "  Created HTTPS listener (443) and HTTP→HTTPS redirect (80)"
else
  echo "  Reusing ALB: ${ALB_ARN}"
fi

ALB_DNS=$(aws elbv2 describe-load-balancers \
  --load-balancer-arns "${ALB_ARN}" \
  --query 'LoadBalancers[0].DNSName' --output text)
echo "  ALB DNS: ${ALB_DNS}"

# ── Step 5: ECS cluster ───────────────────────────────────────────────────────
echo ""
echo "[5/8] Creating ECS cluster..."

CLUSTER_STATUS=$(aws ecs describe-clusters \
  --clusters "${PREFIX}" \
  --query 'clusters[0].status' --output text 2>/dev/null || echo "MISSING")

if [[ "$CLUSTER_STATUS" != "ACTIVE" ]]; then
  aws ecs create-cluster \
    --cluster-name "${PREFIX}" \
    --capacity-providers FARGATE \
    --default-capacity-provider-strategy capacityProvider=FARGATE,weight=1 > /dev/null
  echo "  Created ECS cluster: ${PREFIX}"
else
  echo "  Reusing ECS cluster: ${PREFIX}"
fi

# ── Step 6: CloudWatch log group ──────────────────────────────────────────────
echo ""
echo "[6/8] Ensuring CloudWatch log group exists..."
aws logs create-log-group --log-group-name "/ecs/${PREFIX}" 2>/dev/null || true
echo "  Log group: /ecs/${PREFIX}"

# ── Step 7: Task definition ───────────────────────────────────────────────────
echo ""
echo "[7/8] Registering ECS task definition..."

# Get latest ECR image (most recently pushed)
LATEST_IMAGE=$(aws ecr describe-images \
  --repository-name "${PREFIX}" \
  --query 'sort_by(imageDetails,&imagePushedAt)[-1].imageTags[0]' \
  --output text 2>/dev/null || echo "latest")
IMAGE_URI="${ECR_URL}:${LATEST_IMAGE}"
echo "  Using image: ${IMAGE_URI}"

TASK_DEF_ARN=$(aws ecs register-task-definition \
  --family "${PREFIX}-task" \
  --network-mode awsvpc \
  --requires-compatibilities FARGATE \
  --cpu 512 \
  --memory 1024 \
  --execution-role-arn "arn:aws:iam::${ACCOUNT_ID}:role/LabRole" \
  --task-role-arn "arn:aws:iam::${ACCOUNT_ID}:role/LabRole" \
  --container-definitions "$(jq -n \
    --arg image  "${IMAGE_URI}" \
    --arg region "${AWS_REGION}" \
    --arg prefix "${PREFIX}" \
    --arg db_url     "${DB_URL}" \
    --arg db_user    "${DB_USERNAME}" \
    --arg db_pass    "${DB_PASSWORD}" \
    --arg jwt        "${JWT_SECRET}" \
    --arg seed_pw    "${SEED_USER_PASSWORD}" \
    --arg mail_host  "${MAIL_HOST}" \
    --arg mail_port  "${MAIL_PORT}" \
    --arg mail_user  "${MAIL_USERNAME}" \
    --arg mail_pass  "${MAIL_PASSWORD}" \
    --arg alb_dns    "https://${ALB_DNS}" \
    '[{
      "name": "laps-app",
      "image": $image,
      "portMappings": [{"containerPort": 8080, "protocol": "tcp"}],
      "environment": [
        {"name": "DB_URL",              "value": $db_url},
        {"name": "DB_USERNAME",         "value": $db_user},
        {"name": "DB_PASSWORD",         "value": $db_pass},
        {"name": "JWT_SECRET",          "value": $jwt},
        {"name": "SEED_USER_PASSWORD",  "value": $seed_pw},
        {"name": "MAIL_HOST",           "value": $mail_host},
        {"name": "MAIL_PORT",           "value": $mail_port},
        {"name": "MAIL_USERNAME",       "value": $mail_user},
        {"name": "MAIL_PASSWORD",       "value": $mail_pass},
        {"name": "EC2_HOST",            "value": $alb_dns},
        {"name": "MAIL_SMTP_SSL_TRUST", "value": "sandbox.smtp.mailtrap.io"}
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group":         "/ecs/laps",
          "awslogs-region":        $region,
          "awslogs-stream-prefix": "laps"
        }
      },
      "healthCheck": {
        "command":     ["CMD-SHELL", "wget -qO /dev/null http://localhost:8080/login || exit 1"],
        "interval":    10,
        "timeout":     5,
        "retries":     12,
        "startPeriod": 60
      }
    }]')" \
  --query 'taskDefinition.taskDefinitionArn' --output text)
echo "  Registered: ${TASK_DEF_ARN}"

# ── Step 8: ECS service ───────────────────────────────────────────────────────
echo ""
echo "[8/8] Creating ECS service..."

SERVICE_STATUS=$(aws ecs describe-services \
  --cluster "${PREFIX}" \
  --services "${PREFIX}-service" \
  --query 'services[0].status' --output text 2>/dev/null || echo "MISSING")

if [[ "$SERVICE_STATUS" != "ACTIVE" ]]; then
  aws ecs create-service \
    --cluster "${PREFIX}" \
    --service-name "${PREFIX}-service" \
    --task-definition "${TASK_DEF_ARN}" \
    --desired-count 1 \
    --launch-type FARGATE \
    --network-configuration "awsvpcConfiguration={subnets=$(echo "$SUBNET_IDS" | tr ',' '\n' | jq -R . | jq -s . -c),securityGroups=[\"${ECS_SG_ID}\"],assignPublicIp=ENABLED}" \
    --load-balancers "targetGroupArn=${TG_ARN},containerName=laps-app,containerPort=8080" \
    --health-check-grace-period-seconds 120 > /dev/null
  echo "  Created ECS service: ${PREFIX}-service"
else
  echo "  Reusing ECS service: ${PREFIX}-service (updating task definition)"
  aws ecs update-service \
    --cluster "${PREFIX}" \
    --service "${PREFIX}-service" \
    --task-definition "${TASK_DEF_ARN}" \
    --force-new-deployment > /dev/null
fi

# ── Set GitHub secrets (skipped in CI — GITHUB_TOKEN cannot write secrets) ───
echo ""
if [[ -n "${GH_TOKEN:-}" ]] && gh auth status &>/dev/null 2>&1; then
  echo "Setting GitHub secrets ECS_CLUSTER and ECS_SERVICE via gh CLI..."
  gh secret set ECS_CLUSTER --body "${PREFIX}"
  gh secret set ECS_SERVICE  --body "${PREFIX}-service"
  echo "  ECS_CLUSTER=${PREFIX}"
  echo "  ECS_SERVICE=${PREFIX}-service"
else
  echo "  Skipping gh secret set (GH_TOKEN not available or lacks permission)."
  echo "  Set these two GitHub Secrets manually:"
  echo "    ECS_CLUSTER = ${PREFIX}"
  echo "    ECS_SERVICE = ${PREFIX}-service"
fi

# ── Done ─────────────────────────────────────────────────────────────────────
echo ""
echo "========================================================"
echo " Setup complete!"
echo ""
echo " ALB DNS : https://${ALB_DNS}"
echo "           (self-signed cert — browser warning expected)"
echo ""
echo " GitHub Secrets to set manually if not set above:"
echo "   ECS_CLUSTER = ${PREFIX}"
echo "   ECS_SERVICE = ${PREFIX}-service"
echo ""
echo " Next steps:"
echo "   1. Set ECS_CLUSTER and ECS_SERVICE in GitHub Secrets if shown above"
echo "   2. Wait ~2 min for ECS task to reach RUNNING status"
echo "   3. Check: ECS → ${PREFIX} cluster → ${PREFIX}-service → Tasks"
echo "   4. Merge feat/ecs-fargate-122 — future deploys use ECS automatically"
echo "========================================================"
