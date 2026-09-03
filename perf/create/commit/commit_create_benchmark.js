import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Rate, Trend } from 'k6/metrics';

import { buildCommitBlockPlan } from '../../seed/commit_block_plan.mjs';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TEST_EMAIL = __ENV.TEST_EMAIL || 'test@test.com';
const TEST_PASSWORD = __ENV.TEST_PASSWORD || 'Testtest1';
const VERSION = __ENV.VERSION || 'local';
const BLOCKS_PER_COMMIT = Number(__ENV.BLOCKS_PER_COMMIT || 50);
const ITERATIONS = Number(__ENV.ITERATIONS || 20);
const WARMUP_ITERATIONS = Number(__ENV.WARMUP_ITERATIONS || 3);
const RUN_NUMBER = Number(__ENV.RUN_NUMBER || 1);
const RUN_ID = __ENV.RUN_ID || `${Date.now()}`;
const RESULT_ROOT = __ENV.RESULT_ROOT || 'perf/create/commit/results';

if (![50, 200].includes(BLOCKS_PER_COMMIT)) {
  throw new Error('BLOCKS_PER_COMMIT must be 50 or 200');
}
if (ITERATIONS < 1) {
  throw new Error('ITERATIONS must be at least 1');
}
if (WARMUP_ITERATIONS < 0) {
  throw new Error('WARMUP_ITERATIONS must not be negative');
}

export const options = {
  summaryTrendStats: ['count', 'avg', 'min', 'med', 'max', 'p(90)', 'p(95)'],
  setupTimeout: '2m',
  scenarios: {
    commit_create: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: ITERATIONS,
      maxDuration: '5m',
    },
  },
  thresholds: {
    commit_create_failed: ['rate==0'],
  },
};

const commitCreateFailed = new Rate('commit_create_failed');
const commitCreate = new Trend('commit_create_ms');

function randomUuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (token) => {
    const random = Math.floor(Math.random() * 16);
    const value = token === 'x' ? random : ((random & 0x3) | 0x8);
    return value.toString(16);
  });
}

function headers(cookie) {
  return {
    headers: {
      'Content-Type': 'application/json',
      Cookie: cookie,
      'Idempotency-Key': randomUuid(),
    },
  };
}

function ensureStatus(response, expected, operation) {
  const ok = check(response, {
    [`${operation} status`]: (result) => result.status === expected,
  });
  if (!ok) {
    const body = typeof response.body === 'string' ? response.body.slice(0, 300) : '';
    throw new Error(`${operation} failed status=${response.status}, body=${body}`);
  }
}

function parseJson(response, operation) {
  try {
    return response.json();
  } catch (_error) {
    throw new Error(`${operation} invalid JSON`);
  }
}

function login() {
  const response = http.post(
    `${BASE_URL}/api/user/login`,
    JSON.stringify({ email: TEST_EMAIL, password: TEST_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { op: 'setup_login' } },
  );
  ensureStatus(response, 200, 'setup_login');

  const session = response.cookies.JSESSIONID && response.cookies.JSESSIONID[0];
  if (!session || !session.value) {
    throw new Error('setup_login missing JSESSIONID');
  }
  return `JSESSIONID=${session.value}`;
}

function createDocument(cookie) {
  const title = `PCREATE-${VERSION}-${BLOCKS_PER_COMMIT}-${RUN_ID}`.slice(0, 50);
  const response = http.post(
    `${BASE_URL}/api/document`,
    JSON.stringify({ title }),
    { ...headers(cookie), tags: { op: 'setup_doc_create' } },
  );
  ensureStatus(response, 201, 'setup_doc_create');
  return parseJson(response, 'setup_doc_create');
}

function resolveMainBranch(cookie, documentId) {
  const response = http.get(
    `${BASE_URL}/api/document/${documentId}/graph`,
    { ...headers(cookie), tags: { op: 'setup_graph' } },
  );
  ensureStatus(response, 200, 'setup_graph');

  const graph = parseJson(response, 'setup_graph');
  const branches = Array.isArray(graph.branches) ? graph.branches : [];
  const main = branches.find((branch) => branch.fromCommitId === null) || branches[0];
  if (!main) {
    throw new Error('setup_graph no branches');
  }
  return main.id;
}

function buildCommitBody(branchId, phase, iteration) {
  const key = `${VERSION}-b${BLOCKS_PER_COMMIT}-r${RUN_NUMBER}-${phase}${iteration}`;
  const { blocks, blockOrders } = buildCommitBlockPlan({
    key,
    commitIndex: iteration,
    blockCount: BLOCKS_PER_COMMIT,
    changeRate: 1,
    previousBlockIds: [],
  });

  return {
    title: `create-${BLOCKS_PER_COMMIT}-${iteration}`,
    description: `${VERSION} commit create benchmark`,
    branchId,
    blocks,
    blockOrders,
  };
}

export function setup() {
  const cookie = login();
  const document = createDocument(cookie);
  const branchId = resolveMainBranch(cookie, document.id);

  for (let iteration = 1; iteration <= WARMUP_ITERATIONS; iteration += 1) {
    const response = http.post(
      `${BASE_URL}/api/document/${document.id}/commit`,
      JSON.stringify(buildCommitBody(branchId, 'w', iteration)),
      { ...headers(cookie), tags: { op: 'warmup_commit_create' } },
    );
    ensureStatus(response, 201, 'warmup_commit_create');
  }

  return { cookie, documentId: document.id, branchId };
}

export default function (data) {
  const iteration = exec.scenario.iterationInTest + 1;
  const response = http.post(
    `${BASE_URL}/api/document/${data.documentId}/commit`,
    JSON.stringify(buildCommitBody(data.branchId, 'm', iteration)),
    { ...headers(data.cookie), tags: { op: 'commit_create' } },
  );

  const ok = check(response, {
    'commit_create status': (result) => result.status === 201,
  });
  commitCreateFailed.add(!ok);
  commitCreate.add(response.timings.duration);

  if (!ok) {
    const body = typeof response.body === 'string' ? response.body.slice(0, 300) : '';
    throw new Error(`commit_create failed status=${response.status}, body=${body}`);
  }
}

export function handleSummary(data) {
  const output = `${RESULT_ROOT}/${VERSION}/blocks-${BLOCKS_PER_COMMIT}/run-${RUN_NUMBER}/summary.json`;
  return {
    stdout: `\n[commit-create] version=${VERSION}, blocks=${BLOCKS_PER_COMMIT}, warmup=${WARMUP_ITERATIONS}, measured=${ITERATIONS}\n`,
    [output]: JSON.stringify(data, null, 2),
  };
}
