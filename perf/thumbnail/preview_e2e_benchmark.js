import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const USER_PREFIX = __ENV.USER_PREFIX || 'perfe2e';
const USER_DOMAIN = __ENV.USER_DOMAIN || 'test.com';
const USER_PASSWORD = __ENV.USER_PASSWORD || 'Testtest1';
const USER_COUNT = Number(__ENV.USER_COUNT || 1);
const DOCS_PER_USER = Number(__ENV.DOCS_PER_USER || 20);
const RUN_ID = __ENV.RUN_ID || 'e2e_u1d20';
const E2E_VUS = Number(__ENV.E2E_VUS || 1);
const E2E_DURATION = __ENV.E2E_DURATION || '30s';
const RESULT_DIR = __ENV.RESULT_DIR || 'perf/thumbnail/results';

export const options = {
  summaryTrendStats: ['min', 'med', 'avg', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    preview_e2e: {
      executor: 'constant-vus',
      vus: E2E_VUS,
      duration: E2E_DURATION,
      exec: 'runPreviewE2E',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    preview_e2e_failed: ['rate<0.02'],
  },
};

const e2eFailed = new Rate('preview_e2e_failed');
const tLogin = new Trend('preview_login_ms');
const tSetupDocList = new Trend('preview_setup_doc_list_ms');
const tSetupGraph = new Trend('preview_setup_graph_ms');
const tSaveUpdate = new Trend('op_preview_save_update_ms');
const tDocList = new Trend('op_preview_doc_list_ms');
const tTotal = new Trend('op_preview_e2e_total_ms');

function pad3(n) {
  return String(n).padStart(3, '0');
}

function userEmail(userNo) {
  return `${USER_PREFIX}_u${pad3(userNo)}@${USER_DOMAIN}`;
}

function headers(cookie) {
  return {
    headers: {
      Cookie: cookie,
      'Content-Type': 'application/json',
    },
  };
}

function ensureStatus(res, statuses, op) {
  const ok = check(res, {
    [`${op} status`]: (r) => statuses.includes(r.status),
  });
  e2eFailed.add(!ok, { op });
  if (!ok) {
    const snippet = typeof res.body === 'string' ? res.body.slice(0, 300) : '';
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
  const loginJar = new http.CookieJar();
  const res = http.post(
    `${BASE_URL}/api/user/login`,
    JSON.stringify({ email, password: USER_PASSWORD }),
    {
      headers: { 'Content-Type': 'application/json' },
      jar: loginJar,
      tags: { op: 'preview_login', name: 'POST /api/user/login' },
    },
  );
  tLogin.add(res.timings.duration);
  ensureStatus(res, [200], 'preview_login');

  const jsession = res.cookies.JSESSIONID && res.cookies.JSESSIONID[0];
  if (!jsession || !jsession.value) {
    throw new Error('preview_login missing JSESSIONID');
  }
  return `JSESSIONID=${jsession.value}`;
}

function fetchDocs(cookie) {
  const res = http.get(
    `${BASE_URL}/api/document?page=0&size=${DOCS_PER_USER}&sort=updatedAt&order=desc`,
    { headers: { Cookie: cookie }, tags: { op: 'preview_setup_doc_list', name: 'GET /api/document' } },
  );
  tSetupDocList.add(res.timings.duration);
  ensureStatus(res, [200], 'preview_setup_doc_list');
  const body = parseJson(res, 'preview_setup_doc_list');
  if (!body || !Array.isArray(body.content) || body.content.length === 0) {
    throw new Error('preview_setup_doc_list empty content');
  }
  return body.content.map((doc) => ({ docId: doc.id }));
}

function attachSaveIds(cookie, docs) {
  return docs.map((doc) => {
    const res = http.get(
      `${BASE_URL}/api/document/${doc.docId}/graph`,
      { headers: { Cookie: cookie }, tags: { op: 'preview_setup_graph', name: 'GET /api/document/{docId}/graph' } },
    );
    tSetupGraph.add(res.timings.duration);
    ensureStatus(res, [200], 'preview_setup_graph');
    const graph = parseJson(res, 'preview_setup_graph');
    const branch = graph.branches && graph.branches.find((item) => item.saveId);
    if (!branch) {
      throw new Error(`preview_setup_graph missing saveId docId=${doc.docId}`);
    }
    return { docId: doc.docId, saveId: branch.saveId };
  });
}

function buildSaveContent(docId, iteration) {
  return [
    {
      id: `preview-e2e-${docId}-${iteration}-1`,
      type: 'paragraph',
      props: {},
      content: [
        {
          type: 'text',
          text: `preview e2e ${RUN_ID} doc ${docId} iteration ${iteration}`,
          styles: {},
        },
      ],
      children: [],
    },
  ];
}

function requestSaveUpdate(cookie, target, iteration) {
  const res = http.put(
    `${BASE_URL}/api/document/${target.docId}/save/${target.saveId}`,
    JSON.stringify({ content: buildSaveContent(target.docId, iteration) }),
    { ...headers(cookie), tags: { op: 'preview_save_update', name: 'PUT /api/document/{docId}/save/{saveId}' } },
  );
  tSaveUpdate.add(res.timings.duration);
  ensureStatus(res, [200], 'preview_save_update');
}

function requestDocList(cookie) {
  const res = http.get(
    `${BASE_URL}/api/document?page=0&size=${DOCS_PER_USER}&sort=updatedAt&order=desc`,
    { headers: { Cookie: cookie }, tags: { op: 'preview_doc_list', name: 'GET /api/document' } },
  );
  tDocList.add(res.timings.duration);
  ensureStatus(res, [200], 'preview_doc_list');
}

export function setup() {
  const users = [];
  for (let userNo = 1; userNo <= USER_COUNT; userNo += 1) {
    const cookie = login(userEmail(userNo));
    const docs = attachSaveIds(cookie, fetchDocs(cookie));
    users.push({ userNo, cookie, docs });
  }
  return { users };
}

export function runPreviewE2E(data) {
  const userIndex = (exec.vu.idInTest - 1) % data.users.length;
  const user = data.users[userIndex];
  const iteration = exec.scenario.iterationInTest;
  const target = user.docs[(iteration + exec.vu.idInTest - 1) % user.docs.length];
  const started = Date.now();

  requestSaveUpdate(user.cookie, target, iteration);
  requestDocList(user.cookie);

  tTotal.add(Date.now() - started);
}

export function handleSummary(data) {
  return {
    stdout: `\n[preview-e2e] run_id=${RUN_ID}, users=${USER_COUNT}, docs_per_user=${DOCS_PER_USER}, vus=${E2E_VUS}, duration=${E2E_DURATION}\n`,
    [`${RESULT_DIR}/preview_e2e_${RUN_ID}.json`]: JSON.stringify(data, null, 2),
  };
}
