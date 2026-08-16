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

staging_services=$(docker compose -f "$INFRA_DIR/docker-compose.stg.yml" config --services)
for duplicated_service in cadvisor node_exporter prometheus loki promtail grafana; do
  if printf '%s\n' "$staging_services" | grep -Fxq "$duplicated_service"; then
    fail "스테이징 Compose에 중복 모니터링 서비스 $duplicated_service 가 남아 있음"
  fi
done

staging_exporter_config=$(printf '%s\n' "$staging_config" | awk '
  /^  mysqld-exporter:/ { in_exporter = 1; next }
  in_exporter && /^  [[:alnum:]_-]+:/ { exit }
  in_exporter { print }
')
printf '%s\n' "$staging_exporter_config" | grep -Fq 'docsa_monitoring_net' ||
  fail "스테이징 MySQL Exporter가 공용 모니터링 네트워크를 사용해야 함"

staging_mysql_config=$(printf '%s\n' "$staging_config" | awk '
  /^  mysql:/ { in_mysql = 1; next }
  in_mysql && /^  [[:alnum:]_-]+:/ { exit }
  in_mysql { print }
')
if printf '%s\n' "$staging_mysql_config" | grep -Fq 'docsa_monitoring_net'; then
  fail "스테이징 MySQL은 공용 모니터링 네트워크에 연결하면 안 됨"
fi

prod_config=$(docker compose -f "$INFRA_DIR/docker-compose.yml" config --no-interpolate)
printf '%s\n' "$prod_config" | grep -Fq 'docsa_monitoring_net' ||
  fail "운영 Prometheus가 공용 모니터링 네트워크를 사용해야 함"
printf '%s\n' "$staging_config" | grep -Fq 'docsa_monitoring_net' ||
  fail "스테이징 앱과 MySQL Exporter가 공용 모니터링 네트워크를 사용해야 함"

grep -Fq -- '--storage.tsdb.retention.time=15d' "$INFRA_DIR/docker-compose.yml" ||
  fail "Prometheus 보존 기간은 15일이어야 함"
grep -Fq 'retention_period: 168h' "$INFRA_DIR/loki/config.yml" ||
  fail "Loki 보존 기간은 7일이어야 함"

for target in docsa-app:9091 docsa-app-stg:9091 mysqld-exporter:9104 mysqld-exporter-stg:9104; do
  grep -Fq "$target" "$INFRA_DIR/prometheus/prometheus.yml" ||
    fail "Prometheus 수집 대상 $target 이 필요함"
done

grep -Fq 'regex: docsa|docsa-stg' "$INFRA_DIR/promtail/config.yml" ||
  fail "Promtail은 Docsa 운영과 스테이징 project만 수집해야 함"
grep -Fq './mysql/logs/prod:/var/log/mysql' "$INFRA_DIR/docker-compose.yml" ||
  fail "운영 MySQL slow log 경로를 분리해야 함"
grep -Fq './mysql/logs/staging:/var/log/mysql' "$INFRA_DIR/docker-compose.stg.yml" ||
  fail "스테이징 MySQL slow log 경로를 분리해야 함"
grep -Fq 'install -d -m 0777 "$SLOW_LOG_DIR"' "$INFRA_DIR/deploy.sh" ||
  fail "배포 전에 MySQL slow log 디렉터리를 쓰기 가능하게 준비해야 함"
grep -Fq 'up -d --force-recreate nginx' "$INFRA_DIR/deploy.sh" ||
  fail "스테이징 공개 헬스체크 전에 Nginx를 재생성해야 함"

grep -Fq 'return 302 https://api.docsa.o-r.kr$request_uri;' "$INFRA_DIR/nginx/nginx.stg.conf" ||
  fail "스테이징 Grafana 경로를 통합 Grafana로 redirect해야 함"

[ ! -e "$INFRA_DIR/nginx/nginx.conf" ] || fail "기존 운영 Nginx 설정이 없어야 함"
[ ! -e "$INFRA_DIR/scripts/cert_renew.sh" ] || fail "기존 Docsa 인증서 갱신 스크립트가 없어야 함"

echo "공용 Gateway 계약 통과"
