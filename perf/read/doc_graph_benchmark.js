import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const USER_PREFIX = __ENV.USER_PREFIX || 'perfuser';
const USER_DOMAIN = __ENV.USER_DOMAIN || 'test.com';
const USER_PASSWORD = __ENV.USER_PASSWORD || 'Testtest1';
const USER_COUNT = Number(__ENV.USER_COUNT || 50);
const DOCS_PER_USER = Number(__ENV.DOCS_PER_USER || 5);
const RUN_ID = __ENV.RUN_ID;
const RESULT_DIR = __ENV.RESULT_DIR || 'perf/read/results';
const TITLE_PREFIX = __ENV.TITLE_PREFIX || 'PDEL';
const DOC_LIST_PAGE_SIZE = Number(__ENV.DOC_LIST_PAGE_SIZE || Math.max(DOCS_PER_USER * 2, 20));
const DOC_LIST_MAX_PAGES = Number(__ENV.DOC_LIST_MAX_PAGES || 20);

if (!RUN_ID) {
  throw new Error('RUN_ID is required. Use the same RUN_ID that was used for the graph dataset seed');
}

export const options = {
  summaryTrendStats: ['min', 'med', 'avg', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    graph_read: {
      executor: 'constant-vus',
      vus: Number(__ENV.GRAPH_VUS || 10),
      duration: __ENV.GRAPH_DURATION || '30s',
      exec: 'runGraphRead',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    graph_failed: ['rate<0.02'],
  },
};

const graphFailed = new Rate('graph_failed');
const tLogin = new Trend('graph_login_ms');
const tGraph = new Trend('op_doc_graph_ms');
const tGraphPayload = new Trend('op_doc_graph_payload_bytes');
const tGraphCommitCount = new Trend('op_doc_graph_commit_count');
const tGraphEdgeCount = new Trend('op_doc_graph_edge_count');
const tGraphBranchCount = new Trend('op_doc_graph_branch_count');

const cookieCache = new Map();

function pad3(n) {
  return String(n).padStart(3, '0');
}

function userEmail(userNo) {
  return `${USER_PREFIX}_u${pad3(userNo)}@${USER_DOMAIN}`;
}

function expectedTitlePrefix(userNo) {
  if (TITLE_PREFIX === 'PDEL') {
    return `PDEL-${RUN_ID}u${pad3(userNo)}d`;
  }
  return `PERF-${RUN_ID}-u${pad3(userNo)}-d`;
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
  graphFailed.add(!ok, { op });
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

  http.cookieJar().clear(BASE_URL);
  const res = http.post(
    `${BASE_URL}/api/user/login`,
    JSON.stringify({ email, password: USER_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { op: 'graph_login' } },
  );
  tLogin.add(res.timings.duration);
  ensureStatus(res, [200], 'graph_login');

  const jsession = res.cookies.JSESSIONID && res.cookies.JSESSIONID[0];
  if (!jsession || !jsession.value) {
    throw new Error('graph_login missing JSESSIONID');
  }

  const cookie = `JSESSIONID=${jsession.value}`;
  cookieCache.set(email, cookie);
  return cookie;
}

function requestGraph(cookie, docId) {
  const res = http.get(
    `${BASE_URL}/api/document/${docId}/graph`,
    { ...headers(cookie), tags: { op: 'doc_graph' } },
  );
  tGraph.add(res.timings.duration);
  tGraphPayload.add(typeof res.body === 'string' ? res.body.length : 0);
  ensureStatus(res, [200], 'doc_graph');

  const body = parseJson(res, 'doc_graph');
  const valid = check(body, {
    'doc_graph has branches': (r) => Array.isArray(r.branches) && r.branches.length > 0,
    'doc_graph has commits': (r) => Array.isArray(r.commits),
    'doc_graph has edges': (r) => Array.isArray(r.edges),
  });
  graphFailed.add(!valid, { op: 'doc_graph_shape' });
  if (!valid) {
    throw new Error('doc_graph invalid graph response');
  }

  tGraphBranchCount.add(body.branches.length);
  tGraphCommitCount.add(body.commits.length);
  tGraphEdgeCount.add(body.edges.length);
}

function resolveDocsForUser(cookie, userNo) {
  const docs = [];
  const titlePrefix = expectedTitlePrefix(userNo);

  for (let page = 0; page < DOC_LIST_MAX_PAGES && docs.length < DOCS_PER_USER; page += 1) {
    const res = http.get(
      `${BASE_URL}/api/document?page=${page}&size=${DOC_LIST_PAGE_SIZE}&sort=updatedAt&order=desc`,
      { ...headers(cookie), tags: { op: 'graph_resolve_docs' } },
    );
    ensureStatus(res, [200], 'graph_resolve_docs');

    const body = parseJson(res, 'graph_resolve_docs');
    if (!body || !Array.isArray(body.content)) {
      throw new Error('graph_resolve_docs invalid page response');
    }

    for (const item of body.content) {
      if (item.title && item.title.startsWith(titlePrefix)) {
        docs.push({
          userNo,
          docId: item.id,
          title: item.title,
        });
      }
    }

    if (body.last === true || body.content.length === 0) {
      break;
    }
  }

  if (docs.length !== DOCS_PER_USER) {
    throw new Error(`graph_resolve_docs expected ${DOCS_PER_USER} docs for userNo=${userNo}, got ${docs.length}`);
  }

  return docs;
}

export function setup() {
  const docs = [];
  const cookies = {};
  for (let userNo = 1; userNo <= USER_COUNT; userNo += 1) {
    const cookie = login(userEmail(userNo));
    cookies[userNo] = cookie;
    docs.push(...resolveDocsForUser(cookie, userNo));
  }

  return { docs, cookies };
}

export function runGraphRead(data) {
  const index = exec.scenario.iterationInTest % data.docs.length;
  const doc = data.docs[index];
  const cookie = data.cookies[doc.userNo];
  if (!cookie) {
    throw new Error(`graph missing cookie for userNo=${doc.userNo}`);
  }
  requestGraph(cookie, doc.docId);
}

export function handleSummary(data) {
  return {
    stdout: `\n[doc-graph-benchmark] run_id=${RUN_ID}, users=${USER_COUNT}, docs_per_user=${DOCS_PER_USER}\n`,
    [`${RESULT_DIR}/doc_graph_benchmark_${RUN_ID}.json`]: JSON.stringify(data, null, 2),
  };
}
