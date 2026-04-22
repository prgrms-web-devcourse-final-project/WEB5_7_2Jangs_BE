#!/usr/bin/env bash
set -euo pipefail

# Cleanup perf-created docs by API.
# Modes:
# - single: delete docs created by single-user benchmark (SDEL-*)
# - multi : delete docs created by multi-user benchmark (PDEL-*)
# - all   : both (default)

BASE_URL="${BASE_URL:-http://localhost:8080}"
SESSION_COOKIE_NAME="${SESSION_COOKIE_NAME:-JSESSIONID}"
INSECURE_TLS="${INSECURE_TLS:-0}"

MODE="${MODE:-all}" # single | multi | all
USER_PASSWORD="${USER_PASSWORD:-Testtest1}"
TEST_EMAIL="${TEST_EMAIL:-test@test.com}"

USER_PREFIX="${USER_PREFIX:-perfuser}"
USER_DOMAIN="${USER_DOMAIN:-test.com}"
USER_COUNT="${USER_COUNT:-100}"
SEARCH_PAGE_SIZE="${SEARCH_PAGE_SIZE:-100}"

if [[ "${MODE}" != "single" && "${MODE}" != "multi" && "${MODE}" != "all" ]]; then
  echo "MODE must be one of: single | multi | all" >&2
  exit 1
fi

if ! [[ "${USER_COUNT}" =~ ^[0-9]+$ ]] || [[ "${USER_COUNT}" -lt 0 ]]; then
  echo "USER_COUNT must be a non-negative integer" >&2
  exit 1
fi

if [[ "${INSECURE_TLS}" == "1" ]]; then
  CURL_BASE=(curl -k -sS)
else
  CURL_BASE=(curl -sS)
fi

pad3() {
  printf "%03d" "$1"
}

login_cookie() {
  local email="$1"
  local headers body status cookie
  headers="$(mktemp)"
  body="$(mktemp)"

  status="$(
    "${CURL_BASE[@]}" -o "${body}" -D "${headers}" -w '%{http_code}' \
      -H 'Content-Type: application/json' \
      -X POST "${BASE_URL}/api/user/login" \
      -d "{\"email\":\"${email}\",\"password\":\"${USER_PASSWORD}\"}"
  )"

  if [[ "${status}" != "200" ]]; then
    rm -f "${headers}" "${body}"
    return 1
  fi

  cookie="$(
    grep -i '^set-cookie:' "${headers}" \
      | tr -d '\r' \
      | sed -n "s/^set-cookie:[[:space:]]*${SESSION_COOKIE_NAME}=\\([^;]*\\).*/\\1/p" \
      | head -n 1
  )"
  rm -f "${headers}" "${body}"

  [[ -n "${cookie}" ]] || return 1
  printf '%s' "${cookie}"
}

search_doc_ids() {
  local cookie="$1"
  local keyword="$2"
  local body

  body="$(
    "${CURL_BASE[@]}" \
      -H "Cookie: ${SESSION_COOKIE_NAME}=${cookie}" \
      -H 'Content-Type: application/json' \
      "${BASE_URL}/api/document/search?keyword=${keyword}&page=0&size=${SEARCH_PAGE_SIZE}&sort=updatedAt&order=desc"
  )"

  printf '%s' "${body}" | node -e '
    const fs = require("fs");
    const raw = fs.readFileSync(0, "utf8");
    let json;
    try {
      json = JSON.parse(raw);
    } catch {
      process.exit(0);
    }
    const content = Array.isArray(json.content) ? json.content : [];
    for (const row of content) {
      if (row && row.id != null) console.log(row.id);
    }
  '
}

delete_doc() {
  local cookie="$1"
  local doc_id="$2"
  local status

  status="$(
    "${CURL_BASE[@]}" -o /dev/null -w '%{http_code}' \
      -H "Cookie: ${SESSION_COOKIE_NAME}=${cookie}" \
      -H 'Content-Type: application/json' \
      -X DELETE "${BASE_URL}/api/document/${doc_id}"
  )"
  [[ "${status}" == "204" ]]
}

build_users() {
  if [[ "${MODE}" == "single" || "${MODE}" == "all" ]]; then
    printf '%s\n' "${TEST_EMAIL}"
  fi

  if [[ "${MODE}" == "multi" || "${MODE}" == "all" ]]; then
    local i=1
    while [[ "${i}" -le "${USER_COUNT}" ]]; do
      printf "%s_u%s@%s\n" "${USER_PREFIX}" "$(pad3 "${i}")" "${USER_DOMAIN}"
      i=$((i + 1))
    done
  fi
}

build_keywords() {
  if [[ "${MODE}" == "single" || "${MODE}" == "all" ]]; then
    printf '%s\n' "SDEL-"
  fi

  if [[ "${MODE}" == "multi" || "${MODE}" == "all" ]]; then
    printf '%s\n' "PDEL-"
  fi
}

echo "[cleanup] BASE_URL=${BASE_URL} MODE=${MODE} INSECURE_TLS=${INSECURE_TLS}"

total_deleted=0
total_failed=0
users_processed=0

while IFS= read -r email; do
  [[ -z "${email}" ]] && continue

  cookie="$(login_cookie "${email}" || true)"
  if [[ -z "${cookie}" ]]; then
    echo "[cleanup][skip] login failed: ${email}"
    continue
  fi

  users_processed=$((users_processed + 1))

  while IFS= read -r keyword; do
    [[ -z "${keyword}" ]] && continue
    while true; do
      ids="$(search_doc_ids "${cookie}" "${keyword}" || true)"
      if [[ -z "${ids}" ]]; then
        break
      fi

      deleted_this_round=0
      while IFS= read -r id; do
        [[ -z "${id}" ]] && continue
        if delete_doc "${cookie}" "${id}"; then
          total_deleted=$((total_deleted + 1))
          deleted_this_round=$((deleted_this_round + 1))
        else
          total_failed=$((total_failed + 1))
          echo "[cleanup][warn] delete failed: user=${email}, docId=${id}"
        fi
      done <<< "${ids}"

      # Avoid infinite loop if all deletes fail in this page.
      if [[ "${deleted_this_round}" -eq 0 ]]; then
        break
      fi
    done
  done < <(build_keywords)
done < <(build_users | awk '!seen[$0]++')

echo "[cleanup] users processed=${users_processed}, docs deleted=${total_deleted}, failed=${total_failed}"
echo "[cleanup] Mongo cleanup is async via outbox worker."
