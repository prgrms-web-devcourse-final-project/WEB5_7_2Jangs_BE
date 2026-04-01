import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const USER_PREFIX = __ENV.USER_PREFIX || 'perfdel';
const USER_DOMAIN = __ENV.USER_DOMAIN || 'test.com';
const USER_PASSWORD = __ENV.USER_PASSWORD || 'Testtest1';
const USER_COUNT = Number(__ENV.USER_COUNT || 50);
const DOCS_PER_USER = Number(__ENV.DOCS_PER_USER || 3);
const PAGE_SIZE = Number(__ENV.PAGE_SIZE || 10);
const RUN_ID = __ENV.RUN_ID;
const RESULT_DIR = __ENV.RESULT_DIR || 'perf/read/results';

if (!RUN_ID) {
  throw new Error('RUN_ID is required. Use the same RUN_ID that was used in perf/delete/seed_dataset.js');
}

export const options = {
  scenarios: {
    sidebar_list: {
      executor: 'constant-vus',
      vus: Number(__ENV.SIDEBAR_VUS || 10),
      duration: __ENV.SIDEBAR_DURATION || '30s',
      exec: 'runSidebarList',
    },
    full_list: {
      executor: 'constant-vus',
      vus: Number(__ENV.FULL_LIST_VUS || 10),
      duration: __ENV.FULL_LIST_DURATION || '30s',
      exec: 'runFullList',
      startTime: __ENV.FULL_LIST_START || '35s',
    },
    search_list: {
      executor: 'constant-vus',
      vus: Number(__ENV.SEARCH_VUS || 10),
      duration: __ENV.SEARCH_DURATION || '30s',
      exec: 'runSearchList',
      startTime: __ENV.SEARCH_START || '70s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    read_failed: ['rate<0.02'],
  },
};

const readFailed = new Rate('read_failed');
const tLogin = new Trend('read_login_ms');
const tSidebar = new Trend('op_doc_sidebar_ms');
const tFullList = new Trend('op_doc_list_ms');
const tSearch = new Trend('op_doc_search_ms');

const cookieCache = new Map();

function pad3(n) {
  return String(n).padStart(3, '0');
}

function userNoForIteration() {
  const total = Math.max(USER_COUNT, 1);
  return ((exec.scenario.iterationInTest % total) + 1);
}

function userEmail(userNo) {
  return `${USER_PREFIX}_u${pad3(userNo)}@${USER_DOMAIN}`;
}

function keywordForUser(userNo) {
  return `PDEL-${RUN_ID}u${pad3(userNo)}`;
}

function headers(cookie) {
  return {
    headers: {
      Cookie: cookie,
    },
  };
}

function ensureStatus(res, statuses, op) {
  const ok = check(res, {
    [`${op} status`]: (r) => statuses.includes(r.status),
  });
  readFailed.add(!ok, { op });
  if (!ok) {
    const snippet = typeof res.body === 'string' ? res.body.slice(0, 240) : '';
    throw new Error(`${op} failed status=${res.status}, body=${snippet}`);
  }
}

function parseJson(res, op) {
  try {
    return res.json();
  } catch (_e) {
    throw new Error(`${op} invalid JSON`);
  }
}

function login(email) {
  const cached = cookieCache.get(email);
  if (cached) {
    return cached;
  }

  const res = http.post(
    `${BASE_URL}/api/user/login`,
    JSON.stringify({ email, password: USER_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { op: 'read_login' } },
  );
  tLogin.add(res.timings.duration);
  ensureStatus(res, [200], 'read_login');

  const jsession = res.cookies.JSESSIONID && res.cookies.JSESSIONID[0];
  if (!jsession || !jsession.value) {
    throw new Error('read_login missing JSESSIONID');
  }

  const cookie = `JSESSIONID=${jsession.value}`;
  cookieCache.set(email, cookie);
  return cookie;
}

function assertPage(body, op) {
  if (!body || !Array.isArray(body.content)) {
    throw new Error(`${op} invalid page response`);
  }
}

function requestSidebar(cookie) {
  const res = http.get(
    `${BASE_URL}/api/document/sidebar?page=0&size=${PAGE_SIZE}&sort=updatedAt&order=desc`,
    { ...headers(cookie), tags: { op: 'doc_sidebar' } },
  );
  tSidebar.add(res.timings.duration);
  ensureStatus(res, [200], 'doc_sidebar');
  const body = parseJson(res, 'doc_sidebar');
  assertPage(body, 'doc_sidebar');
}

function requestFullList(cookie) {
  const res = http.get(
    `${BASE_URL}/api/document?page=0&size=${PAGE_SIZE}&sort=updatedAt&order=desc`,
    { ...headers(cookie), tags: { op: 'doc_list' } },
  );
  tFullList.add(res.timings.duration);
  ensureStatus(res, [200], 'doc_list');
  const body = parseJson(res, 'doc_list');
  assertPage(body, 'doc_list');
}

function requestSearch(cookie, keyword) {
  const res = http.get(
    `${BASE_URL}/api/document/search?keyword=${encodeURIComponent(keyword)}&page=0&size=${PAGE_SIZE}&sort=updatedAt&order=desc`,
    { ...headers(cookie), tags: { op: 'doc_search' } },
  );
  tSearch.add(res.timings.duration);
  ensureStatus(res, [200], 'doc_search');
  const body = parseJson(res, 'doc_search');
  assertPage(body, 'doc_search');
}

export function runSidebarList() {
  const userNo = userNoForIteration();
  const cookie = login(userEmail(userNo));
  requestSidebar(cookie);
}

export function runFullList() {
  const userNo = userNoForIteration();
  const cookie = login(userEmail(userNo));
  requestFullList(cookie);
}

export function runSearchList() {
  const userNo = userNoForIteration();
  const cookie = login(userEmail(userNo));
  requestSearch(cookie, keywordForUser(userNo));
}

export function handleSummary(data) {
  return {
    stdout: `\n[doc-list-benchmark] run_id=${RUN_ID}, users=${USER_COUNT}, docs_per_user=${DOCS_PER_USER}, page_size=${PAGE_SIZE}\n`,
    [`${RESULT_DIR}/doc_list_benchmark_${RUN_ID}.json`]: JSON.stringify(data, null, 2),
  };
}
