#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-"$ROOT_DIR/infra/.local.env"}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "env file not found: $ENV_FILE" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

export SPRING_PROFILES_ACTIVE="${BACKFILL_PROFILES:-local,backfill}"
export DDL_AUTO="${BACKFILL_DDL_AUTO:-none}"
export MONGO_LOCAL_CLEANUP_ENABLED="false"
export PERF_SEED_USER_COUNT="0"
export DOC_LIST_READ_MODEL_BACKFILL_BATCH_SIZE="${DOC_LIST_READ_MODEL_BACKFILL_BATCH_SIZE:-100}"

bash "$ROOT_DIR/gradlew" bootRun
