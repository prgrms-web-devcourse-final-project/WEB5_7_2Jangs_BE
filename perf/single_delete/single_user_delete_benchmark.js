import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TEST_EMAIL = __ENV.TEST_EMAIL || 'test@test.com';
const TEST_PASSWORD = __ENV.TEST_PASSWORD || 'Testtest1';

const TARGET = (__ENV.TARGET || 'doc').toLowerCase(); // doc | branch | commit
const TARGET_COUNT = Number(__ENV.TARGET_COUNT || 5);
const MAIN_COMMITS = Number(__ENV.MAIN_COMMITS || 6);
const FEATURE_COMMITS = Number(__ENV.FEATURE_COMMITS || 4);
const BLOCKS_PER_COMMIT = Number(__ENV.BLOCKS_PER_COMMIT || 300);

const RUN_ID = __ENV.RUN_ID || Math.floor(Date.now() / 1000).toString(36);
const RESULT_DIR = __ENV.RESULT_DIR || 'perf/single_delete/results';
const DELETE_P95_THRESHOLD_MS = Number(__ENV.DELETE_P95_THRESHOLD_MS || 15000);

if (!['doc', 'branch', 'commit'].includes(TARGET)) {
  throw new Error('TARGET must be one of: doc | branch | commit');
}

if (TARGET_COUNT < 1) {
  throw new Error('TARGET_COUNT must be >= 1');
}

const deleteMetricName = `op_${TARGET}_delete_ms`;

export const options = {
  setupTimeout: __ENV.SETUP_TIMEOUT || '45m',
  scenarios: {
    single_user_delete: {
      executor: 'shared-iterations',
      vus: 1, // single-user benchmark
      iterations: TARGET_COUNT,
      maxDuration: __ENV.DELETE_MAX_DURATION || '40m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    delete_failed: ['rate<0.02'],
    [deleteMetricName]: [`p(95)<${DELETE_P95_THRESHOLD_MS}`],
  },
};

const deleteFailed = new Rate('delete_failed');
const tLogin = new Trend('op_login_ms');
const tDelete = new Trend(deleteMetricName);
const tSeedDoc = new Trend('seed_doc_create_ms');
const tSeedCommit = new Trend('seed_commit_create_ms');
const tSeedBranch = new Trend('seed_branch_create_ms');

function pad3(n) {
  return String(n).padStart(3, '0');
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

function headers(cookie) {
  return {
    headers: {
      'Content-Type': 'application/json',
      Cookie: cookie,
    },
  };
}

function login(email) {
  const res = http.post(
    `${BASE_URL}/api/user/login`,
    JSON.stringify({ email, password: TEST_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { op: 'login' } },
  );
  tLogin.add(res.timings.duration);
  ensureStatus(res, [200], 'login');

  const jsession = res.cookies.JSESSIONID && res.cookies.JSESSIONID[0];
  if (!jsession || !jsession.value) {
    throw new Error('login missing JSESSIONID');
  }
  return `JSESSIONID=${jsession.value}`;
}

function createDoc(cookie, title) {
  const res = http.post(
    `${BASE_URL}/api/document`,
    JSON.stringify({ title }),
    { ...headers(cookie), tags: { op: 'seed_doc_create' } },
  );
  tSeedDoc.add(res.timings.duration);
  ensureStatus(res, [201], 'seed_doc_create');
  return parseJson(res, 'seed_doc_create');
}

function getGraph(cookie, docId) {
  const res = http.get(
    `${BASE_URL}/api/document/${docId}/graph`,
    { ...headers(cookie), tags: { op: 'seed_graph' } },
  );
  ensureStatus(res, [200], 'seed_graph');
  return parseJson(res, 'seed_graph');
}

function mainBranchId(graph) {
  const branches = Array.isArray(graph?.branches) ? graph.branches : [];
  if (branches.length === 0) {
    throw new Error('seed_graph no branches');
  }
  const main = branches.find((b) => b.fromCommitId === null);
  return (main || branches[0]).id;
}

function findBranchIdByName(graph, name) {
  const branches = Array.isArray(graph?.branches) ? graph.branches : [];
  const branch = branches.find((b) => b && b.name === name);
  return branch ? branch.id : null;
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
        data: {
          text: `${title}-text-${i}`,
        },
      },
    });
    blockOrders.push(blockId);
  }

  return {
    title: title.slice(0, 30),
    description: `${title}-desc`.slice(0, 100),
    branchId,
    blocks,
    blockOrders,
  };
}

function createCommit(cookie, docId, branchId, title) {
  const res = http.post(
    `${BASE_URL}/api/document/${docId}/commit`,
    JSON.stringify(buildCommitBody(title, branchId)),
    { ...headers(cookie), tags: { op: 'seed_commit_create' } },
  );
  tSeedCommit.add(res.timings.duration);
  ensureStatus(res, [201], 'seed_commit_create');
  return parseJson(res, 'seed_commit_create');
}

function createBranch(cookie, docId, branchName, fromCommitId) {
  const res = http.post(
    `${BASE_URL}/api/document/${docId}/branch`,
    JSON.stringify({ name: branchName, fromCommitId }),
    { ...headers(cookie), tags: { op: 'seed_branch_create' } },
  );
  tSeedBranch.add(res.timings.duration);
  ensureStatus(res, [201], 'seed_branch_create');
  return parseJson(res, 'seed_branch_create');
}

function seedOneTarget(cookie, idx) {
  const key = `${RUN_ID}${pad3(idx + 1)}`;
  const docTitle = `SDEL-${TARGET}-${key}`.slice(0, 50);

  const doc = createDoc(cookie, docTitle);
  const graph0 = getGraph(cookie, doc.id);
  const mainId = mainBranchId(graph0);

  const mainCommitCount = Math.max(2, MAIN_COMMITS);
  const mainCommitIds = [];
  for (let i = 0; i < mainCommitCount; i += 1) {
    const commit = createCommit(cookie, doc.id, mainId, `m${i}-${key}`);
    mainCommitIds.push(commit.id);
  }

  const fromCommitId = mainCommitIds[0];
  const featureName = `feat-${key}`.slice(0, 100);
  const branchRes = createBranch(cookie, doc.id, featureName, fromCommitId);

  let featureBranchId = branchRes.branchId;
  if (!featureBranchId) {
    const graph1 = getGraph(cookie, doc.id);
    featureBranchId = findBranchIdByName(graph1, featureName);
    if (!featureBranchId) {
      throw new Error('seed_branch_create could not resolve feature branch id');
    }
  }

  const featureCommitCount = Math.max(1, FEATURE_COMMITS);
  const featureCommitIds = [];
  for (let i = 0; i < featureCommitCount; i += 1) {
    const commit = createCommit(cookie, doc.id, featureBranchId, `f${i}-${key}`);
    featureCommitIds.push(commit.id);
  }

  return {
    docId: doc.id,
    branchId: featureBranchId,
    commitId: featureCommitIds[featureCommitIds.length - 1],
    docTitle,
  };
}

function deleteTarget(cookie, target) {
  let url;
  let op;

  if (TARGET === 'doc') {
    url = `${BASE_URL}/api/document/${target.docId}`;
    op = 'delete_doc';
  } else if (TARGET === 'branch') {
    url = `${BASE_URL}/api/document/${target.docId}/branch/${target.branchId}`;
    op = 'delete_branch';
  } else {
    url = `${BASE_URL}/api/document/${target.docId}/commit/${target.commitId}`;
    op = 'delete_commit';
  }

  const res = http.del(url, null, { ...headers(cookie), tags: { op } });
  tDelete.add(res.timings.duration);
  ensureStatus(res, [204], op);
}

export function setup() {
  const cookie = login(TEST_EMAIL);
  const targets = [];

  for (let i = 0; i < TARGET_COUNT; i += 1) {
    targets.push(seedOneTarget(cookie, i));
  }

  return { targets, runId: RUN_ID, target: TARGET };
}

export default function (data) {
  const idx = exec.scenario.iterationInTest;
  const target = data.targets[idx];
  if (!target) {
    throw new Error(`No target for iteration ${idx}`);
  }

  const cookie = login(TEST_EMAIL);
  deleteTarget(cookie, target);
}

export function handleSummary(data) {
  const outPath = `${RESULT_DIR}/${TARGET}_delete_${RUN_ID}.json`;
  return {
    stdout: `\n[single-user-delete] target=${TARGET}, run_id=${RUN_ID}, count=${TARGET_COUNT}, blocks_per_commit=${BLOCKS_PER_COMMIT}\n`,
    [outPath]: JSON.stringify(data, null, 2),
  };
}
