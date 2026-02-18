#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="/srv/docsa/infra/.secrets/docsa-alert.env"
TIMEOUT=5

TARGETS=(
  "api.docsa.o-r.kr:443"
  "stg.api.docsa.o-r.kr:8443"
)

[[ -f "$ENV_FILE" ]] && source "$ENV_FILE"

notify_slack () {
  local msg="$1"
  [[ -z "${SLACK_WEBHOOK_URL:-}" ]] && return 0
  curl -sS -X POST -H 'Content-type: application/json' \
    --data "{\"text\":${msg@Q}}" \
    "$SLACK_WEBHOOK_URL" >/dev/null 2>&1 || true
}

for target in "${TARGETS[@]}"; do
  host="${target%%:*}"
  port="${target##*:}"

  if ! timeout "${TIMEOUT}" bash -c "</dev/tcp/${host}/${port}" 2>/dev/null; then
    notify_slack "🚨 아아 공습경보 서버 불량: ${host}:${port}"
  fi
done
