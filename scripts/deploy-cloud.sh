#!/usr/bin/env bash
set -Eeuo pipefail

# Deploys the single-instance showcase stack from a fixed GHCR sha-* image tag.
# Run this behind a host-level HTTPS/WSS reverse proxy; port 8080 stays on loopback.

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

ENV_FILE="${ENV_FILE:-.env.cloud}"
COMPOSE_FILE="docker-compose-cloud.yml"
PROJECT_NAME="${PROJECT_NAME:-xiyouji-cloud}"

compose() {
  docker compose --project-name "$PROJECT_NAME" --env-file "$ENV_FILE" --file "$COMPOSE_FILE" "$@"
}

fail() {
  echo "[ERROR] $*" >&2
  if command -v docker >/dev/null 2>&1; then
    compose ps 2>/dev/null || true
    compose logs --tail=120 app 2>/dev/null || true
  fi
  exit 1
}

command -v docker >/dev/null 2>&1 || fail "Docker is not installed."
docker compose version >/dev/null 2>&1 || fail "Docker Compose v2 is not installed."
[[ -f "$ENV_FILE" ]] || fail "Missing $ENV_FILE. Copy .env.cloud.example and replace every placeholder."

read_env() {
  sed -n "s/^$1=//p" "$ENV_FILE" | tail -n 1 | tr -d '\r'
}

APP_IMAGE="$(read_env APP_IMAGE)"
DB_PASSWORD="$(read_env DB_PASSWORD)"
JWT_SECRET="$(read_env JWT_SECRET)"
PUBLIC_BASE_URL="$(read_env PUBLIC_BASE_URL)"
CORS_ORIGINS="$(read_env CORS_ORIGINS)"
HTTP_BIND="$(read_env HTTP_BIND)"
HTTP_PORT="$(read_env HTTP_PORT)"

[[ "$APP_IMAGE" =~ ^ghcr\.io/[a-z0-9._/-]+(:sha-[a-f0-9]{7,40}|@sha256:[a-f0-9]{64})$ ]] || \
  fail "APP_IMAGE must use a ghcr.io/...:sha-<commit> tag or @sha256:<digest>."
[[ "$APP_IMAGE" != *replace* ]] || fail "APP_IMAGE still contains a placeholder."
[[ ${#DB_PASSWORD} -ge 20 && "$DB_PASSWORD" != *replace* && "$DB_PASSWORD" != *change-me* ]] || \
  fail "DB_PASSWORD must be a non-placeholder value with at least 20 characters."
[[ ${#JWT_SECRET} -ge 64 && "$JWT_SECRET" != *replace* && "$JWT_SECRET" != *change-me* ]] || \
  fail "JWT_SECRET must be a non-placeholder value with at least 64 characters."
[[ "$PUBLIC_BASE_URL" == https://* ]] || fail "PUBLIC_BASE_URL must be an HTTPS URL."
[[ "$CORS_ORIGINS" == https://* && "$CORS_ORIGINS" != *example.com* ]] || \
  fail "CORS_ORIGINS must contain the real HTTPS demo origin."
[[ "$HTTP_BIND" == "127.0.0.1" ]] || fail "HTTP_BIND must stay on 127.0.0.1 for VPS deployment."
[[ "$HTTP_PORT" =~ ^[0-9]{2,5}$ ]] || fail "HTTP_PORT must be a numeric TCP port."

echo "[1/5] Validating Compose configuration"
compose config --quiet || fail "Compose configuration is invalid."

echo "[2/5] Pulling the fixed commit image and runtime dependencies"
compose pull || fail "Image pull failed. If the GHCR package is private, run docker login ghcr.io first."

echo "[3/5] Starting MySQL, Redis and App"
compose up -d --no-build --wait --wait-timeout 360 || fail "Containers did not become healthy."

echo "[4/5] Verifying local health and frontend"
curl --fail --silent --show-error --max-time 15 "http://127.0.0.1:${HTTP_PORT}/actuator/health" | grep -q '"status":"UP"' || \
  fail "Health endpoint is not UP."
curl --fail --silent --show-error --max-time 15 "http://127.0.0.1:${HTTP_PORT}/" >/dev/null || \
  fail "Frontend entry page is unavailable."

echo "[5/5] Deployment passed"
compose ps
echo "Public URL: $PUBLIC_BASE_URL"
echo "Run the browser E2E from a trusted development machine after HTTPS/WSS is configured."
