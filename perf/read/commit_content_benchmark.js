import http from 'k6/http';
import exec from 'k6/execution';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import {
  summaryDataWithoutAuth,
  summaryLoad,
  utf8ByteLength,
  validBlock,
} from './commit_content_benchmark_helpers.mjs';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const USER_PREFIX = __ENV.USER_PREFIX || 'perfuser';
const USER_DOMAIN = __ENV.USER_DOMAIN || 'test.com';
const USER_PASSWORD = __ENV.USER_PASSWORD || 'Testtest1';
const USER_COUNT = Number(__ENV.USER_COUNT || 20);
const DOCS_PER_USER = Number(__ENV.DOCS_PER_USER || 2);
const MAIN_COMMITS = Number(__ENV.MAIN_COMMITS || 10);
const BLOCKS_PER_COMMIT = Number(__ENV.BLOCKS_PER_COMMIT || 20);
const RUN_ID = __ENV.RUN_ID;
const SCENARIO = __ENV.SCENARIO || 'hot';
const PROVIDER = __ENV.PROVIDER || 'local';
const RUN_NUMBER = Number(__ENV.RUN_NUMBER || 1);
const RESULT_ROOT = __ENV.RESULT_ROOT || 'perf/read/results/commit-cache';
const COLD_TARGET_COUNT = 400;
const MEASUREMENT_GATE_URL = __ENV.MEASUREMENT_GATE_URL;
const MEASUREMENT_GATE_RETRIES = Number(__ENV.MEASUREMENT_GATE_RETRIES || 120);
const MEASUREMENT_GATE_INTERVAL_SECONDS = Number(__ENV.MEASUREMENT_GATE_INTERVAL_SECONDS || 0.5);

if (!['verify', 'cold', 'hot', 'mixed', 'cold_burst', 'saturation'].includes(SCENARIO)) {
  throw new Error(`Unsupported SCENARIO=${SCENARIO}`);
}
if (RUN_NUMBER < 1 || RUN_NUMBER > 3) {
  throw new Error('RUN_NUMBER must be 1, 2, or 3');
}
function scenarioOptions() {
  if (SCENARIO === 'verify') {
    return { executor: 'shared-iterations', vus: 1, iterations: 1, exec: 'verifyResponses' };
  }
  if (SCENARIO === 'cold') {
    return {
      executor: 'shared-iterations',
      vus: Number(__ENV.COLD_VUS || 50),
      iterations: COLD_TARGET_COUNT,
      maxDuration: __ENV.COLD_MAX_DURATION || '5m',
      exec: 'runCold',
    };
  }
  if (SCENARIO === 'hot') {
    return {
      executor: 'constant-vus',
      vus: Number(__ENV.HOT_VUS || 10),
      duration: __ENV.HOT_DURATION || '60s',
      exec: 'runHot',
    };
  }
  if (SCENARIO === 'mixed') {
    return {
      executor: 'constant-vus',
      vus: Number(__ENV.MIXED_VUS || 10),
      duration: __ENV.MIXED_DURATION || '60s',
      exec: 'runMixed',
    };
  }
  if (SCENARIO === 'cold_burst') {
    const vus = Number(__ENV.COLD_BURST_VUS || 10);
    if (![10, 50, 100].includes(vus)) {
      throw new Error('COLD_BURST_VUS must be one of 10, 50, or 100');
    }
    return { executor: 'per-vu-iterations', vus, iterations: 1, maxDuration: '1m', exec: 'runColdBurst' };
  }
  return {
    executor: 'ramping-arrival-rate',
    startRate: 25,
    timeUnit: '1s',
    preAllocatedVUs: Number(__ENV.SATURATION_PRE_ALLOCATED_VUS || 200),
    maxVUs: Number(__ENV.SATURATION_MAX_VUS || 400),
    stages: [
      { target: 25, duration: '60s' },
      { target: 50, duration: '60s' },
      { target: 100, duration: '60s' },
      { target: 200, duration: '60s' },
    ],
    exec: 'runSaturation',
  };
}

function canonicalLoadProfile(scenario) {
  if (scenario.executor === 'ramping-arrival-rate') {
    return `rate-${scenario.stages.map((stage) => stage.target).join('-')}`;
  }
  return `vus-${scenario.vus}`;
}

const configuredScenario = scenarioOptions();
const CANONICAL_LOAD_PROFILE = canonicalLoadProfile(configuredScenario);
const LOAD_PROFILE = __ENV.LOAD_PROFILE || CANONICAL_LOAD_PROFILE;
if (LOAD_PROFILE !== CANONICAL_LOAD_PROFILE) {
  throw new Error(
    `LOAD_PROFILE=${LOAD_PROFILE} does not match canonical ${CANONICAL_LOAD_PROFILE} for SCENARIO=${SCENARIO}`,
  );
}

export const options = {
  summaryTrendStats: ['min', 'med', 'avg', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: { commit_content: configuredScenario },
  thresholds: {
    'http_req_failed{op:commit_get}': ['rate<0.02'],
    commit_get_failed: ['rate<0.02'],
  },
};

const commitGetFailed = new Rate('commit_get_failed');
const tCommitGet = new Trend('op_commit_get_ms');
const tCommitGetPayload = new Trend('op_commit_get_payload_bytes');
const cookieCache = new Map();

function pad3(n) {
  return String(n).padStart(3, '0');
}

function userEmail(userNo) {
  return `${USER_PREFIX}_u${pad3(userNo)}@${USER_DOMAIN}`;
}

function titlePrefix(userNo) {
  return `PDEL-${RUN_ID}u${pad3(userNo)}d`;
}

function headers(cookie) {
  return { headers: { Cookie: cookie } };
}

function ensureStatus(res, statuses, op) {
  const ok = check(res, { [`${op} status`]: (r) => statuses.includes(r.status) });
  if (!ok) {
    const body = typeof res.body === 'string' ? res.body.slice(0, 240) : '';
    throw new Error(`${op} failed status=${res.status}, body=${body}`);
  }
}

function parseJson(res, op) {
  try {
    return res.json();
  } catch (_error) {
    throw new Error(`${op} invalid JSON`);
  }
}

function login(email) {
  const cached = cookieCache.get(email);
  if (cached) return cached;

  http.cookieJar().clear(BASE_URL);
  const res = http.post(
    `${BASE_URL}/api/user/login`,
    JSON.stringify({ email, password: USER_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { op: 'commit_setup_login' } },
  );
  ensureStatus(res, [200], 'commit_setup_login');
  const jsession = res.cookies.JSESSIONID && res.cookies.JSESSIONID[0];
  if (!jsession || !jsession.value) throw new Error('commit_setup_login missing JSESSIONID');
  const cookie = `JSESSIONID=${jsession.value}`;
  cookieCache.set(email, cookie);
  return cookie;
}

function resolveDocs(cookie, userNo) {
  const docs = [];
  const pageSize = Math.max(DOCS_PER_USER * 2, 20);
  for (let page = 0; page < 20 && docs.length < DOCS_PER_USER; page += 1) {
    const res = http.get(
      `${BASE_URL}/api/document?page=${page}&size=${pageSize}&sort=updatedAt&order=desc`,
      { ...headers(cookie), tags: { op: 'commit_setup_resolve_docs' } },
    );
    ensureStatus(res, [200], 'commit_setup_resolve_docs');
    const body = parseJson(res, 'commit_setup_resolve_docs');
    if (!body || !Array.isArray(body.content)) throw new Error('commit_setup_resolve_docs invalid page response');
    for (const item of body.content) {
      if (item.title && item.title.startsWith(titlePrefix(userNo))) docs.push(item);
    }
    if (body.last === true || body.content.length === 0) break;
  }
  if (docs.length !== DOCS_PER_USER) {
    throw new Error(`commit_setup expected ${DOCS_PER_USER} docs for userNo=${userNo}, got ${docs.length}`);
  }
  return docs.sort((a, b) => String(a.title).localeCompare(String(b.title)));
}

function mainCommitTargets(cookie, userNo, doc) {
  const res = http.get(
    `${BASE_URL}/api/document/${doc.id}/graph`,
    { ...headers(cookie), tags: { op: 'commit_setup_graph' } },
  );
  ensureStatus(res, [200], 'commit_setup_graph');
  const graph = parseJson(res, 'commit_setup_graph');
  const main = Array.isArray(graph.branches) && graph.branches.find((branch) => branch.fromCommitId === null);
  const commits = Array.isArray(graph.commits) ? graph.commits : [];
  const mainCommits = main ? commits.filter((commit) => commit.branchId === main.id) : [];
  if (mainCommits.length !== MAIN_COMMITS) {
    throw new Error(`commit_setup expected ${MAIN_COMMITS} main commits for docId=${doc.id}, got ${mainCommits.length}`);
  }
  return mainCommits
    .sort((a, b) => Number(b.id) - Number(a.id))
    .map((commit) => ({ userNo, cookie, docId: doc.id, commitId: commit.id }));
}

function warmup(targets) {
  for (const target of targets) {
    const res = http.get(`${BASE_URL}/api/document/${target.docId}/commit/${target.commitId}`, headers(target.cookie));
    ensureStatus(res, [200], 'commit_setup_warmup');
  }
}

function awaitMeasurementRelease() {
  if (!MEASUREMENT_GATE_URL) return;
  if (!/^http:\/\/(127\.0\.0\.1|localhost):\d+$/.test(MEASUREMENT_GATE_URL)) {
    throw new Error('MEASUREMENT_GATE_URL must target localhost over HTTP');
  }
  const tags = { tags: { op: 'measurement_gate' } };
  const complete = http.post(`${MEASUREMENT_GATE_URL}/gate/setup-complete`, null, tags);
  ensureStatus(complete, [200], 'measurement_gate_setup_complete');
  for (let attempt = 0; attempt < MEASUREMENT_GATE_RETRIES; attempt += 1) {
    const release = http.get(`${MEASUREMENT_GATE_URL}/gate/release`, tags);
    if (release.status === 200) return;
    if (release.status !== 425) {
      throw new Error(`measurement_gate_release failed status=${release.status}`);
    }
    sleep(MEASUREMENT_GATE_INTERVAL_SECONDS);
  }
  throw new Error('measurement_gate_release timed out');
}

export function setup() {
  if (!RUN_ID) {
    throw new Error('RUN_ID is required. Use the same RUN_ID that was used for the dataset seed');
  }
  const users = [];
  const allTargets = [];
  const hotTargets = [];
  for (let userNo = 1; userNo <= USER_COUNT; userNo += 1) {
    const cookie = login(userEmail(userNo));
    const commits = resolveDocs(cookie, userNo).flatMap((doc) => mainCommitTargets(cookie, userNo, doc));
    if (commits.length !== DOCS_PER_USER * MAIN_COMMITS) {
      throw new Error(`commit_setup expected ${DOCS_PER_USER * MAIN_COMMITS} commits for userNo=${userNo}, got ${commits.length}`);
    }
    users.push({ userNo, cookie, commits });
    allTargets.push(...commits);
    hotTargets.push(...commits.slice(0, 2));
  }
  if (USER_COUNT !== 20 || DOCS_PER_USER !== 2 || MAIN_COMMITS !== 10 || allTargets.length !== COLD_TARGET_COUNT) {
    throw new Error(`commit_setup requires 20 users, 2 docs per user, 10 main commits per doc, and ${COLD_TARGET_COUNT} targets`);
  }
  if (hotTargets.length !== 40) throw new Error(`commit_setup expected 40 hot targets, got ${hotTargets.length}`);
  const mixedHotTargets = users.flatMap((user) => user.commits.slice(0, 4));
  if (mixedHotTargets.length !== 80) throw new Error(`commit_setup expected 80 mixed hot targets, got ${mixedHotTargets.length}`);
  if (SCENARIO === 'mixed') warmup(mixedHotTargets);
  if (['hot', 'saturation'].includes(SCENARIO)) warmup(hotTargets);
  awaitMeasurementRelease();
  return { users, allTargets, hotTargets };
}

function requestCommit(target, measured) {
  const res = http.get(
    `${BASE_URL}/api/document/${target.docId}/commit/${target.commitId}`,
    { ...headers(target.cookie), tags: { op: measured ? 'commit_get' : 'commit_setup_warmup' } },
  );
  const ok = check(res, { 'commit_get status': (r) => r.status === 200 });
  if (measured) {
    commitGetFailed.add(!ok, { op: 'commit_get' });
    tCommitGet.add(res.timings.duration, { op: 'commit_get' });
    tCommitGetPayload.add(typeof res.body === 'string' ? utf8ByteLength(res.body) : 0, { op: 'commit_get' });
  }
  if (!ok) {
    const body = typeof res.body === 'string' ? res.body.slice(0, 240) : '';
    throw new Error(`commit_get failed status=${res.status}, body=${body}`);
  }
  return res;
}

function userIteration(data) {
  const iteration = exec.scenario.iterationInTest;
  return {
    user: data.users[iteration % data.users.length],
    localIteration: Math.floor(iteration / data.users.length),
  };
}

export function verifyResponses(data) {
  const indexes = [0, Math.floor(data.allTargets.length / 2), data.allTargets.length - 1];
  for (const index of indexes) {
    const body = parseJson(requestCommit(data.allTargets[index], false), 'commit_verify');
    const valid = check(body, {
      '커밋 본문 block 수': (value) => Array.isArray(value.content) && value.content.length === BLOCKS_PER_COMMIT,
      '커밋 첫 block 형태': (value) => validBlock(value.content, 0),
      '커밋 중간 block 형태': (value) => validBlock(value.content, Math.floor(value.content.length / 2)),
      '커밋 마지막 block 형태': (value) => validBlock(value.content, value.content.length - 1),
    });
    if (!valid) throw new Error(`commit_verify invalid content for target index=${index}`);
  }
}

export function runCold(data) {
  const target = data.allTargets[exec.scenario.iterationInTest];
  if (!target) throw new Error(`cold missing target index=${exec.scenario.iterationInTest}`);
  requestCommit(target, true);
}

export function runHot(data) {
  const { user, localIteration } = userIteration(data);
  const target = user.commits[localIteration % 2];
  requestCommit(target, true);
}

export function runMixed(data) {
  const { user, localIteration } = userIteration(data);
  const hotCount = 4;
  const target = localIteration % 5 < 4
    ? user.commits[localIteration % hotCount]
    : user.commits[hotCount + (Math.floor(localIteration / 5) % (user.commits.length - hotCount))];
  requestCommit(target, true);
}

export function runColdBurst(data) {
  requestCommit(data.hotTargets[0], true);
}

export function runSaturation(data) {
  const target = data.hotTargets[exec.scenario.iterationInTest % data.hotTargets.length];
  requestCommit(target, true);
}

export function handleSummary(data) {
  const load = summaryLoad(configuredScenario);
  const path = `${RESULT_ROOT}/${PROVIDER}/blocks-${BLOCKS_PER_COMMIT}/${SCENARIO}/${LOAD_PROFILE}/run-${RUN_NUMBER}/summary.json`;
  const summary = {
    provider: PROVIDER,
    blockCount: BLOCKS_PER_COMMIT,
    pattern: SCENARIO,
    loadProfile: LOAD_PROFILE,
    ...load,
    runNumber: RUN_NUMBER,
    metrics: summaryDataWithoutAuth(data),
  };
  return {
    stdout: `\n[commit-content-benchmark] provider=${PROVIDER}, blocks=${BLOCKS_PER_COMMIT}, pattern=${SCENARIO}, ${Object.keys(load)[0]}=${Object.values(load)[0]}, run=${RUN_NUMBER}\n`,
    [path]: JSON.stringify(summary, null, 2),
  };
}
