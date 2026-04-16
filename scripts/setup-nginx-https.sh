#!/usr/bin/env bash
# setup-nginx-https.sh — Generate a self-signed TLS certificate for LAPS on EC2
#
# Usage: bash setup-nginx-https.sh <DOMAIN>
#   DOMAIN — EC2 public DNS hostname (e.g. ec2-1-2-3-4.compute-1.amazonaws.com)
#
# Run on every deploy. The certificate is regenerated each time so it always
# matches the current EC2_HOST value (AWS Academy dynamic DNS — issue #9).
#
# nginx runs as a Docker container (issue #34) — this script only manages certs.
# The nginx container mounts /opt/laps/ssl read-only.
#
# Security controls implemented:
#   ASVS V6.4.1 — Private key chmod 600, owned by root

set -euo pipefail

DOMAIN="${1:?Usage: $0 <DOMAIN>}"
CERT_DIR="/opt/laps/ssl"

# ---------------------------------------------------------------------------
# 1. Create cert directory with tight permissions
# ---------------------------------------------------------------------------
echo "[1/3] Creating certificate directory ${CERT_DIR}..."
sudo mkdir -p "${CERT_DIR}"
sudo chmod 700 "${CERT_DIR}"

# ---------------------------------------------------------------------------
# 2. Generate self-signed certificate (regenerated on every deploy so CN always
#    matches the current dynamic EC2 DNS hostname)
# ---------------------------------------------------------------------------
echo "[2/3] Generating self-signed certificate for ${DOMAIN}..."
sudo openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout "${CERT_DIR}/server.key" \
  -out    "${CERT_DIR}/server.crt" \
  -subj   "/CN=${DOMAIN}" \
  -addext "subjectAltName=DNS:${DOMAIN}"

# Private key must be readable by root only (ASVS V6.4.1)
sudo chmod 600 "${CERT_DIR}/server.key"
sudo chmod 644 "${CERT_DIR}/server.crt"

# ---------------------------------------------------------------------------
# 3. Verify certificate was created
# ---------------------------------------------------------------------------
echo "[3/3] Verifying certificate..."
sudo openssl x509 -noout -subject -dates -in "${CERT_DIR}/server.crt"

echo ""
echo "TLS certificate setup complete."
echo "  Certificate : ${CERT_DIR}/server.crt  (self-signed, valid 365 days)"
echo "  Private key : ${CERT_DIR}/server.key  (chmod 600)"
echo "  Domain      : ${DOMAIN}"
echo ""
echo "NOTE: Browsers will show a self-signed certificate warning — this is expected."
