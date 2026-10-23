#!/usr/bin/env bash
# Starts the workflow engine for local end-to-end testing.
#
# LOCAL TESTING ONLY: uses throwaway credentials, allows webhooks to localhost,
# and runs background jobs every 1-2 seconds so tests do not wait long.
# Never use these settings or credentials in a shared or production environment.
#
# Usage:  scripts/e2e/start-engine.sh
# Then, in another terminal:  python3 scripts/e2e/smoke_test.py
set -euo pipefail
cd "$(dirname "$0")/../.."

DB_NAME="${E2E_DB_NAME:-workflow_engine_e2e}"
DB_HOST="${E2E_DB_HOST:-localhost}"

# Java 21 is required; on macOS pick it automatically if the default is older.
if [[ -x /usr/libexec/java_home ]] && /usr/libexec/java_home -v 21 >/dev/null 2>&1; then
  export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
fi

# Create the test database on first run (no-op if it exists).
if command -v createdb >/dev/null 2>&1; then
  createdb -h "$DB_HOST" "$DB_NAME" 2>/dev/null || true
fi

# One random encryption key per checkout, kept outside git, so client and webhook
# secrets stored in the test database stay readable across restarts.
mkdir -p .e2e
[[ -s .e2e/encryption.key ]] || openssl rand -base64 32 > .e2e/encryption.key

export WORKFLOW_DB_URL="${WORKFLOW_DB_URL:-jdbc:postgresql://$DB_HOST:5432/$DB_NAME}"
export WORKFLOW_DB_USERNAME="${WORKFLOW_DB_USERNAME:-$USER}"
export WORKFLOW_DB_PASSWORD="${WORKFLOW_DB_PASSWORD:-}"
export WORKFLOW_ADMIN_USERNAME="e2e_admin"
export WORKFLOW_ADMIN_PASSWORD="e2e-admin-password-local-only"
export WORKFLOW_SERVICE_USERNAME="e2e_service"
export WORKFLOW_SERVICE_PASSWORD="e2e-service-password-local-only"
export WORKFLOW_ACTOR_SIGNING_SECRET="e2e-signing-secret-local-only-0123456789"
export WORKFLOW_SECRET_ENCRYPTION_KEY="$(cat .e2e/encryption.key)"
export WORKFLOW_WEBHOOKS_ALLOW_PRIVATE_NETWORK=true
export WORKFLOW_WEBHOOKS_POLL_INTERVAL_MS=1000
export WORKFLOW_SLA_POLL_INTERVAL_MS=2000
export SERVER_PORT="${SERVER_PORT:-8081}"

echo "Starting workflow engine on http://localhost:$SERVER_PORT (database: $WORKFLOW_DB_URL)"
echo "Swagger UI: http://localhost:$SERVER_PORT/swagger-ui/index.html  (admin: $WORKFLOW_ADMIN_USERNAME)"
exec ./mvnw -q spring-boot:run
