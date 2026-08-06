#!/usr/bin/env node

import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const PROVIDERS = ['none', 'caffeine', 'redis'];
const RUNS = [1, 2, 3];
const REQUIRED_PROMETHEUS_FIELDS = [
  'assembleCount', 'cacheHit', 'cacheError', 'cachePutError', 'heapUsed', 'gcPauseCount', 'gcPauseSum',
];

export function repeatVariation(values) {
  if (!validThreeRunValues(values)) return null;
  const average = mean(values);
  if (average === 0) return null;
  return (Math.max(...values) - Math.min(...values)) / Math.abs(average);
}

export function minimumMeaningfulDelta(variation) {
  if (!Number.isFinite(variation) || variation < 0) return null;
  return Math.max(0.10, variation);
}

export function sameDirection(values) {
  if (!validThreeRunValues(values)) return false;
  return values.every((value) => value > 0) || values.every((value) => value < 0);
}

export function parseK6Summary(summary) {
  const metrics = summary?.metrics?.metrics;
  const hasMetricsObject = metrics !== null && typeof metrics === 'object';
  return {
    metadata: {
      provider: summary?.provider,
      blockCount: summary?.blockCount,
      pattern: summary?.pattern,
      loadProfile: summary?.loadProfile,
      runNumber: summary?.runNumber,
      vus: summary?.vus ?? null,
      rate: summary?.rate ?? null,
    },
    p95: metricValue(metrics, 'op_commit_get_ms', 'p(95)'),
    p99: metricValue(metrics, 'op_commit_get_ms', 'p(99)'),
    throughput: metricValue(metrics, 'iterations', 'rate'),
    failureRate: metricValue(metrics, 'commit_get_failed', 'rate'),
    droppedIterations: hasMetricsObject && !Object.hasOwn(metrics, 'dropped_iterations')
      ? 0
      : metricValue(metrics, 'dropped_iterations', 'count'),
  };
}

export function parsePrometheus(text) {
  const samples = parsePrometheusSamples(text);
  return {
    assembleCount: sumMetric(samples, 'commit_content_assemble_seconds_count'),
    cacheHit: sumMetric(samples, 'commit_content_cache_get_total', { result: 'hit' }),
    cacheError: sumMetric(samples, 'commit_content_cache_get_total', { result: 'error' }),
    cachePutError: sumMetric(samples, 'commit_content_cache_put_total', { result: 'error' }),
    heapUsed: sumMetric(samples, 'jvm_memory_used_bytes', { area: 'heap' }),
    gcPauseCount: sumMetric(samples, 'jvm_gc_pause_seconds_count'),
    gcPauseSum: sumMetric(samples, 'jvm_gc_pause_seconds_sum'),
  };
}

export function parseRedisInfo(text) {
  const values = new Map();
  for (const line of text.split(/\r?\n/)) {
    if (!line || line.startsWith('#')) continue;
    const separator = line.indexOf(':');
    if (separator < 1) continue;
    values.set(line.slice(0, separator), line.slice(separator + 1));
  }
  return finiteNumber(values.get('used_memory'));
}

export async function compareResults(options) {
  const warnings = [];
  const providers = {};
  const revisions = new Set();
  const invariantMetadata = [];

  for (const provider of PROVIDERS) {
    const runs = [];
    for (const runNumber of RUNS) {
      const label = `${provider} run-${runNumber}`;
      const runDir = path.join(
        options.resultRoot, provider, `blocks-${options.blocks}`, options.pattern,
        options.loadProfile, `run-${runNumber}`,
      );
      const run = await loadRun(
        runDir, provider, options.blocks, options.pattern, options.loadProfile, runNumber, warnings,
      );
      runs.push(run);
      if (run.revision) revisions.add(run.revision);
      if (run.environment) invariantMetadata.push({ label, environment: run.environment });
    }
    providers[provider] = summarizeProvider(runs);
  }

  if (revisions.size > 1) warnings.push('3회 비교 결과의 git revision이 서로 다릅니다.');
  validateInvariantEnvironment(invariantMetadata, warnings);
  validateInvariantLoad(providers, warnings);

  const baselineP95 = providers.none.runs.map((run) => run.k6?.p95 ?? null);
  const variation = repeatVariation(baselineP95);
  const threshold = minimumMeaningfulDelta(variation);
  const baseline = { repeatVariation: variation, minimumMeaningfulDelta: threshold };

  for (const provider of ['caffeine', 'redis']) {
    const candidate = providers[provider];
    candidate.pairedP95Changes = pairedChanges(
      providers.none.runs.map((run) => run.k6?.p95 ?? null),
      candidate.runs.map((run) => run.k6?.p95 ?? null),
      'lower',
    );
    candidate.pairedThroughputChanges = pairedChanges(
      providers.none.runs.map((run) => run.k6?.throughput ?? null),
      candidate.runs.map((run) => run.k6?.throughput ?? null),
      'higher',
    );
    candidate.sameDirection = sameDirection(candidate.pairedP95Changes);
    candidate.throughputSameDirection = sameDirection(candidate.pairedThroughputChanges);
  }

  const requiredWarnings = [...warnings];
  const decision = requiredWarnings.length > 0
    ? { verdict: 'INSUFFICIENT_DATA', reasons: [...requiredWarnings] }
    : decideVerdict(providers, threshold, Boolean(options.redisFailOpenPassed));

  const evidenceWarnings = [...requiredWarnings];
  if (!options.redisFailOpenPassed) {
    evidenceWarnings.push('Redis Fail-Open 통과 증거가 없어 Redis를 긍정 후보에서 제외했습니다.');
  }

  return {
    condition: { blocks: options.blocks, pattern: options.pattern, loadProfile: options.loadProfile },
    verdict: decision.verdict,
    reasons: decision.reasons,
    limitation: '이 판정은 단일 블록 수·접근 패턴 조건만 다루며 최종 provider를 선택하지 않습니다.',
    baseline,
    providers,
    evidence: {
      redisFailOpenPassed: Boolean(options.redisFailOpenPassed),
      warnings: evidenceWarnings,
    },
  };
}

async function loadRun(runDir, provider, blocks, pattern, loadProfile, runNumber, warnings) {
  const label = `${provider} run-${runNumber}`;
  const run = { runNumber, directory: runDir };
  const summary = await readJson(path.join(runDir, 'summary.json'), label, warnings);
  if (summary) {
    run.k6 = parseK6Summary(summary);
    validateSummaryMetadata(
      run.k6.metadata, { provider, blocks, pattern, loadProfile, runNumber }, label, warnings,
    );
    for (const field of ['p95', 'throughput', 'failureRate', 'droppedIterations']) {
      if (!Number.isFinite(run.k6[field])) warnings.push(`${label}: k6 ${field} 값이 없거나 유한하지 않습니다.`);
    }
  }

  const environmentText = await readRequired(path.join(runDir, 'environment.txt'), label, warnings);
  if (environmentText !== null) {
    run.environment = parseEnvironment(environmentText);
    validateEnvironment(
      run.environment, { provider, blocks, pattern, loadProfile, runNumber }, label, warnings,
    );
  }
  const revisionText = await readRequired(path.join(runDir, 'git-revision.txt'), label, warnings);
  if (revisionText !== null) {
    run.revision = revisionText.trim();
    if (!run.revision) warnings.push(`${label}: git revision이 비어 있습니다.`);
  }

  const before = await readPrometheusSnapshot(runDir, 'before', label, warnings);
  const after = await readPrometheusSnapshot(runDir, 'after', label, warnings);
  if (before && after) run.resources = prometheusDeltas(before, after, label, warnings);

  if (provider === 'redis') {
    const redisBeforeText = await readRequired(path.join(runDir, 'redis-info-before.txt'), label, warnings);
    const redisAfterText = await readRequired(path.join(runDir, 'redis-info-after.txt'), label, warnings);
    if (redisBeforeText !== null && redisAfterText !== null) {
      const redisBefore = parseRedisInfo(redisBeforeText);
      const redisAfter = parseRedisInfo(redisAfterText);
      if (!Number.isFinite(redisBefore) || !Number.isFinite(redisAfter)) {
        warnings.push(`${label}: Redis used_memory before/after 값이 없습니다.`);
      } else {
        run.redisUsedMemoryDelta = redisAfter - redisBefore;
      }
    }
  }
  return run;
}

function summarizeProvider(runs) {
  return {
    runs,
    means: {
      p95: meanOrNull(runs.map((run) => run.k6?.p95 ?? null)),
      throughput: meanOrNull(runs.map((run) => run.k6?.throughput ?? null)),
      failureRate: meanOrNull(runs.map((run) => run.k6?.failureRate ?? null)),
      droppedIterations: meanOrNull(runs.map((run) => run.k6?.droppedIterations ?? null)),
      assembleCountDelta: meanOrNull(runs.map((run) => run.resources?.assembleCountDelta ?? null)),
      cacheHitDelta: meanOrNull(runs.map((run) => run.resources?.cacheHitDelta ?? null)),
      cacheErrorDelta: meanOrNull(runs.map((run) => run.resources?.cacheErrorDelta ?? null)),
      cachePutErrorDelta: meanOrNull(runs.map((run) => run.resources?.cachePutErrorDelta ?? null)),
      heapUsedDelta: meanOrNull(runs.map((run) => run.resources?.heapUsedDelta ?? null)),
      gcPauseCountDelta: meanOrNull(runs.map((run) => run.resources?.gcPauseCountDelta ?? null)),
      gcPauseSumDelta: meanOrNull(runs.map((run) => run.resources?.gcPauseSumDelta ?? null)),
      redisUsedMemoryDelta: meanOrNull(runs.map((run) => run.redisUsedMemoryDelta ?? null)),
    },
  };
}

function decideVerdict(providers, threshold, redisFailOpenPassed) {
  if (!Number.isFinite(threshold)) {
    return {
      verdict: 'INSUFFICIENT_DATA',
      reasons: ['baseline 반복 변동폭과 최소 의미 차이를 계산할 수 없습니다.'],
    };
  }

  const allDroppedIterationsAreZero = PROVIDERS.every((provider) =>
    providers[provider].runs.every((run) => run.k6.droppedIterations === 0));
  const conflicts = [];
  const eligibleCandidates = [];
  for (const provider of ['caffeine', 'redis']) {
    const candidate = providers[provider];
    const p95MeanChange = meanOrNull(candidate.pairedP95Changes);
    const noErrors = candidate.runs.every((run, index) =>
      run.k6.failureRate <= providers.none.runs[index].k6.failureRate
      && run.resources.cacheErrorDelta === 0
      && run.resources.cachePutErrorDelta === 0);
    const assembleReduced = candidate.runs.every((run, index) =>
      run.resources.assembleCountDelta < providers.none.runs[index].resources.assembleCountDelta);
    const cacheHitObserved = candidate.runs.every((run) => run.resources.cacheHitDelta > 0);
    const failOpenEligible = provider !== 'redis' || redisFailOpenPassed;
    const meaningfulP95 = candidate.sameDirection && p95MeanChange > threshold;
    const gates = {
      direction: candidate.sameDirection,
      meaningfulP95,
      assembleReduced,
      cacheHit: cacheHitObserved,
      cacheErrors: noErrors,
      dropped: allDroppedIterationsAreZero,
      failOpen: failOpenEligible,
    };
    const reasons = [];
    if (!gates.direction) reasons.push('3회 p95 변화 방향이 일치하지 않습니다.');
    if (gates.direction && !gates.meaningfulP95) reasons.push('p95 개선이 최소 의미 차이에 미달했습니다.');
    if (!gates.assembleReduced) reasons.push('MongoDB assemble 감소가 3회 모두 확인되지 않았습니다.');
    if (!gates.cacheHit) reasons.push('cache hit 증가가 3회 모두 확인되지 않았습니다.');
    if (!gates.cacheErrors) reasons.push('요청 오류율 또는 cache get/put error가 증가했습니다.');
    if (!gates.dropped) reasons.push('dropped iteration이 발생했습니다.');
    if (!gates.failOpen) reasons.push('Redis Fail-Open 통과 증거가 없습니다.');
    const eligible = Object.values(gates).every(Boolean);
    candidate.condition = {
      p95MeanChange,
      gates,
      eligible,
    };
    candidate.reasons = reasons;
    if (!gates.direction) conflicts.push(`${provider}: ${reasons[0]}`);
    if (meaningfulP95 && !eligible) {
      conflicts.push(...reasons.filter((reason) => !reason.includes('최소 의미 차이'))
        .map((reason) => `${provider}: ${reason}`));
    }
    if (eligible) eligibleCandidates.push(provider);
  }
  if (conflicts.length > 0) return { verdict: 'INCONCLUSIVE', reasons: [...new Set(conflicts)] };
  if (eligibleCandidates.length > 0) {
    return {
      verdict: 'CACHE_CANDIDATE_CONFIRMED',
      reasons: [`조건을 통과한 캐시 후보: ${eligibleCandidates.join(', ')}`],
    };
  }
  return {
    verdict: 'NO_MEANINGFUL_IMPROVEMENT',
    reasons: ['완전하고 일관된 3회 증거에서 p95 개선이 최소 의미 차이에 미달했습니다.'],
  };
}

function validateInvariantLoad(providers, warnings) {
  const signatures = new Set();
  for (const provider of PROVIDERS) {
    for (const run of providers[provider].runs) {
      if (!run.k6) continue;
      const { vus, rate } = run.k6.metadata;
      const hasVus = Number.isFinite(vus);
      const hasRate = Number.isFinite(rate);
      if (hasVus === hasRate) {
        warnings.push(`${provider} run-${run.runNumber}: summary 부하 설정은 vus 또는 rate 중 하나만 가져야 합니다.`);
        continue;
      }
      signatures.add(hasVus ? `vus=${vus}` : `rate=${rate}`);
    }
  }
  if (signatures.size > 1) warnings.push('비교 실행의 summary 부하 설정이 서로 다릅니다.');
}

function prometheusDeltas(before, after, label, warnings) {
  const result = {};
  for (const field of REQUIRED_PROMETHEUS_FIELDS) {
    if (!Number.isFinite(before[field]) || !Number.isFinite(after[field])) {
      warnings.push(`${label}: Prometheus ${field} before/after 표본이 없습니다.`);
      continue;
    }
    const deltaName = `${field}Delta`;
    result[deltaName] = after[field] - before[field];
    if (field !== 'heapUsed' && result[deltaName] < 0) {
      warnings.push(`${label}: Prometheus 누적 metric ${field}이 감소해 재시작 또는 reset 가능성이 있습니다.`);
    }
  }
  return result;
}

async function readPrometheusSnapshot(runDir, suffix, label, warnings) {
  const text = await readRequired(path.join(runDir, `prometheus-${suffix}.txt`), label, warnings);
  if (text === null) return null;
  return parsePrometheus(text);
}

function validateSummaryMetadata(actual, expected, label, warnings) {
  const pairs = [
    ['provider', expected.provider], ['blockCount', expected.blocks],
    ['pattern', expected.pattern], ['loadProfile', expected.loadProfile],
    ['runNumber', expected.runNumber],
  ];
  for (const [field, value] of pairs) {
    if (String(actual[field]) !== String(value)) {
      warnings.push(`${label}: summary ${field}=${actual[field]}가 기대값 ${value}와 다릅니다.`);
    }
  }
  validateLoadProfileMeaning(actual, expected, label, warnings);
}

function validateLoadProfileMeaning(actual, expected, label, warnings) {
  if (expected.pattern === 'saturation') {
    const canonical = 'rate-25-50-100-200';
    if (expected.loadProfile !== canonical
        || actual.loadProfile !== canonical
        || actual.rate !== 200
        || actual.vus !== null) {
      warnings.push(
        `${label}: load profile 의미가 saturation stages 및 summary rate=200과 일치하지 않습니다.`,
      );
    }
    return;
  }

  const match = expected.loadProfile.match(/^vus-([1-9][0-9]*)$/);
  const expectedVus = match ? Number(match[1]) : null;
  if (!Number.isFinite(expectedVus)
      || actual.loadProfile !== expected.loadProfile
      || actual.vus !== expectedVus
      || actual.rate !== null) {
    warnings.push(
      `${label}: load profile 의미(${expected.loadProfile})가 summary vus=${actual.vus}, rate=${actual.rate}와 일치하지 않습니다.`,
    );
  }
}

function validateEnvironment(actual, expected, label, warnings) {
  const pairs = [
    ['provider', expected.provider], ['blocks_per_commit', expected.blocks],
    ['pattern', expected.pattern], ['load_profile', expected.loadProfile],
    ['run_no', expected.runNumber],
  ];
  for (const [field, value] of pairs) {
    if (String(actual[field]) !== String(value)) {
      warnings.push(`${label}: environment ${field}=${actual[field]}가 기대값 ${value}와 다릅니다.`);
    }
  }
  for (const field of ['run_id', 'user_count', 'docs_per_user', 'main_commits']) {
    if (!actual[field]) warnings.push(`${label}: environment ${field}가 없습니다.`);
  }
}

function validateInvariantEnvironment(entries, warnings) {
  for (const field of ['run_id', 'user_count', 'docs_per_user', 'main_commits']) {
    const values = new Set(entries.map(({ environment }) => environment[field]).filter(Boolean));
    if (values.size > 1) warnings.push(`비교 결과의 environment ${field}가 서로 다릅니다.`);
  }
}

function parseEnvironment(text) {
  const result = {};
  for (const line of text.split(/\r?\n/)) {
    const separator = line.indexOf('=');
    if (separator > 0) result[line.slice(0, separator)] = line.slice(separator + 1);
  }
  return result;
}

function parsePrometheusSamples(text) {
  const samples = [];
  for (const rawLine of text.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) continue;
    const match = line.match(/^([a-zA-Z_:][a-zA-Z0-9_:]*)(?:\{(.*)\})?\s+([^\s]+)(?:\s+\d+)?$/);
    if (!match) continue;
    const value = finiteNumber(match[3]);
    if (!Number.isFinite(value)) continue;
    samples.push({ metric: match[1], labels: parseLabels(match[2] ?? ''), value });
  }
  return samples;
}

function parseLabels(text) {
  const labels = {};
  const expression = /([a-zA-Z_][a-zA-Z0-9_]*)="((?:\\.|[^"\\])*)"/g;
  let match;
  while ((match = expression.exec(text)) !== null) {
    labels[match[1]] = match[2].replace(/\\([\\"n])/g, (_, character) => character === 'n' ? '\n' : character);
  }
  return labels;
}

function sumMetric(samples, metric, expectedLabels = {}) {
  const matches = samples.filter((sample) => sample.metric === metric
    && Object.entries(expectedLabels).every(([key, value]) => sample.labels[key] === value));
  if (matches.length === 0) return null;
  return matches.reduce((sum, sample) => sum + sample.value, 0);
}

function metricValue(metrics, metric, statistic) {
  return finiteNumber(metrics?.[metric]?.values?.[statistic]);
}

function finiteNumber(value) {
  if (value === null || value === undefined || value === '') return null;
  const number = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(number) ? number : null;
}

function pairedChanges(baseline, candidate, direction) {
  if (baseline.length !== 3 || candidate.length !== 3) return [];
  return baseline.map((base, index) => {
    const compared = candidate[index];
    if (!Number.isFinite(base) || !Number.isFinite(compared) || base === 0) return null;
    return direction === 'lower' ? (base - compared) / base : (compared - base) / base;
  });
}

function validNumberArray(values) {
  return Array.isArray(values) && values.length > 0 && values.every(Number.isFinite);
}

function validThreeRunValues(values) {
  return Array.isArray(values) && values.length === 3 && values.every(Number.isFinite);
}

function mean(values) {
  return values.reduce((sum, value) => sum + value, 0) / values.length;
}

function meanOrNull(values) {
  return validNumberArray(values) ? mean(values) : null;
}

async function readJson(file, label, warnings) {
  const text = await readRequired(file, label, warnings);
  if (text === null) return null;
  try {
    return JSON.parse(text);
  } catch (error) {
    warnings.push(`${label}: ${path.basename(file)} JSON을 읽을 수 없습니다: ${error.message}`);
    return null;
  }
}

async function readRequired(file, label, warnings) {
  try {
    return await readFile(file, 'utf8');
  } catch (error) {
    warnings.push(`${label}: 필수 파일 ${path.basename(file)}을 읽을 수 없습니다 (${error.code ?? error.message}).`);
    return null;
  }
}

export function renderMarkdown(result) {
  const lines = [
    '# 커밋 본문 캐시 조건별 비교', '',
    `- 조건: blocks=${result.condition.blocks}, pattern=${result.condition.pattern}, load-profile=${result.condition.loadProfile}`,
    `- 판정: **${result.verdict}**`,
    `- baseline 반복 변동폭: ${percent(result.baseline.repeatVariation)}`,
    `- 최소 의미 차이: ${percent(result.baseline.minimumMeaningfulDelta)}`,
    `- 제한: ${result.limitation}`, '',
    '## 판정 이유', '',
    ...result.reasons.map((reason) => `- ${reason}`), '',
    '| Provider | p95 3회 (ms) | p95 평균 | 처리량 3회 | 처리량 평균 | p95 baseline 대비 변화율 | p95 방향 일치 | 처리량 baseline 대비 변화율 | 처리량 방향 일치 |',
    '|---|---:|---:|---:|---:|---:|:---:|---:|:---:|',
  ];
  for (const provider of PROVIDERS) {
    const data = result.providers[provider];
    lines.push(`| ${provider} | ${list(data.runs.map((run) => run.k6?.p95))} | ${number(data.means.p95)} | ${list(data.runs.map((run) => run.k6?.throughput))} | ${number(data.means.throughput)} | ${provider === 'none' ? '-' : list(data.pairedP95Changes, percent)} | ${provider === 'none' ? '-' : yesNo(data.sameDirection)} | ${provider === 'none' ? '-' : list(data.pairedThroughputChanges, percent)} | ${provider === 'none' ? '-' : yesNo(data.throughputSameDirection)} |`);
  }
  lines.push('', '## 오류와 자원 증거', '',
    '| Provider | 오류율 3회 / 평균 | dropped 3회 / 평균 | assemble Δ 3회 / 평균 | cache hit Δ 3회 / 평균 | get error Δ 3회 / 평균 | put error Δ 3회 / 평균 | heap Δ 3회 / 평균 | GC count Δ 3회 / 평균 | GC pause Δ 3회 / 평균 | Redis memory Δ 3회 / 평균 |',
    '|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|');
  for (const provider of PROVIDERS) {
    const data = result.providers[provider];
    lines.push(`| ${provider} | ${rawMean(data, 'failureRate', 'k6')} | ${rawMean(data, 'droppedIterations', 'k6')} | ${rawMean(data, 'assembleCountDelta', 'resources')} | ${rawMean(data, 'cacheHitDelta', 'resources')} | ${rawMean(data, 'cacheErrorDelta', 'resources')} | ${rawMean(data, 'cachePutErrorDelta', 'resources')} | ${rawMean(data, 'heapUsedDelta', 'resources')} | ${rawMean(data, 'gcPauseCountDelta', 'resources')} | ${rawMean(data, 'gcPauseSumDelta', 'resources')} | ${rawMean(data, 'redisUsedMemoryDelta', 'redis')} |`);
  }
  lines.push('', '## 후보별 gate와 판정 이유', '',
    '| Provider | direction | meaningfulP95 | assembleReduced | cacheHit | cacheErrors | dropped | failOpen | eligible | 이유 |',
    '|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|---|');
  for (const provider of ['caffeine', 'redis']) {
    const data = result.providers[provider];
    const gates = data.condition?.gates;
    lines.push(`| ${provider} | ${gate(gates, 'direction')} | ${gate(gates, 'meaningfulP95')} | ${gate(gates, 'assembleReduced')} | ${gate(gates, 'cacheHit')} | ${gate(gates, 'cacheErrors')} | ${gate(gates, 'dropped')} | ${gate(gates, 'failOpen')} | ${yesNo(data.condition?.eligible)} | ${(data.reasons ?? ['판정할 수 없습니다.']).join('<br>')} |`);
  }
  lines.push('', '## 증거 경고', '');
  if (result.evidence.warnings.length === 0) lines.push('- 없음');
  else for (const warning of result.evidence.warnings) lines.push(`- ${warning}`);
  lines.push('', `Redis Fail-Open 통과: ${result.evidence.redisFailOpenPassed ? '예' : '아니요'}`, '');
  return lines.join('\n');
}

function rawMean(data, field, source) {
  const values = data.runs.map((run) => {
    if (source === 'redis') return run.redisUsedMemoryDelta;
    return run[source]?.[field];
  });
  return `${list(values)} / ${number(data.means[field])}`;
}

function gate(gates, field) {
  return gates ? yesNo(gates[field]) : 'N/A';
}

function yesNo(value) {
  return typeof value === 'boolean' ? value ? '예' : '아니요' : 'N/A';
}

function list(values, formatter = number) {
  return values.map(formatter).join(', ');
}

function number(value) {
  return Number.isFinite(value) ? String(Math.round(value * 10000) / 10000) : 'N/A';
}

function percent(value) {
  return Number.isFinite(value) ? `${number(value * 100)}%` : 'N/A';
}

function parseArguments(argv) {
  const options = { redisFailOpenPassed: false };
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (argument === '--help' || argument === '-h') return { help: true };
    if (argument === '--redis-fail-open-passed') {
      options.redisFailOpenPassed = true;
      continue;
    }
    const fields = {
      '--result-root': 'resultRoot', '--blocks': 'blocks', '--pattern': 'pattern',
      '--load-profile': 'loadProfile', '--output-dir': 'outputDir',
    };
    const field = fields[argument];
    if (!field) throw new Error(`알 수 없는 옵션입니다: ${argument}`);
    const value = argv[index + 1];
    if (!value || value.startsWith('--')) throw new Error(`${argument} 값이 필요합니다.`);
    options[field] = value;
    index += 1;
  }
  if (!options.resultRoot || !options.blocks || !options.pattern || !options.loadProfile) {
    throw new Error('--result-root, --blocks, --pattern, --load-profile은 필수입니다.');
  }
  if (!/^[1-9][0-9]*$/.test(options.blocks)) throw new Error('--blocks는 양의 정수여야 합니다.');
  if (!/^(cold|hot|mixed|cold_burst|saturation)$/.test(options.pattern)) {
    throw new Error('--pattern은 cold, hot, mixed, cold_burst, saturation 중 하나여야 합니다.');
  }
  if (!/^(vus-[1-9][0-9]*|rate-[1-9][0-9]*(-[1-9][0-9]*)+)$/.test(options.loadProfile)) {
    throw new Error('--load-profile은 vus-<n> 또는 rate-<n>-<n> 형식이어야 합니다.');
  }
  options.blocks = Number(options.blocks);
  options.outputDir ??= path.join(
    options.resultRoot, 'comparisons', `blocks-${options.blocks}`, options.pattern, options.loadProfile,
  );
  return options;
}

function helpText() {
  return `커밋 본문 캐시의 동일 조건 3회 결과를 비교합니다.

사용법:
  node perf/read/commit-cache/compare_commit_cache_results.mjs \\
    --result-root <dir> --blocks <n> --pattern <pattern> --load-profile <profile> \\
    [--output-dir <dir>] \\
    [--redis-fail-open-passed]

입력:
  <result-root>/<provider>/blocks-<n>/<pattern>/<load-profile>/run-<1..3>/ 아래의
  summary.json, environment.txt, git-revision.txt, Prometheus before/after,
  그리고 Redis provider의 redis-info before/after가 필요합니다.

출력:
  <output-dir>/comparison.json 및 comparison.md
  필수 증거 누락·불일치는 가능한 결과 파일을 남기고 종료 코드 2를 반환합니다.
  이 도구는 단일 조건을 판정하며 전체 실험의 최종 provider를 선택하지 않습니다.
`;
}

async function main(argv) {
  let options;
  try {
    options = parseArguments(argv);
  } catch (error) {
    console.error(`오류: ${error.message}\n\n${helpText()}`);
    return 2;
  }
  if (options.help) {
    console.log(helpText());
    return 0;
  }
  const result = await compareResults(options);
  await mkdir(options.outputDir, { recursive: true });
  await writeFile(path.join(options.outputDir, 'comparison.json'), `${JSON.stringify(result, null, 2)}\n`);
  await writeFile(path.join(options.outputDir, 'comparison.md'), renderMarkdown(result));
  console.log(`비교 결과: ${result.verdict} (${options.outputDir})`);
  return result.verdict === 'INSUFFICIENT_DATA' ? 2 : 0;
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  main(process.argv.slice(2)).then((status) => {
    process.exitCode = status;
  }).catch((error) => {
    console.error(`오류: 비교 결과를 생성하지 못했습니다: ${error.message}`);
    process.exitCode = 2;
  });
}
