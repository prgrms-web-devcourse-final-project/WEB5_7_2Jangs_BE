#!/usr/bin/env bash
set -euo pipefail

INFRA_DIR=$(cd "$(dirname "$0")/.." && pwd)

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

prod_services=$(docker compose -f "$INFRA_DIR/docker-compose.yml" config --services)
for legacy_service in nginx certbot_init certbot_renew; do
  if printf '%s\n' "$prod_services" | grep -Fxq "$legacy_service"; then
    fail "운영 Compose에 $legacy_service 서비스가 남아 있음"
  fi
done

staging_config=$(docker compose -f "$INFRA_DIR/docker-compose.stg.yml" config --no-interpolate)
printf '%s\n' "$staging_config" |
  grep -Fq 'source: /srv/gateway/certbot/etc' ||
  fail "스테이징 Nginx가 Gateway 인증서를 마운트해야 함"
printf '%s\n' "$staging_config" |
  grep -Fq 'target: /etc/letsencrypt' ||
  fail "Gateway 인증서를 Nginx 인증서 경로에 마운트해야 함"

if printf '%s\n' "$staging_config" | grep -Fq '/srv/docsa/infra/certbot'; then
  fail "스테이징 Compose가 기존 Docsa 인증서 경로를 참조하면 안 됨"
fi

[ ! -e "$INFRA_DIR/nginx/nginx.conf" ] || fail "기존 운영 Nginx 설정이 없어야 함"
[ ! -e "$INFRA_DIR/scripts/cert_renew.sh" ] || fail "기존 Docsa 인증서 갱신 스크립트가 없어야 함"

echo "공용 Gateway 계약 통과"
