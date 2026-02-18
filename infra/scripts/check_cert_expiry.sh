#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="/srv/docsa/infra/.secrets/docsa-alert.env"
THRESHOLD_DAYS=14

DOMAINS=(
  "api.docsa.o-r.kr:443"
  "stg.api.docsa.o-r.kr:8443"
)

[[ -f "$ENV_FILE" ]] && source "$ENV_FILE"

notify_slack () {
  local msg="$1"
  [[ -z "${SLACK_WEBHOOK_URL:-}" ]] && return 0
  curl -sS -X POST -H 'Content-type: application/json' \
    --data "{\"text\":${msg@Q}}" \
    "$SLACK_WEBHOOK_URL" >/dev/null || true
}

failed=0
now_epoch=$(date -u +%s)

for target in "${DOMAINS[@]}"; do
  host="${target%%:*}"
  port="${target##*:}"

  exp_date=$(echo | openssl s_client -connect "$host:$port" -servername "$host" 2>/dev/null \
    | openssl x509 -noout -enddate \
    | cut -d= -f2 || true)

  [[ -z "$exp_date" ]] && {
    notify_slack "❌ $host:$port 인증서 만료일 조회 실패"
    failed=1
    continue
  }

  exp_epoch=$(date -u -d "$exp_date" +%s)
  remain_days=$(( (exp_epoch - now_epoch) / 86400 ))

  if (( remain_days <= THRESHOLD_DAYS )); then
    notify_slack "⚠️ $host:$port 인증서 만료 임박 D-${remain_days} (만료: $exp_date UTC)"
    failed=1
  fi
done

exit "$failed"
