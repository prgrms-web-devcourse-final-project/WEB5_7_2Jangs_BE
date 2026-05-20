#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

COMPOSE_FILE="${BACKFILL_COMPOSE_FILE:-"$ROOT_DIR/infra/docker-compose.stg.yml"}"
SERVICE="${BACKFILL_SERVICE:-app}"
PROFILES="${BACKFILL_PROFILES:-stg,backfill}"
BATCH_SIZE="${DOC_LIST_READ_MODEL_BACKFILL_BATCH_SIZE:-1000}"
DDL_AUTO="${BACKFILL_DDL_AUTO:-validate}"

echo "[backfill] composeFile=$COMPOSE_FILE service=$SERVICE profiles=$PROFILES batchSize=$BATCH_SIZE ddlAuto=$DDL_AUTO"

docker compose -f "$COMPOSE_FILE" run --rm \
  -e SPRING_PROFILES_ACTIVE="$PROFILES" \
  -e SPRING_JPA_HIBERNATE_DDL_AUTO="$DDL_AUTO" \
  -e DDL_AUTO="$DDL_AUTO" \
  -e DOC_LIST_READ_MODEL_BACKFILL_BATCH_SIZE="$BATCH_SIZE" \
  -e MONGO_LOCAL_CLEANUP_ENABLED=false \
  -e PERF_SEED_USER_COUNT=0 \
  "$SERVICE"
