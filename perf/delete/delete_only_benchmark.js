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
const RUN_ID = __ENV.RUN_ID;

if (!RUN_ID) {
  throw new Error('RUN_ID is required. Use the same RUN_ID that was used in seed_dataset.js');
}

const TOTAL_DOCS = USER_COUNT * DOCS_PER_USER;

export const options = {
  scenarios: {
    delete_only: {
      executor: 'shared-iterations',
      vus: Number(__ENV.DELETE_VUS || 30),
      iterations: TOTAL_DOCS,
      maxDuration: __ENV.DELETE_MAX_DURATION || '20m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    delete_failed: ['rate<0.02'],
    op_doc_delete_ms: ['p(95)<3000'],
  },
};

const deleteFailed = new Rate('delete_failed');
const tLogin = new Trend('op_login_ms');
const tSearch = new Trend('op_doc_search_ms');
const tDelete = new Trend('op_doc_delete_ms');

function pad3(n) {
  return String(n).padStart(3, '0');
}

function userEmail(userNo) {
  return `${USER_PREFIX}_u${pad3(userNo)}@${USER_DOMAIN}`;
}

function headers(cookie) {
  return {
    headers: {
      'Content-Type': 'application/json',
      Cookie: cookie,
    },
  };
}

function ensureStatus(res, statuses, op) {
  const ok = check(res, {
    [`${op} status`]: (r) => statuses.includes(r.status),
  });
  deleteFailed.add(!ok, { op });
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
  const res = http.post(
    `${BASE_URL}/api/user/login`,
    JSON.stringify({ email, password: USER_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { op: 'delete_login' } },
  );
  tLogin.add(res.timings.duration);
  ensureStatus(res, [200], 'delete_login');

  const jsession = res.cookies.JSESSIONID && res.cookies.JSESSIONID[0];
  if (!jsession || !jsession.value) {
    throw new Error('delete_login missing JSESSIONID');
  }
  return `JSESSIONID=${jsession.value}`;
}

function findDocIdByTitle(cookie, title) {
  const url = `${BASE_URL}/api/document/search?keyword=${encodeURIComponent(title)}&page=0&size=10&sort=updatedAt&order=desc`;
  const res = http.get(url, { ...headers(cookie), tags: { op: 'delete_doc_search' } });
  tSearch.add(res.timings.duration);
  ensureStatus(res, [200], 'delete_doc_search');

  const body = parseJson(res, 'delete_doc_search');
  const content = Array.isArray(body.content) ? body.content : [];
  const exact = content.find((d) => d && d.title === title);
  if (!exact || !exact.id) {
    throw new Error(`target doc not found by title=${title}`);
  }
  return exact.id;
}

function deleteDoc(cookie, docId) {
  const res = http.del(
    `${BASE_URL}/api/document/${docId}`,
    null,
    { ...headers(cookie), tags: { op: 'delete_doc' } },
  );
  tDelete.add(res.timings.duration);
  ensureStatus(res, [204], 'delete_doc');
}

export default function () {
  const it = exec.scenario.iterationInTest;
  const userNo = Math.floor(it / DOCS_PER_USER) + 1;
  const docNo = (it % DOCS_PER_USER) + 1;

  const email = userEmail(userNo);
  const cookie = login(email);

  const key = `${RUN_ID}u${pad3(userNo)}d${pad3(docNo)}`;
  const title = `PDEL-${key}`;

  const docId = findDocIdByTitle(cookie, title);
  deleteDoc(cookie, docId);
}

export function handleSummary(data) {
  return {
    stdout: `\n[delete-only] run_id=${RUN_ID}, users=${USER_COUNT}, docs_per_user=${DOCS_PER_USER}, total_docs=${TOTAL_DOCS}\n`,
    'perf/delete/results/delete_only_summary.json': JSON.stringify(data, null, 2),
  };
}
