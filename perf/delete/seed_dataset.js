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
const MAIN_COMMITS = Number(__ENV.MAIN_COMMITS || 6);
const FEATURE_COMMITS = Number(__ENV.FEATURE_COMMITS || 4);
const BLOCKS_PER_COMMIT = Number(__ENV.BLOCKS_PER_COMMIT || 20);
const RUN_ID = __ENV.RUN_ID || Math.floor(Date.now() / 1000).toString(36);

const TOTAL_DOCS = USER_COUNT * DOCS_PER_USER;

export const options = {
  scenarios: {
    seed_dataset: {
      executor: 'shared-iterations',
      vus: Number(__ENV.SEED_VUS || 20),
      iterations: TOTAL_DOCS,
      maxDuration: __ENV.SEED_MAX_DURATION || '30m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    seed_failed: ['rate<0.01'],
  },
};

const seedFailed = new Rate('seed_failed');
const tLogin = new Trend('seed_login_ms');
const tDocCreate = new Trend('seed_doc_create_ms');
const tCommitCreate = new Trend('seed_commit_create_ms');
const tBranchCreate = new Trend('seed_branch_create_ms');

function pad3(n) {
  return String(n).padStart(3, '0');
}

function userEmail(userNo) {
  return `${USER_PREFIX}_u${pad3(userNo)}@${USER_DOMAIN}`;
}

function baseHeaders(cookie) {
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
  seedFailed.add(!ok, { op });
  if (!ok) {
    const body = typeof res.body === 'string' ? res.body.slice(0, 240) : '';
    throw new Error(`${op} failed status=${res.status}, body=${body}`);
  }
}

function json(res, op) {
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
    { headers: { 'Content-Type': 'application/json' }, tags: { op: 'seed_login' } },
  );
  tLogin.add(res.timings.duration);
  ensureStatus(res, [200], 'seed_login');

  const jsession = res.cookies.JSESSIONID && res.cookies.JSESSIONID[0];
  if (!jsession || !jsession.value) {
    throw new Error('seed_login missing JSESSIONID');
  }
  return `JSESSIONID=${jsession.value}`;
}

function createDoc(cookie, title) {
  const res = http.post(
    `${BASE_URL}/api/document`,
    JSON.stringify({ title }),
    { ...baseHeaders(cookie), tags: { op: 'seed_doc_create' } },
  );
  tDocCreate.add(res.timings.duration);
  ensureStatus(res, [201], 'seed_doc_create');
  return json(res, 'seed_doc_create');
}

function getGraph(cookie, docId) {
  const res = http.get(
    `${BASE_URL}/api/document/${docId}/graph`,
    { ...baseHeaders(cookie), tags: { op: 'seed_graph' } },
  );
  ensureStatus(res, [200], 'seed_graph');
  return json(res, 'seed_graph');
}

function mainBranchId(graph) {
  if (!graph || !Array.isArray(graph.branches) || graph.branches.length === 0) {
    throw new Error('seed_graph no branches');
  }
  const main = graph.branches.find((b) => b.fromCommitId === null);
  return (main || graph.branches[0]).id;
}

function findBranchIdByName(graph, name) {
  if (!graph || !Array.isArray(graph.branches)) {
    return null;
  }
  const found = graph.branches.find((b) => b.name === name);
  return found ? found.id : null;
}

function buildCommitBody(title, branchId) {
  const blocks = [];
  const blockOrders = [];

  for (let i = 0; i < BLOCKS_PER_COMMIT; i += 1) {
    const blockId = `${title}-b${i}-${Math.floor(Math.random() * 1e6)}`;
    blocks.push({
      data: {
        id: blockId,
        type: 'paragraph',
        text: `${title}-text-${i}`,
      },
    });
    blockOrders.push(blockId);
  }

  return {
    title,
    description: `${title}-desc`,
    branchId,
    blocks,
    blockOrders,
  };
}

function createCommit(cookie, docId, branchId, title) {
  const body = buildCommitBody(title, branchId);
  const res = http.post(
    `${BASE_URL}/api/document/${docId}/commit`,
    JSON.stringify(body),
    { ...baseHeaders(cookie), tags: { op: 'seed_commit_create' } },
  );
  tCommitCreate.add(res.timings.duration);
  ensureStatus(res, [201], 'seed_commit_create');
  return json(res, 'seed_commit_create');
}

function createBranch(cookie, docId, name, fromCommitId) {
  const res = http.post(
    `${BASE_URL}/api/document/${docId}/branch`,
    JSON.stringify({ name, fromCommitId }),
    { ...baseHeaders(cookie), tags: { op: 'seed_branch_create' } },
  );
  tBranchCreate.add(res.timings.duration);
  ensureStatus(res, [201], 'seed_branch_create');
  return json(res, 'seed_branch_create');
}

export default function () {
  const it = exec.scenario.iterationInTest;
  const userNo = Math.floor(it / DOCS_PER_USER) + 1;
  const docNo = (it % DOCS_PER_USER) + 1;

  const email = userEmail(userNo);
  const cookie = login(email);

  const key = `${RUN_ID}u${pad3(userNo)}d${pad3(docNo)}`;
  const docTitle = `PDEL-${key}`;

  const doc = createDoc(cookie, docTitle);
  const graph0 = getGraph(cookie, doc.id);
  const mainId = mainBranchId(graph0);

  const mainCommits = [];
  for (let i = 0; i < MAIN_COMMITS; i += 1) {
    const title = `m${i}-${key}`.slice(0, 28);
    const commit = createCommit(cookie, doc.id, mainId, title);
    mainCommits.push(commit.id);
  }

  if (mainCommits.length < 2) {
    return;
  }

  const featureName = `feat-${key}`;
  const fromCommitId = mainCommits[0]; // non-leaf from commit

  const branchRes = createBranch(cookie, doc.id, featureName, fromCommitId);
  let featureBranchId = branchRes.branchId;

  if (!featureBranchId) {
    const graph1 = getGraph(cookie, doc.id);
    featureBranchId = findBranchIdByName(graph1, featureName);
    if (!featureBranchId) {
      throw new Error('seed_branch_create could not resolve feature branch id');
    }
  }

  for (let i = 0; i < FEATURE_COMMITS; i += 1) {
    const title = `f${i}-${key}`.slice(0, 28);
    createCommit(cookie, doc.id, featureBranchId, title);
  }
}

export function handleSummary(data) {
  return {
    stdout: `\n[seed-dataset] run_id=${RUN_ID}, users=${USER_COUNT}, docs_per_user=${DOCS_PER_USER}, total_docs=${TOTAL_DOCS}\n`,
    'perf/delete/results/seed_dataset_summary.json': JSON.stringify(data, null, 2),
  };
}
