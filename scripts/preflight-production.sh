#!/usr/bin/env bash
set -euo pipefail

fail() { printf 'ERROR: %s\n' "$1" >&2; exit 1; }
require() {
  local name="$1"
  local value="${!name:-}"
  [[ -n "${value//[[:space:]]/}" ]] || fail "Required environment variable is missing: ${name}"
}

[[ "${SPRING_PROFILES_ACTIVE:-}" =~ (^|,)(prod|production)(,|$) ]] \
  || fail "Set SPRING_PROFILES_ACTIVE=prod (or include prod in the active profiles)."

for name in WORKFLOW_DB_URL WORKFLOW_DB_USERNAME WORKFLOW_DB_PASSWORD \
            WORKFLOW_ADMIN_USERNAME WORKFLOW_ADMIN_PASSWORD \
            WORKFLOW_SERVICE_USERNAME WORKFLOW_SERVICE_PASSWORD \
            WORKFLOW_ACTOR_SIGNING_SECRET WORKFLOW_SECRET_ENCRYPTION_KEY; do
  require "$name"
done

[[ "$WORKFLOW_DB_URL" == jdbc:postgresql://* ]] \
  || fail "WORKFLOW_DB_URL must use jdbc:postgresql:// for this service."

weak_password() {
  local value="${1,,}"
  [[ ${#value} -lt 16 || "$value" == "12345" || "$value" == "admin123" || "$value" == "workflow123" ]]
}
for name in WORKFLOW_DB_PASSWORD WORKFLOW_ADMIN_PASSWORD WORKFLOW_SERVICE_PASSWORD; do
  value="${!name}"
  weak_password "$value" && fail "$name is too short or uses a known development default."
done

[[ ${#WORKFLOW_ACTOR_SIGNING_SECRET} -ge 32 ]] \
  || fail "WORKFLOW_ACTOR_SIGNING_SECRET must contain at least 32 characters."

key_bytes=$(printf '%s' "$WORKFLOW_SECRET_ENCRYPTION_KEY" | base64 -d 2>/dev/null | wc -c | tr -d ' ')
[[ "$key_bytes" == "32" ]] \
  || fail "WORKFLOW_SECRET_ENCRYPTION_KEY must be Base64 for exactly 32 bytes (openssl rand -base64 32)."

if [[ "${WORKFLOW_ADMIN_USERNAME}" == "${WORKFLOW_SERVICE_USERNAME}" ]]; then
  fail "Administrator and service usernames must be different."
fi

printf 'Preflight passed. This checks required configuration only; it does not prove the service is production-ready.\n'
