#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
RUNNER="$SCRIPT_DIR/run_commit_cache_matrix.sh"
COMPOSE_FILE="$REPO_ROOT/infra/docker-compose.local.yml"
REAL_DOCKER="$(command -v docker || true)"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/commit-cache-runner-test.XXXXXX")"

cleanup() {
  rm -rf "$TMP_ROOT"
}
trap cleanup EXIT

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_contains() {
  local file="$1"
  local expected="$2"
  grep -F -- "$expected" "$file" >/dev/null || fail "$file 에 '$expected'가 없습니다"
}

assert_not_contains() {
  local file="$1"
  local unexpected="$2"
  if grep -Fi -- "$unexpected" "$file" >/dev/null; then
    fail "$file 에 금지 문자열 '$unexpected'가 있습니다"
  fi
}

assert_count() {
  local file="$1"
  local expected="$2"
  local pattern="$3"
  local actual
  actual="$(grep -Fc -- "$pattern" "$file" || true)"
  [[ "$actual" == "$expected" ]] || fail "$file 의 '$pattern' 횟수: expected=$expected actual=$actual"
}

make_stubs() {
  local stub_dir="$1"
  mkdir -p "$stub_dir"

  cat >"$stub_dir/docker" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
printf 'docker %s\n' "$*" >>"$COMMAND_LOG"
if [[ " $* " == *" up -d --force-recreate --no-deps app "* ]]; then
  printf 'caffeine_reset\n' >>"$ORDER_LOG"
fi
if [[ " $* " == *" --profile commit-cache stop redis "* && "${FAIL_REDIS_STOP:-0}" == "1" ]]; then
  exit 17
fi
if [[ " $* " == *" compose "*" ps -q redis "* ]]; then
  if [[ "${REDIS_EXISTS:-0}" == "1" ]]; then echo redis-container-id; fi
elif [[ " $* " == *" inspect "* ]]; then
  echo healthy
elif [[ " $* " == *" exec "*" --scan "* ]]; then
  if [[ "${FAIL_REDIS_SCAN:-0}" == "1" ]]; then exit 9; fi
  printf '%s\n' "${COMMIT_CONTENT_CACHE_KEY_PREFIX}key-a" "${COMMIT_CONTENT_CACHE_KEY_PREFIX}key-b"
elif [[ " $* " == *" exec "*" INFO memory "* ]]; then
  if [[ -f "$GATE_STATE/released" ]]; then printf 'snapshot_after_redis\n' >>"$ORDER_LOG"; fi
  printf 'used_memory:1024\r\nmaxmemory:0\r\n'
elif [[ " $* " == *" stats "* ]]; then
  if [[ -f "$GATE_STATE/released" ]]; then printf 'snapshot_after_container\n' >>"$ORDER_LOG"; fi
  echo 'docsa-app-local 0.10% 256MiB / 1GiB'
fi
STUB

  cat >"$stub_dir/curl" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
printf 'curl %s\n' "$*" >>"$COMMAND_LOG"
if [[ " $* " == *"/gate/health"* ]]; then
  echo '{"status":"UP"}'
elif [[ " $* " == *"/gate/status"* ]]; then
  if [[ -f "$GATE_STATE/setup-complete" ]]; then echo '{"setupComplete":true}'; else echo '{"setupComplete":false}'; fi
elif [[ " $* " == *"/gate/release"* ]]; then
  printf 'gate_release\n' >>"$ORDER_LOG"
  touch "$GATE_STATE/released"
  echo '{"released":true}'
elif [[ " $* " == *"/actuator/prometheus"* ]]; then
  if [[ -f "$GATE_STATE/released" ]]; then
    printf 'snapshot_after_prometheus\n' >>"$ORDER_LOG"
    if [[ "${FAIL_AFTER_PROMETHEUS:-0}" == "1" ]]; then exit 22; fi
  else
    printf 'snapshot_before_prometheus\n' >>"$ORDER_LOG"
  fi
  cat <<'METRICS'
commit_content_cache_get_total{result="hit"} 1
commit_content_cache_get_total{result="miss"} 0
commit_content_cache_get_total{result="error"} 0
commit_content_cache_put_total{result="success"} 0
commit_content_cache_put_total{result="error"} 0
commit_content_cache_eviction_total{result="success"} 0
commit_content_cache_eviction_total{result="error"} 0
jvm_memory_used_bytes{area="heap"} 1024
jvm_gc_pause_seconds_count 0
commit_content_assemble_seconds_count 1
http_server_requests_seconds_count{uri="/api/document/{docId}/commit/{commitId}"} 1
process_cpu_usage 0.1
METRICS
else
  echo '{"status":"UP"}'
fi
STUB

  cat >"$stub_dir/k6" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
printf 'k6 scenario=%s result_root=%s provider=%s blocks=%s run=%s\n' \
  "$SCENARIO" "$RESULT_ROOT" "$PROVIDER" "$BLOCKS_PER_COMMIT" "$RUN_NUMBER" >>"$COMMAND_LOG"
if [[ -n "${MEASUREMENT_GATE_URL:-}" ]]; then
  printf 'k6 gate_url=%s\n' "$MEASUREMENT_GATE_URL" >>"$COMMAND_LOG"
fi
if [[ -n "${MEASUREMENT_GATE_URL:-}" ]]; then
  printf 'main_setup_complete\n' >>"$ORDER_LOG"
  touch "$GATE_STATE/setup-complete"
  for _attempt in 1 2 3 4 5 6 7 8 9 10; do
    [[ -f "$GATE_STATE/released" ]] && break
    sleep 0.05
  done
  [[ -f "$GATE_STATE/released" ]] || exit 31
  printf 'main_scenario_started\n' >>"$ORDER_LOG"
fi
output_dir="$RESULT_ROOT/$PROVIDER/blocks-$BLOCKS_PER_COMMIT/$SCENARIO/$LOAD_PROFILE/run-$RUN_NUMBER"
mkdir -p "$output_dir"
printf '{"scenario":"%s"}\n' "$SCENARIO" >"$output_dir/summary.json"
if [[ "$SCENARIO" == verify ]]; then printf 'verify_complete\n' >>"$ORDER_LOG"; fi
if [[ -n "${MEASUREMENT_GATE_URL:-}" && -n "${K6_MAIN_EXIT:-}" ]]; then exit "$K6_MAIN_EXIT"; fi
STUB

cat >"$stub_dir/node" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
printf 'node %s\n' "$*" >>"$COMMAND_LOG"
if [[ "${NODE_EXIT_IMMEDIATELY:-0}" == "1" ]]; then exit 42; fi
ready_file=''
while (($#)); do
  if [[ "$1" == '--ready-file' ]]; then
    ready_file="$2"
    shift 2
  else
    shift
  fi
done
[[ -n "$ready_file" ]] || exit 43
printf '%s\n' "${GATE_STUB_PORT:-32123}" >"$ready_file"
trap 'exit 0' TERM INT
while :; do sleep 1; done
STUB

  cat >"$stub_dir/dirname" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
printf 'dirname %s\n' "$*" >>"$COMMAND_LOG"
/usr/bin/dirname "$@"
STUB

  cat >"$stub_dir/git" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
printf 'git %s\n' "$*" >>"$COMMAND_LOG"
echo '0123456789abcdef0123456789abcdef01234567'
STUB

  chmod +x "$stub_dir/docker" "$stub_dir/curl" "$stub_dir/k6" "$stub_dir/git" "$stub_dir/node" "$stub_dir/dirname"
}

run_runner() {
  local case_dir="$1"
  shift
  mkdir -p "$case_dir/results" "$case_dir/stubs"
  mkdir -p "$case_dir/gate"
  : >"$case_dir/commands.log"
  : >"$case_dir/order.log"
  make_stubs "$case_dir/stubs"
  env -i \
    PATH="$case_dir/stubs:/usr/bin:/bin" \
    COMMAND_LOG="$case_dir/commands.log" \
    ORDER_LOG="$case_dir/order.log" \
    GATE_STATE="$case_dir/gate" \
    RESULT_ROOT="$case_dir/results" \
    RUN_ID=runner-test \
    USER_PASSWORD='runner-secret-password' \
    HEALTH_RETRIES=3 HEALTH_INTERVAL_SECONDS=0 \
    MEASUREMENT_GATE_RETRIES=200 MEASUREMENT_GATE_INTERVAL_SECONDS=0.01 \
    "$@" \
    bash "$RUNNER"
}

test_required_input_fails_before_external_command() {
  local case_dir="$TMP_ROOT/missing"
  mkdir -p "$case_dir/stubs"
  : >"$case_dir/commands.log"
  make_stubs "$case_dir/stubs"
  if env -i PATH="$case_dir/stubs:/usr/bin:/bin" COMMAND_LOG="$case_dir/commands.log" /bin/bash "$RUNNER" >/dev/null 2>&1; then
    fail '필수 입력 누락을 허용했습니다'
  fi
  [[ ! -s "$case_dir/commands.log" ]] || fail '필수 입력 검증 전에 외부 명령을 실행했습니다'
}

test_invalid_provider_and_run_are_rejected() {
  local case_dir="$TMP_ROOT/invalid"
  if run_runner "$case_dir" PROVIDER=memory BLOCKS_PER_COMMIT=100 PATTERN=cold RUN_NO=1 BASE_URL=http://localhost:8080 >/dev/null 2>&1; then
    fail '허용하지 않은 provider를 허용했습니다'
  fi
  [[ ! -s "$case_dir/commands.log" ]] || fail 'provider 검증 전에 외부 명령을 실행했습니다'

  if run_runner "$case_dir" PROVIDER=none BLOCKS_PER_COMMIT=100 PATTERN=cold RUN_NO=4 BASE_URL=http://localhost:8080 >/dev/null 2>&1; then
    fail '허용하지 않은 run 번호를 허용했습니다'
  fi
  [[ ! -s "$case_dir/commands.log" ]] || fail 'run 번호 검증 전에 외부 명령을 실행했습니다'
}

test_snapshot_fields_reject_embedded_credentials() {
  local case_dir="$TMP_ROOT/unsafe-snapshot"
  if run_runner "$case_dir" PROVIDER=none BLOCKS_PER_COMMIT=100 PATTERN=cold RUN_NO=1 \
      BASE_URL=http://localhost:8080 ACTUATOR_HEALTH_URL=http://user:secret@localhost:9091/actuator/health >/dev/null 2>&1; then
    fail '인증 정보가 포함된 actuator URL을 허용했습니다'
  fi
  [[ ! -s "$case_dir/commands.log" ]] || fail 'actuator URL 검증 전에 외부 명령을 실행했습니다'
}

test_none_and_caffeine_do_not_start_redis_profile() {
  local provider case_dir
  for provider in none caffeine; do
    case_dir="$TMP_ROOT/$provider"
    run_runner "$case_dir" PROVIDER="$provider" BLOCKS_PER_COMMIT=100 PATTERN=cold RUN_NO=1 BASE_URL=http://localhost:8080 >/dev/null
    assert_not_contains "$case_dir/commands.log" '--profile commit-cache up -d redis'
    assert_contains "$case_dir/commands.log" 'up -d --build --force-recreate app'
  done

  case_dir="$TMP_ROOT/redis-stop"
  run_runner "$case_dir" PROVIDER=none BLOCKS_PER_COMMIT=100 PATTERN=hot RUN_NO=1 \
    BASE_URL=http://localhost:8080 REDIS_EXISTS=1 >/dev/null
  assert_contains "$case_dir/commands.log" '--profile commit-cache stop redis'

  case_dir="$TMP_ROOT/redis-stop-failure"
  if run_runner "$case_dir" PROVIDER=caffeine BLOCKS_PER_COMMIT=100 PATTERN=hot RUN_NO=1 \
      BASE_URL=http://localhost:8080 REDIS_EXISTS=1 FAIL_REDIS_STOP=1 >/dev/null 2>&1; then
    fail 'Redis stop 실패를 무시했습니다'
  fi
}

test_redis_starts_profile_and_clears_only_prefix() {
  local case_dir="$TMP_ROOT/redis"
  run_runner "$case_dir" PROVIDER=redis BLOCKS_PER_COMMIT=100 PATTERN=cold RUN_NO=1 BASE_URL=http://localhost:8080 >/dev/null
  assert_contains "$case_dir/commands.log" '--profile commit-cache up -d redis'
  assert_contains "$case_dir/commands.log" '--scan --pattern docsa:experiment:commit-content:*'
  assert_contains "$case_dir/commands.log" 'UNLINK docsa:experiment:commit-content:key-a'
  assert_not_contains "$case_dir/commands.log" 'FLUSHDB'
  assert_not_contains "$case_dir/commands.log" 'FLUSHALL'
}

test_fixed_prefix_rejects_runtime_override() {
  local case_dir="$TMP_ROOT/fixed-prefix"
  run_runner "$case_dir" PROVIDER=redis BLOCKS_PER_COMMIT=100 PATTERN=hot RUN_NO=1 \
    BASE_URL=http://localhost:8080 COMMIT_CONTENT_CACHE_KEY_PREFIX=unsafe: >/dev/null
  assert_contains "$case_dir/commands.log" '--scan --pattern docsa:experiment:commit-content:*'
  assert_not_contains "$case_dir/commands.log" '--scan --pattern unsafe:*'
}

test_redis_scan_failure_stops_before_unlink() {
  local case_dir="$TMP_ROOT/scan-failure"
  if run_runner "$case_dir" PROVIDER=redis BLOCKS_PER_COMMIT=100 PATTERN=cold RUN_NO=1 \
      BASE_URL=http://localhost:8080 FAIL_REDIS_SCAN=1 >/dev/null 2>&1; then
    fail 'Redis SCAN 실패를 무시했습니다'
  fi
  assert_not_contains "$case_dir/commands.log" 'UNLINK '
}

test_stale_result_directory_is_rejected() {
  local case_dir="$TMP_ROOT/stale"
  mkdir -p "$case_dir/results/none/blocks-100/cold/vus-50/run-1"
  echo stale >"$case_dir/results/none/blocks-100/cold/vus-50/run-1/summary.json"
  if run_runner "$case_dir" PROVIDER=none BLOCKS_PER_COMMIT=100 PATTERN=cold RUN_NO=1 BASE_URL=http://localhost:8080 >/dev/null 2>&1; then
    fail '기존 결과 디렉터리를 재사용했습니다'
  fi
  [[ "$(cat "$case_dir/results/none/blocks-100/cold/vus-50/run-1/summary.json")" == stale ]] || fail '기존 summary를 변경했습니다'
}

test_load_profile_separates_hot_vu_results() {
  local case_dir="$TMP_ROOT/load-profile"
  run_runner "$case_dir" PROVIDER=none BLOCKS_PER_COMMIT=500 PATTERN=hot RUN_NO=1 \
    BASE_URL=http://localhost:8080 HOT_VUS=20 >/dev/null
  run_runner "$case_dir" PROVIDER=none BLOCKS_PER_COMMIT=500 PATTERN=hot RUN_NO=1 \
    BASE_URL=http://localhost:8080 HOT_VUS=50 >/dev/null

  [[ -f "$case_dir/results/none/blocks-500/hot/vus-20/run-1/summary.json" ]] \
    || fail 'Hot VU20 결과가 vus-20 경로에 없습니다'
  [[ -f "$case_dir/results/none/blocks-500/hot/vus-50/run-1/summary.json" ]] \
    || fail 'Hot VU50 결과가 vus-50 경로에 없습니다'
  [[ ! -d "$case_dir/results/none/blocks-500/hot/run-1" ]] \
    || fail 'load profile이 빠진 legacy 결과 디렉터리를 생성했습니다'
  assert_contains "$case_dir/results/none/blocks-500/hot/vus-50/run-1/environment.txt" 'load_profile=vus-50'
}

test_snapshots_do_not_persist_secret_values() {
  local case_dir="$TMP_ROOT/snapshots"
  local result_dir
  run_runner "$case_dir" PROVIDER=none BLOCKS_PER_COMMIT=500 PATTERN=cold RUN_NO=2 BASE_URL=http://localhost:8080 >/dev/null
  result_dir="$case_dir/results/none/blocks-500/cold/vus-50/run-2"
  for file in git-revision.txt environment.txt prometheus-before.txt container-stats-before.txt prometheus-after.txt container-stats-after.txt summary.json run.log; do
    [[ -f "$result_dir/$file" ]] || fail "$result_dir/$file 가 생성되지 않았습니다"
  done
  assert_not_contains "$result_dir/environment.txt" 'runner-secret-password'
  assert_not_contains "$result_dir/environment.txt" 'cookie'
  assert_not_contains "$result_dir/environment.txt" 'restart_command'
  assert_not_contains "$result_dir/prometheus-before.txt" 'process_cpu_usage'
  assert_contains "$result_dir/prometheus-before.txt" 'commit_content_assemble_seconds_count'
}

test_cold_has_no_warmup_and_hot_mixed_use_isolated_warmup() {
  local cold_dir="$TMP_ROOT/cold-order"
  local pattern warm_dir
  run_runner "$cold_dir" PROVIDER=none BLOCKS_PER_COMMIT=100 PATTERN=cold_burst RUN_NO=1 BASE_URL=http://localhost:8080 >/dev/null
  assert_count "$cold_dir/commands.log" 1 'k6 scenario=verify'
  assert_count "$cold_dir/commands.log" 1 'k6 scenario=cold_burst'

  for pattern in hot mixed; do
    warm_dir="$TMP_ROOT/$pattern-order"
    run_runner "$warm_dir" PROVIDER=caffeine BLOCKS_PER_COMMIT=100 PATTERN="$pattern" RUN_NO=1 BASE_URL=http://localhost:8080 >/dev/null
    assert_count "$warm_dir/commands.log" 1 'k6 scenario=verify'
    assert_count "$warm_dir/commands.log" 2 "k6 scenario=$pattern"
    assert_contains "$warm_dir/commands.log" 'commit-cache-warmup.'
    [[ -f "$warm_dir/results/caffeine/blocks-100/$pattern/vus-10/run-1/summary.json" ]] || fail '본 측정 summary가 없습니다'
  done
}

line_number() {
  local file="$1" pattern="$2"
  grep -nF -- "$pattern" "$file" | head -1 | cut -d: -f1
}

test_caffeine_cold_resets_after_verify_before_main_setup() {
  local case_dir="$TMP_ROOT/caffeine-reset"
  local verify_line reset_line setup_line
  run_runner "$case_dir" PROVIDER=caffeine BLOCKS_PER_COMMIT=100 PATTERN=cold RUN_NO=1 BASE_URL=http://localhost:8080 >/dev/null
  verify_line="$(line_number "$case_dir/order.log" 'verify_complete')"
  reset_line="$(line_number "$case_dir/order.log" 'caffeine_reset')"
  setup_line="$(line_number "$case_dir/order.log" 'main_setup_complete')"
  [[ -n "$verify_line" && -n "$reset_line" && -n "$setup_line" \
      && "$verify_line" -lt "$reset_line" && "$reset_line" -lt "$setup_line" ]] \
    || fail 'Caffeine reset 순서가 verify → reset → 본 setup이 아닙니다'
}

test_measurement_gate_orders_before_snapshot_and_scenario() {
  local case_dir="$TMP_ROOT/gate-order"
  local setup_line before_line release_line scenario_line
  run_runner "$case_dir" PROVIDER=none BLOCKS_PER_COMMIT=100 PATTERN=hot RUN_NO=1 BASE_URL=http://localhost:8080 >/dev/null
  setup_line="$(line_number "$case_dir/order.log" 'main_setup_complete')"
  before_line="$(line_number "$case_dir/order.log" 'snapshot_before_prometheus')"
  release_line="$(line_number "$case_dir/order.log" 'gate_release')"
  scenario_line="$(line_number "$case_dir/order.log" 'main_scenario_started')"
  [[ "$setup_line" -lt "$before_line" && "$before_line" -lt "$release_line" && "$release_line" -lt "$scenario_line" ]] \
    || fail "gate 순서가 잘못되었습니다: $(tr '\n' ' ' <"$case_dir/order.log")"
}

test_gate_uses_dynamic_port_and_own_ready_file() {
  local case_dir="$TMP_ROOT/gate-dynamic"
  local ready_file
  run_runner "$case_dir" PROVIDER=none BLOCKS_PER_COMMIT=100 PATTERN=hot RUN_NO=1 \
    BASE_URL=http://localhost:8080 GATE_STUB_PORT=32123 >/dev/null
  assert_contains "$case_dir/commands.log" 'measurement_gate.mjs --port 0 --ready-file '
  assert_contains "$case_dir/commands.log" 'http://127.0.0.1:32123/gate/health'
  assert_contains "$case_dir/commands.log" 'k6 gate_url=http://127.0.0.1:32123'
  ready_file="$(grep -F 'measurement_gate.mjs --port 0 --ready-file ' "$case_dir/commands.log" | sed -E 's/.*--ready-file ([^ ]+).*/\1/')"
  [[ "$ready_file" == *'/ready-port' ]] || fail "고유 ready file을 전달하지 않았습니다: $ready_file"
  [[ ! -e "$(dirname "$ready_file")" ]] || fail 'runner cleanup이 gate state directory를 남겼습니다'
}

test_gate_child_death_is_rejected_before_http_or_k6() {
  local case_dir="$TMP_ROOT/gate-child-death"
  if run_runner "$case_dir" PROVIDER=none BLOCKS_PER_COMMIT=100 PATTERN=hot RUN_NO=1 \
      BASE_URL=http://localhost:8080 NODE_EXIT_IMMEDIATELY=1 >/dev/null 2>&1; then
    fail 'gate child 조기 종료를 허용했습니다'
  fi
  assert_not_contains "$case_dir/commands.log" '/gate/health'
  assert_not_contains "$case_dir/commands.log" 'k6 gate_url='
}

test_after_snapshot_attempts_all_and_preserves_workload_failure() {
  local case_dir="$TMP_ROOT/after-failure"
  set +e
  run_runner "$case_dir" PROVIDER=redis BLOCKS_PER_COMMIT=100 PATTERN=hot RUN_NO=1 BASE_URL=http://localhost:8080 \
    FAIL_AFTER_PROMETHEUS=1 K6_MAIN_EXIT=23 >/dev/null 2>&1
  local status=$?
  set -e
  [[ "$status" == 23 ]] || fail "workload exit 23이 보존되지 않았습니다: actual=$status"
  assert_contains "$case_dir/order.log" 'snapshot_after_container'
  assert_contains "$case_dir/order.log" 'snapshot_after_redis'

  case_dir="$TMP_ROOT/after-only-failure"
  if run_runner "$case_dir" PROVIDER=none BLOCKS_PER_COMMIT=100 PATTERN=hot RUN_NO=1 BASE_URL=http://localhost:8080 \
      FAIL_AFTER_PROMETHEUS=1 >/dev/null 2>&1; then
    fail 'workload 성공 뒤 after snapshot 실패를 무시했습니다'
  fi
}

test_compose_config() {
  [[ -n "$REAL_DOCKER" ]] || fail 'docker compose config 검증에 docker가 필요합니다'
  local default_services="$TMP_ROOT/default-services.txt"
  local profile_services="$TMP_ROOT/profile-services.txt"
  local rendered="$TMP_ROOT/compose-rendered.txt"
  "$REAL_DOCKER" compose -f "$COMPOSE_FILE" config --services >"$default_services"
  "$REAL_DOCKER" compose -f "$COMPOSE_FILE" --profile commit-cache config --services >"$profile_services"
  "$REAL_DOCKER" compose -f "$COMPOSE_FILE" --profile commit-cache config >"$rendered"
  assert_contains "$default_services" 'app'
  assert_not_contains "$default_services" 'redis'
  assert_contains "$profile_services" 'app'
  assert_contains "$profile_services" 'redis'
  assert_contains "$rendered" 'container_name: docsa-app-local'
  assert_contains "$rendered" 'COMMIT_CONTENT_CACHE_PROVIDER: none'
  assert_contains "$rendered" 'MONGO_LOCAL_CLEANUP_ENABLED: "false"'
  assert_contains "$rendered" 'PERF_SEED_USER_COUNT: "0"'
  assert_contains "$rendered" 'SPRING_JPA_HIBERNATE_DDL_AUTO: update'
  assert_contains "$rendered" 'target: 9091'
}

test_required_input_fails_before_external_command
test_invalid_provider_and_run_are_rejected
test_snapshot_fields_reject_embedded_credentials
test_none_and_caffeine_do_not_start_redis_profile
test_redis_starts_profile_and_clears_only_prefix
test_fixed_prefix_rejects_runtime_override
test_redis_scan_failure_stops_before_unlink
test_stale_result_directory_is_rejected
test_load_profile_separates_hot_vu_results
test_snapshots_do_not_persist_secret_values
test_cold_has_no_warmup_and_hot_mixed_use_isolated_warmup
test_caffeine_cold_resets_after_verify_before_main_setup
test_measurement_gate_orders_before_snapshot_and_scenario
test_gate_uses_dynamic_port_and_own_ready_file
test_gate_child_death_is_rejected_before_http_or_k6
test_after_snapshot_attempts_all_and_preserves_workload_failure
test_compose_config

echo 'PASS: commit cache matrix runner'
