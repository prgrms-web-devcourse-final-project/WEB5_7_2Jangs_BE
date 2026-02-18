#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

out="$(docker compose run --rm --no-deps certbot_renew \
  renew --webroot -w /var/www/certbot)"

echo "$out"

# "갱신됨"일 때만 reload
if echo "$out" | grep -Eqi "successfully renewed|Congratulations|renewed"; then
  docker kill -s HUP docsa-nginx 2>/dev/null || true
  docker kill -s HUP docsa-nginx-stg 2>/dev/null || true
fi
