#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# Slack webhook (선택)
ENV_FILE="$ROOT/docsa-alert.env"
[[ -f "$ENV_FILE" ]] && source "$ENV_FILE"

notify_slack () {
  local msg="$1"
  [[ -z "${SLACK_WEBHOOK_URL:-}" ]] && return 0
  curl -sS -X POST -H 'Content-type: application/json' \
    --data "{\"text\":${msg@Q}}" \
    "$SLACK_WEBHOOK_URL" >/dev/null 2>&1 || true
}

# ---- 1) certbot renew 실행 ----
out="$(
  docker compose run --rm --no-deps certbot_renew \
    certbot renew --webroot -w /var/www/certbot 2>&1 || true
)"

echo "$out"

# ---- 2) 실패 감지 ----
# certbot이 실패할 때 자주 나오는 문구 위주로 (오탐 줄임)
if echo "$out" | grep -Eqi \
  "certbot failed to authenticate|failed authorization procedure|the following errors were reported|unauthorized|too many requests|nxdomain|connection refused|timeout"; then
  err_summary="$(echo "$out" | tail -n 40)"
  notify_slack "❌ *Certbot renew 실패*
\`\`\`
${err_summary}
\`\`\`"
  exit 1
fi

# ---- 3) 갱신됨 감지 (갱신 성공 시에만 nginx reload) ----
# 성공 시 출력되는 대표 문구들
renewed=0
if echo "$out" | grep -Eqi "successfully renewed|congratulations, all renewals succeeded|renewal succeeded"; then
  renewed=1
fi

if (( renewed == 1 )); then
  # 실행 중인 컨테이너만 HUP
  for c in docsa-nginx docsa-nginx-stg; do
    if [[ -n "$(docker ps -q -f "name=^/${c}$")" ]]; then
      docker kill -s HUP "$c" >/dev/null 2>&1 || true
    fi
  done

  # ---- 4) 실제 서비스에서 인증서 만료일 확인(nginx 적용 검증) ----
  cert_api="$(echo | openssl s_client -connect api.docsa.o-r.kr:443 -servername api.docsa.o-r.kr 2>/dev/null \
    | openssl x509 -noout -enddate | cut -d= -f2 || true)"

  cert_stg="$(echo | openssl s_client -connect stg.api.docsa.o-r.kr:8443 -servername stg.api.docsa.o-r.kr 2>/dev/null \
    | openssl x509 -noout -enddate | cut -d= -f2 || true)"

  notify_slack "✅ *인증서 갱신 성공* → Nginx Reload 완료
• api 만료일: \`${cert_api:-확인실패}\`
• stg 만료일: \`${cert_stg:-확인실패}\`"
else
  # 갱신 불필요도 정상. (스팸 방지로 슬랙은 안 보냄)
  echo "[info] No renewal needed."
fi
