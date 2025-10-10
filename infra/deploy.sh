#!/usr/bin/env bash
set -euo pipefail

cd /srv/docsa/infra

# 특정 태그로 배포시 DEPLOY_TAG=...
IMAGE_BASE="ghcr.io/prgrms-web-devcourse-final-project/docsa-backend"
TAG="${DEPLOY_TAG:-}"      # 비어있으면 compose에 적힌 태그 사용
OVR=""
if [[ -n "$TAG" ]]; then
  OVR="docker-compose.override.deploy.yml"
  cat > "$OVR" <<EOF
services:
  app:
    image: ${IMAGE_BASE}:${TAG}
EOF
fi

FILES=(-f docker-compose.yml)
[[ -n "$OVR" ]] && FILES+=(-f "$OVR")

# 최신 이미지 받고 교체
docker compose "${FILES[@]}" pull app
docker compose "${FILES[@]}" up -d app

# 헬스체크 대기 (최대 120s)
echo -n "Waiting for app (docsa-app) to be healthy"
ok=0
for i in {1..60}; do
  status="$(docker inspect -f '{{.State.Health.Status}}' docsa-app 2>/dev/null || echo none)"
  if [[ "$status" == "healthy" ]]; then ok=1; echo -e "\nApp healthy"; break; fi
  sleep 2; echo -n "."
done

# 임시 override 제거 & 청소
[[ -n "$OVR" ]] && rm -f "$OVR"
docker image prune -f >/dev/null 2>&1 || true

# 실패 처리
if [[ $ok -eq 0 ]]; then
  echo -e "\nApp failed to become healthy (status=$status)"
  docker logs --tail=200 docsa-app || true
  exit 1
fi
