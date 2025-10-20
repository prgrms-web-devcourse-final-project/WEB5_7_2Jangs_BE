#!/usr/bin/env bash
set -euo pipefail

# 사용법:
#   /srv/docsa/infra/deploy.sh dev
#   /srv/docsa/infra/deploy.sh staging
# 환경변수(선택):
#   DEPLOY_TAG       : 덮어쓸 이미지 태그(dev, dev-<sha>, staging, staging-<sha> 등)
#   IMAGE_BASE       : GHCR 이미지 경로 (기본: ghcr.io/prgrms-web-devcourse-final-project/docsa-backend)
#   SERVICE          : 배포할 서비스명 (기본 app)
#   HEALTH_TIMEOUT   : 헬스 대기시간 초 (기본 120)

TARGET="${1:-}"                             # ← dev 또는 staging 인자 필수
if [[ "$TARGET" != "dev" && "$TARGET" != "staging" ]]; then
  echo "Usage: $0 <dev|staging>"
  exit 2
fi

IMAGE_BASE="${IMAGE_BASE:-ghcr.io/prgrms-web-devcourse-final-project/docsa-backend}"
SERVICE="${SERVICE:-app}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-120}"

cd "$(dirname "$0")"
ROOT="$(pwd)"

# 타깃에 따라 compose/env 자동 선택
if [[ "$TARGET" == "dev" ]]; then
  COMPOSE_FILE="$ROOT/compose.dev.yml"
  ENV_FILE="$ROOT/.env"
else
  COMPOSE_FILE="$ROOT/compose.stg.yml"
  ENV_FILE="$ROOT/.stg.env"
fi

TAG="${DEPLOY_TAG:-$TARGET}"               # ← 기본은 채널 태그(dev|staging), 입력 있으면 우선

# 임시 override 파일로 이미지 태그만 덮어쓰기
OVR=""
if [[ -n "$TAG" ]]; then
  OVR="$ROOT/infra/docker-compose.override.deploy.yml"
  cat > "$OVR" <<EOF
services:
  ${SERVICE}:
    image: ${IMAGE_BASE}:${TAG}
EOF
fi

# docker compose 인자 구성 (-p로 프로젝트 격리, --env-file로 env 명시)
ARGS=(-f "$COMPOSE_FILE")
[[ -n "$OVR" ]] && ARGS+=(-f "$OVR")
ARGS+=(--env-file "$ENV_FILE")

echo "[deploy] target=$TARGET tag=$TAG"
echo "[deploy] compose=$COMPOSE_FILE env=$ENV_FILE service=$SERVICE"

# 1) 유효성 검사(문법/치환 확인)
docker compose "${ARGS[@]}" config >/dev/null

# 2) 이미지 풀 + 대상 서비스만 업데이트
docker compose "${ARGS[@]}" pull "$SERVICE" || true
docker compose "${ARGS[@]}" up -d "$SERVICE"

# 3) 컨테이너 ID를 compose로 조회(이름 하드코딩 회피)
CID="$(docker compose "${ARGS[@]}" ps -q "$SERVICE" | tail -n1 || true)"
if [[ -z "$CID" ]]; then
  echo "No container found for service '$SERVICE' (project $PROJECT)"; exit 1
fi

# 4) 헬스체크 대기
echo -n "Waiting for $SERVICE to be healthy"
ok=0
ITER=$(( HEALTH_TIMEOUT / 2 ))
for _ in $(seq 1 "$ITER"); do
  status="$(docker inspect -f '{{.State.Health.Status}}' "$CID" 2>/dev/null || echo none)"
  if [[ "$status" == "healthy" ]]; then ok=1; echo -e "\n$SERVICE healthy"; break; fi
  sleep 2; echo -n "."
done

# 5) 임시 override 청소 + 이미지 정리
[[ -n "$OVR" ]] && rm -f "$OVR" || true
docker image prune -f >/dev/null 2>&1 || true

# 6) 실패 시 로그 출력 후 종료
if [[ $ok -eq 0 ]]; then
  echo -e "\n$SERVICE failed to become healthy (status=${status:-unknown})"
  docker logs --tail=200 "$CID" || true
  exit 1
fi