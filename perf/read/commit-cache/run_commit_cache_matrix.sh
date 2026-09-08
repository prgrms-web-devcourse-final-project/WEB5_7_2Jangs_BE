#!/usr/bin/env bash

set -euo pipefail

die() {
  echo "오류: $*" >&2
  exit 1
}

require_input() {
  local name="$1"
  [[ -n "${!name:-}" ]] || die "$name 환경변수가 필요합니다"
}

# 필수 입력 검증 전에는 외부 명령을 실행하지 않는다.
for required in PROVIDER BLOCKS_PER_COMMIT PATTERN RUN_NO BASE_URL RUN_ID; do
  require_input "$required"
done

case "$PROVIDER" in
  none|caffeine) ;;
  *) die "PROVIDER는 none, caffeine 중 하나여야 합니다" ;;
esac
case "$PATTERN" in
  cold|hot|mixed|cold_burst|saturation) ;;
  *) die "PATTERN은 cold, hot, mixed, cold_burst, saturation 중 하나여야 합니다" ;;
esac
case "$RUN_NO" in
  1|2|3|4|5) ;;
  *) die "RUN_NO는 1~5 중 하나여야 합니다" ;;
esac

[[ "$BLOCKS_PER_COMMIT" =~ ^[1-9][0-9]*$ ]] || die "BLOCKS_PER_COMMIT은 양의 정수여야 합니다"
[[ "$BASE_URL" =~ ^https?:// ]] || die "BASE_URL은 http 또는 https URL이어야 합니다"
[[ "$BASE_URL" != *"@"* && "$BASE_URL" != *"?"* && "$BASE_URL" != *"#"* ]] || die "BASE_URL에는 인증 정보나 query를 넣을 수 없습니다"
[[ "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ ]] || die "RUN_ID는 영문, 숫자, 점, 밑줄, 하이픈만 사용할 수 있습니다"

ACTUATOR_HEALTH_URL="${ACTUATOR_HEALTH_URL:-http://localhost:9091/actuator/health}"
HEALTH_RETRIES="${HEALTH_RETRIES:-60}"
HEALTH_INTERVAL_SECONDS="${HEALTH_INTERVAL_SECONDS:-2}"
WARMUP_DURATION="${WARMUP_DURATION:-10s}"
REBUILD_APP="${REBUILD_APP:-true}"
CANONICAL_REBENCHMARK="${CANONICAL_REBENCHMARK:-false}"
WARMUP_VUS="${WARMUP_VUS:-50}"
USER_COUNT="${USER_COUNT:-20}"
DOCS_PER_USER="${DOCS_PER_USER:-5}"
MAIN_COMMITS="${MAIN_COMMITS:-20}"
COMMIT_CONTENT_CACHE_MAXIMUM_SIZE="${COMMIT_CONTENT_CACHE_MAXIMUM_SIZE:-400}"
MEASUREMENT_GATE_RETRIES="${MEASUREMENT_GATE_RETRIES:-120}"
MEASUREMENT_GATE_INTERVAL_SECONDS="${MEASUREMENT_GATE_INTERVAL_SECONDS:-0.5}"

[[ "$ACTUATOR_HEALTH_URL" =~ ^https?:// ]] || die "ACTUATOR_HEALTH_URL은 http 또는 https URL이어야 합니다"
[[ "$ACTUATOR_HEALTH_URL" != *"@"* && "$ACTUATOR_HEALTH_URL" != *"?"* && "$ACTUATOR_HEALTH_URL" != *"#"* ]] \
  || die "ACTUATOR_HEALTH_URL에는 인증 정보나 query를 넣을 수 없습니다"
[[ "$HEALTH_RETRIES" =~ ^[1-9][0-9]*$ ]] || die "HEALTH_RETRIES는 양의 정수여야 합니다"
[[ "$HEALTH_INTERVAL_SECONDS" =~ ^[0-9]+$ ]] || die "HEALTH_INTERVAL_SECONDS는 0 이상의 정수여야 합니다"
[[ "$MEASUREMENT_GATE_RETRIES" =~ ^[1-9][0-9]*$ ]] || die "MEASUREMENT_GATE_RETRIES는 양의 정수여야 합니다"
[[ "$MEASUREMENT_GATE_INTERVAL_SECONDS" =~ ^(0|[0-9]+)(\.[0-9]+)?$ ]] \
  || die "MEASUREMENT_GATE_INTERVAL_SECONDS는 0 이상의 숫자여야 합니다"
[[ "$REBUILD_APP" == true || "$REBUILD_APP" == false ]] || die "REBUILD_APP은 true 또는 false여야 합니다"
[[ "$CANONICAL_REBENCHMARK" == true || "$CANONICAL_REBENCHMARK" == false ]] \
  || die "CANONICAL_REBENCHMARK는 true 또는 false여야 합니다"
for positive_integer in WARMUP_VUS USER_COUNT DOCS_PER_USER MAIN_COMMITS COMMIT_CONTENT_CACHE_MAXIMUM_SIZE; do
  [[ "${!positive_integer}" =~ ^[1-9][0-9]*$ ]] || die "$positive_integer 는 양의 정수여야 합니다"
done
TARGET_COUNT=$((USER_COUNT * DOCS_PER_USER * MAIN_COMMITS))
(( TARGET_COUNT > COMMIT_CONTENT_CACHE_MAXIMUM_SIZE )) \
  || die "전체 target 수는 캐시 maximum size보다 커야 합니다"

case "$PATTERN" in
  cold)
    LOAD_VALUE="${COLD_VUS:-50}"
    ;;
  hot)
    LOAD_VALUE="${HOT_VUS:-50}"
    ;;
  mixed)
    LOAD_VALUE="${MIXED_VUS:-50}"
    ;;
  cold_burst)
    LOAD_VALUE="${COLD_BURST_VUS:-10}"
    [[ "$LOAD_VALUE" == 10 || "$LOAD_VALUE" == 50 || "$LOAD_VALUE" == 100 ]] \
      || die "COLD_BURST_VUS는 10, 50, 100 중 하나여야 합니다"
    ;;
  saturation)
    LOAD_PROFILE='rate-25-50-100-200'
    ;;
esac
if [[ "$PATTERN" != saturation ]]; then
  [[ "$LOAD_VALUE" =~ ^[1-9][0-9]*$ ]] || die "시나리오 VU는 양의 정수여야 합니다"
  LOAD_PROFILE="vus-$LOAD_VALUE"
fi
[[ "$LOAD_PROFILE" =~ ^(vus-[1-9][0-9]*|rate-[1-9][0-9]*(-[1-9][0-9]*)+)$ ]] \
  || die "계산된 load profile이 안전하지 않습니다"
if [[ "$CANONICAL_REBENCHMARK" == true ]]; then
  [[ "$PATTERN" == hot || "$PATTERN" == mixed ]] \
    || die "canonical 재측정은 hot 또는 mixed만 허용합니다"
  [[ "$BLOCKS_PER_COMMIT" == 500 && "$USER_COUNT" == 20 && "$DOCS_PER_USER" == 5 \
      && "$MAIN_COMMITS" == 20 && "$COMMIT_CONTENT_CACHE_MAXIMUM_SIZE" == 400 \
      && "$WARMUP_VUS" == 50 && "$WARMUP_DURATION" == 10s && "$LOAD_VALUE" == 50 ]] \
    || die "canonical 재측정 조건이 변경되었습니다"
  if [[ "$PATTERN" == hot ]]; then
    [[ "${HOT_DURATION:-60s}" == 60s ]] || die "canonical Hot duration은 60s여야 합니다"
  else
    [[ "${MIXED_DURATION:-60s}" == 60s ]] || die "canonical Mixed duration은 60s여야 합니다"
  fi
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
COMPOSE_FILE="$REPO_ROOT/infra/docker-compose.local.yml"
WORKLOAD="$SCRIPT_DIR/commit_content_benchmark.js"
GATE_SCRIPT="$SCRIPT_DIR/measurement_gate.mjs"
ACTUATOR_PROMETHEUS_URL="${ACTUATOR_HEALTH_URL%/health}/prometheus"
RESULT_ROOT="${RESULT_ROOT:-$SCRIPT_DIR/results}"
RESULT_DIR="$RESULT_ROOT/$PROVIDER/blocks-$BLOCKS_PER_COMMIT/$PATTERN/$LOAD_PROFILE/run-$RUN_NO"
APP_CONTAINER_NAME="${APP_CONTAINER_NAME:-docsa-app-local}"
MEASUREMENT_GATE_URL=""

[[ ! -e "$RESULT_DIR" ]] || die "결과 디렉터리가 이미 존재합니다: $RESULT_DIR"
mkdir -p "$RESULT_DIR"
RUN_LOG="$RESULT_DIR/run.log"
: >"$RUN_LOG"

VERIFY_ROOT=""
WARMUP_ROOT=""
GATE_PID=""
WORKLOAD_PID=""
GATE_STATE_DIR=""
cleanup() {
  if [[ -n "$WORKLOAD_PID" ]]; then
    kill "$WORKLOAD_PID" 2>/dev/null || true
    wait "$WORKLOAD_PID" 2>/dev/null || true
  fi
  if [[ -n "$GATE_PID" ]]; then
    kill "$GATE_PID" 2>/dev/null || true
    wait "$GATE_PID" 2>/dev/null || true
  fi
  [[ -z "$GATE_STATE_DIR" ]] || rm -rf "$GATE_STATE_DIR"
  [[ -z "$VERIFY_ROOT" ]] || rm -rf "$VERIFY_ROOT"
  [[ -z "$WARMUP_ROOT" ]] || rm -rf "$WARMUP_ROOT"
}
trap cleanup EXIT

compose() {
  docker compose -f "$COMPOSE_FILE" "$@"
}

wait_for_app() {
  local attempt
  for ((attempt = 1; attempt <= HEALTH_RETRIES; attempt += 1)); do
    if curl -fsS "$ACTUATOR_HEALTH_URL" 2>/dev/null | grep -q '"status"[[:space:]]*:[[:space:]]*"UP"'; then
      return 0
    fi
    sleep "$HEALTH_INTERVAL_SECONDS"
  done
  die "애플리케이션이 제한 시간 안에 UP 상태가 되지 않았습니다"
}

snapshot_prometheus() {
  local destination="$1"
  curl -fsS "$ACTUATOR_PROMETHEUS_URL" 2>/dev/null \
    | awk '/^(commit_content_|cache_gets_|cache_evictions_|cache_size|cache_puts_|jvm_memory_|jvm_gc_pause_|http_server_requests_)/' \
    >"$destination"
}

snapshot_container() {
  local destination="$1"
  docker stats --no-stream \
    --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}} {{.NetIO}} {{.BlockIO}} {{.PIDs}}' \
    "$APP_CONTAINER_NAME" >"$destination" 2>/dev/null
}

run_workload() {
  local scenario="$1" output_root="$2" scenario_load_profile
  shift 2
  scenario_load_profile="$LOAD_PROFILE"
  if [[ "$scenario" == verify ]]; then
    scenario_load_profile='vus-1'
  elif [[ "$scenario" == warm_hot ]]; then
    scenario_load_profile="vus-$WARMUP_VUS"
  fi
  mkdir -p "$output_root/$PROVIDER/blocks-$BLOCKS_PER_COMMIT/$scenario/$scenario_load_profile/run-$RUN_NO"
  env \
    BASE_URL="$BASE_URL" RUN_ID="$RUN_ID" \
    USER_PREFIX="${USER_PREFIX:-perfuser}" USER_DOMAIN="${USER_DOMAIN:-test.com}" \
    USER_PASSWORD="${USER_PASSWORD:-Testtest1}" \
    USER_COUNT="$USER_COUNT" DOCS_PER_USER="$DOCS_PER_USER" MAIN_COMMITS="$MAIN_COMMITS" \
    COMMIT_CONTENT_CACHE_MAXIMUM_SIZE="$COMMIT_CONTENT_CACHE_MAXIMUM_SIZE" \
    BLOCKS_PER_COMMIT="$BLOCKS_PER_COMMIT" PROVIDER="$PROVIDER" RUN_NUMBER="$RUN_NO" \
    RESULT_ROOT="$output_root" SCENARIO="$scenario" LOAD_PROFILE="$scenario_load_profile" \
    "$@" k6 run "$WORKLOAD"
}

wait_for_gate_ready() {
  local attempt gate_port
  for ((attempt = 1; attempt <= MEASUREMENT_GATE_RETRIES; attempt += 1)); do
    if ! kill -0 "$GATE_PID" 2>/dev/null; then
      wait "$GATE_PID" 2>/dev/null || true
      GATE_PID=""
      die "measurement gate 자식이 ready 전에 종료되었습니다"
    fi
    if [[ -f "$GATE_READY_FILE" ]]; then
      IFS= read -r gate_port <"$GATE_READY_FILE" || die "measurement gate ready file을 읽지 못했습니다"
      [[ "$gate_port" =~ ^[1-9][0-9]*$ ]] && (( gate_port <= 65535 )) \
        || die "measurement gate가 잘못된 port를 기록했습니다"
      kill -0 "$GATE_PID" 2>/dev/null || die "measurement gate 자식이 ready 직후 종료되었습니다"
      MEASUREMENT_GATE_URL="http://127.0.0.1:$gate_port"
      curl -fsS "$MEASUREMENT_GATE_URL/gate/health" >/dev/null 2>&1 \
        && return 0
    fi
    sleep "$MEASUREMENT_GATE_INTERVAL_SECONDS"
  done
  die "measurement gate ready file을 제한 시간 안에 확인하지 못했습니다"
}

wait_for_setup_complete() {
  local attempt
  for ((attempt = 1; attempt <= MEASUREMENT_GATE_RETRIES; attempt += 1)); do
    if curl -fsS "$MEASUREMENT_GATE_URL/gate/status" 2>/dev/null \
        | grep -q '"setupComplete"[[:space:]]*:[[:space:]]*true'; then
      return 0
    fi
    if ! kill -0 "$WORKLOAD_PID" 2>/dev/null; then
      wait "$WORKLOAD_PID" || true
      WORKLOAD_PID=""
      die "본 k6가 setup 완료 전에 종료되었습니다"
    fi
    sleep "$MEASUREMENT_GATE_INTERVAL_SECONDS"
  done
  die "본 k6 setup 완료 신호를 제한 시간 안에 받지 못했습니다"
}

cd "$REPO_ROOT"
if [[ "$PROVIDER" == caffeine ]]; then
  export COMMIT_CONTENT_CACHE_ENABLED=true
else
  export COMMIT_CONTENT_CACHE_ENABLED=false
fi
export COMMIT_CONTENT_CACHE_MAXIMUM_SIZE

if [[ "$REBUILD_APP" == true ]]; then
  compose up -d --build --force-recreate app >>"$RUN_LOG" 2>&1
else
  compose up -d --force-recreate app >>"$RUN_LOG" 2>&1
fi
wait_for_app

git rev-parse HEAD >"$RESULT_DIR/git-revision.txt"
cat >"$RESULT_DIR/environment.txt" <<EOF
provider=$PROVIDER
blocks_per_commit=$BLOCKS_PER_COMMIT
pattern=$PATTERN
load_profile=$LOAD_PROFILE
run_no=$RUN_NO
base_url=$BASE_URL
actuator_health_url=$ACTUATOR_HEALTH_URL
run_id=$RUN_ID
user_count=$USER_COUNT
docs_per_user=$DOCS_PER_USER
main_commits=$MAIN_COMMITS
target_count=$TARGET_COUNT
cache_maximum_size=$COMMIT_CONTENT_CACHE_MAXIMUM_SIZE
cache_enabled=$COMMIT_CONTENT_CACHE_ENABLED
canonical_rebenchmark=$CANONICAL_REBENCHMARK
sensitive_values=not_recorded
EOF

VERIFY_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/commit-cache-verify.XXXXXX")"
run_workload verify "$VERIFY_ROOT" >>"$RUN_LOG" 2>&1

if [[ "$PROVIDER" == caffeine || "$PATTERN" == cold || "$PATTERN" == cold_burst ]]; then
  compose up -d --force-recreate --no-deps app >>"$RUN_LOG" 2>&1
  wait_for_app
fi
if [[ "$PATTERN" == hot || "$PATTERN" == mixed ]]; then
  WARMUP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/commit-cache-warmup.XXXXXX")"
  run_workload warm_hot "$WARMUP_ROOT" \
    WARMUP_DURATION="$WARMUP_DURATION" WARMUP_VUS="$WARMUP_VUS" \
    >>"$RUN_LOG" 2>&1
fi

GATE_STATE_DIR="$(mktemp -d "${TMPDIR:-/tmp}/commit-cache-gate.XXXXXX")"
GATE_READY_FILE="$GATE_STATE_DIR/ready-port"
node "$GATE_SCRIPT" --port 0 --ready-file "$GATE_READY_FILE" --lifetime-ms 600000 >>"$RUN_LOG" 2>&1 &
GATE_PID=$!
wait_for_gate_ready

run_workload "$PATTERN" "$RESULT_ROOT" \
  MEASUREMENT_GATE_URL="$MEASUREMENT_GATE_URL" \
  MEASUREMENT_GATE_RETRIES="$MEASUREMENT_GATE_RETRIES" \
  MEASUREMENT_GATE_INTERVAL_SECONDS="$MEASUREMENT_GATE_INTERVAL_SECONDS" \
  >>"$RUN_LOG" 2>&1 &
WORKLOAD_PID=$!
wait_for_setup_complete

snapshot_prometheus "$RESULT_DIR/prometheus-before.txt"
snapshot_container "$RESULT_DIR/container-stats-before.txt"

curl -fsS -X POST "$MEASUREMENT_GATE_URL/gate/release" >/dev/null \
  || die "measurement gate 해제에 실패했습니다"

set +e
wait "$WORKLOAD_PID"
workload_status=$?
set -e
WORKLOAD_PID=""
kill "$GATE_PID" 2>/dev/null || true
wait "$GATE_PID" 2>/dev/null || true
GATE_PID=""

snapshot_status=0
set +e
snapshot_prometheus "$RESULT_DIR/prometheus-after.txt" || snapshot_status=1
snapshot_container "$RESULT_DIR/container-stats-after.txt" || snapshot_status=1
set -e

if (( workload_status != 0 )); then
  echo "오류: k6 본 측정이 실패했습니다(exit=$workload_status)" >&2
  exit "$workload_status"
fi
if (( snapshot_status != 0 )); then
  die "본 측정은 성공했지만 after snapshot 일부가 실패했습니다"
fi
[[ -f "$RESULT_DIR/summary.json" ]] || die "k6 summary가 생성되지 않았습니다"
echo "완료: $RESULT_DIR"
