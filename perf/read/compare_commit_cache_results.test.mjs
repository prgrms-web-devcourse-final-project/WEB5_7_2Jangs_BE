import assert from 'node:assert/strict';
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';

import {
  minimumMeaningfulDelta,
  parseK6Summary,
  parsePrometheus,
  repeatVariation,
  sameDirection,
} from './compare_commit_cache_results.mjs';

test('반복 변동폭을 3회 평균 대비 범위로 계산한다', () => {
  assert.equal(repeatVariation([100, 110, 90]), 20 / 100);
});

test('반복 변동폭은 비어 있거나 유한하지 않거나 평균이 0이면 계산하지 않는다', () => {
  assert.equal(repeatVariation([]), null);
  assert.equal(repeatVariation([1, Number.NaN, 2]), null);
  assert.equal(repeatVariation([-1, 0, 1]), null);
});

test('최소 의미 차이는 10퍼센트와 baseline 변동폭 중 큰 값이다', () => {
  assert.equal(minimumMeaningfulDelta(0.04), 0.10);
  assert.equal(minimumMeaningfulDelta(0.18), 0.18);
});

test('최소 의미 차이는 음수이거나 유한하지 않은 변동폭을 거부한다', () => {
  assert.equal(minimumMeaningfulDelta(-0.1), null);
  assert.equal(minimumMeaningfulDelta(Number.POSITIVE_INFINITY), null);
});

test('세 번의 변화율이 모두 같은 양의 방향일 때만 방향 일치로 본다', () => {
  assert.equal(sameDirection([12, 15, 11]), true);
  assert.equal(sameDirection([12, -2, 11]), false);
  assert.equal(sameDirection([12, 0, 11]), false);
  assert.equal(sameDirection([]), false);
  assert.equal(sameDirection([12, 15]), false);
});

test('k6 summary에서 전용 Trend와 scenario iterations 처리량을 읽는다', () => {
  const parsed = parseK6Summary({
    provider: 'none', blockCount: 20, pattern: 'hot', vus: 20, runNumber: 1,
    metrics: { metrics: {
      op_commit_get_ms: { values: { 'p(95)': 123.4, 'p(99)': 150 } },
      iterations: { values: { count: 600, rate: 20 } },
      http_reqs: { values: { count: 999, rate: 99 } },
      commit_get_failed: { values: { rate: 0.01 } },
      dropped_iterations: { values: { count: 2, rate: 0.1 } },
    } },
  });

  assert.equal(parsed.p95, 123.4);
  assert.equal(parsed.throughput, 20);
  assert.equal(parsed.failureRate, 0.01);
  assert.equal(parsed.droppedIterations, 2);
});

test('실제형 k6 summary에서 dropped metric key가 없으면 0으로 해석한다', () => {
  const parsed = parseK6Summary({
    metrics: { metrics: {
      op_commit_get_ms: { values: { 'p(95)': 123.4 } },
      iterations: { values: { rate: 20 } },
      commit_get_failed: { values: { rate: 0 } },
    } },
  });

  assert.equal(parsed.p95, 123.4);
  assert.equal(parsed.throughput, 20);
  assert.equal(parsed.failureRate, 0);
  assert.equal(parsed.droppedIterations, 0);
});

test('k6 summary의 null metric을 0으로 해석하지 않는다', () => {
  const parsed = parseK6Summary({
    metrics: { metrics: {
      op_commit_get_ms: { values: { 'p(95)': null } },
      iterations: { values: { rate: null } },
      commit_get_failed: { values: { rate: null } },
      dropped_iterations: { values: { count: null } },
    } },
  });

  assert.equal(parsed.p95, null);
  assert.equal(parsed.throughput, null);
  assert.equal(parsed.failureRate, null);
  assert.equal(parsed.droppedIterations, null);
});

test('Prometheus 표본은 metric과 label로 구분해 필요한 합계를 계산한다', () => {
  const parsed = parsePrometheus(`
commit_content_assemble_seconds_count 10
commit_content_cache_get_total{result="hit"} 20
commit_content_cache_get_total{result="error"} 1
commit_content_cache_put_total{result="error"} 2
jvm_memory_used_bytes{area="heap",id="G1 Eden Space"} 100
jvm_memory_used_bytes{area="heap",id="G1 Old Gen"} 200
jvm_memory_used_bytes{area="nonheap",id="Metaspace"} 900
jvm_gc_pause_seconds_count{action="end of minor GC"} 3
jvm_gc_pause_seconds_sum{action="end of minor GC"} 0.4
`);

  assert.equal(parsed.assembleCount, 10);
  assert.equal(parsed.cacheHit, 20);
  assert.equal(parsed.cacheError, 1);
  assert.equal(parsed.cachePutError, 2);
  assert.equal(parsed.heapUsed, 300);
  assert.equal(parsed.gcPauseCount, 3);
  assert.equal(parsed.gcPauseSum, 0.4);
});

test('완전한 3회 결과는 JSON과 한국어 Markdown 판정 자료를 생성한다', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'commit-cache-compare-valid-'));
  const output = path.join(root, 'output');
  await writeFixtureMatrix(root);

  const result = runCli(root, output, ['--redis-fail-open-passed']);
  assert.equal(result.status, 0, result.stderr);

  const json = JSON.parse(await readFile(path.join(output, 'comparison.json'), 'utf8'));
  assert.equal(json.verdict, 'CACHE_CANDIDATE_CONFIRMED');
  assert.equal(json.providers.none.runs.length, 3);
  assert.equal(json.baseline.repeatVariation, 0);
  assert.equal(json.baseline.minimumMeaningfulDelta, 0.1);
  assert.deepEqual(json.providers.caffeine.pairedP95Changes, [0.3, 0.3, 0.3]);
  assert.equal(json.providers.caffeine.sameDirection, true);
  assert.equal(json.evidence.redisFailOpenPassed, true);

  const markdown = await readFile(path.join(output, 'comparison.md'), 'utf8');
  assert.match(markdown, /커밋 본문 캐시 조건별 비교/);
  assert.match(markdown, /최소 의미 차이/);
  assert.match(markdown, /CACHE_CANDIDATE_CONFIRMED/);
  assert.match(markdown, /오류율 3회/);
  assert.match(markdown, /put error Δ 3회/);
  assert.match(markdown, /후보별 gate와 판정 이유/);
  assert.match(markdown, /meaningfulP95/);
});

test('fixture matrix에서 dropped metric key가 없어도 CLI는 불충분으로 판정하지 않는다', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'commit-cache-compare-no-dropped-key-'));
  const output = path.join(root, 'output');
  await writeFixtureMatrix(root);
  await removeDroppedMetricFromFixtureMatrix(root);

  const result = runCli(root, output, ['--redis-fail-open-passed']);
  assert.equal(result.status, 0, result.stderr);

  const json = JSON.parse(await readFile(path.join(output, 'comparison.json'), 'utf8'));
  assert.notEqual(json.verdict, 'INSUFFICIENT_DATA');
  assert.equal(json.providers.none.runs[0].k6.droppedIterations, 0);
});

test('필수 증거가 빠지면 결과 파일에 사유를 남기고 비정상 종료한다', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'commit-cache-compare-missing-'));
  const output = path.join(root, 'output');
  await writeFixtureMatrix(root);
  const missing = path.join(root, 'redis', 'blocks-20', 'hot', 'vus-20', 'run-3', 'prometheus-after.txt');
  await writeFile(missing, 'commit_content_assemble_seconds_count 1\n');

  const result = runCli(root, output, ['--redis-fail-open-passed']);
  assert.equal(result.status, 2, result.stderr);

  const json = JSON.parse(await readFile(path.join(output, 'comparison.json'), 'utf8'));
  assert.equal(json.verdict, 'INSUFFICIENT_DATA');
  assert.ok(json.evidence.warnings.some((warning) => warning.includes('redis run-3')));
});

test('summary 전체가 없어도 불충분 결과 파일을 남긴다', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'commit-cache-compare-no-summary-'));
  const output = path.join(root, 'output');
  await writeFixtureMatrix(root);
  await rm(path.join(root, 'caffeine', 'blocks-20', 'hot', 'vus-20', 'run-2', 'summary.json'));

  const result = runCli(root, output, ['--redis-fail-open-passed']);
  assert.equal(result.status, 2, result.stderr);

  const json = JSON.parse(await readFile(path.join(output, 'comparison.json'), 'utf8'));
  assert.equal(json.verdict, 'INSUFFICIENT_DATA');
  assert.ok(json.evidence.warnings.some((warning) => warning.includes('summary.json')));
});

test('비교 실행의 부하 설정이 다르면 불충분으로 판정한다', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'commit-cache-compare-load-mismatch-'));
  const output = path.join(root, 'output');
  await writeFixtureMatrix(root);
  const summaryPath = path.join(root, 'redis', 'blocks-20', 'hot', 'vus-20', 'run-3', 'summary.json');
  const summary = JSON.parse(await readFile(summaryPath, 'utf8'));
  summary.vus = 50;
  await writeFile(summaryPath, JSON.stringify(summary));

  const result = runCli(root, output, ['--redis-fail-open-passed']);
  assert.equal(result.status, 2, result.stderr);
  const json = JSON.parse(await readFile(path.join(output, 'comparison.json'), 'utf8'));
  assert.equal(json.verdict, 'INSUFFICIENT_DATA');
  assert.ok(json.evidence.warnings.some((warning) => warning.includes('부하 설정')));
});

test('경로 load profile과 summary VU 의미가 다르면 불충분으로 판정한다', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'commit-cache-compare-profile-meaning-'));
  const output = path.join(root, 'output');
  await writeFixtureMatrix(root);
  const summaryPath = path.join(root, 'none', 'blocks-20', 'hot', 'vus-20', 'run-1', 'summary.json');
  const summary = JSON.parse(await readFile(summaryPath, 'utf8'));
  summary.vus = 10;
  await writeFile(summaryPath, JSON.stringify(summary));

  const result = runCli(root, output, ['--redis-fail-open-passed']);
  assert.equal(result.status, 2, result.stderr);
  const json = JSON.parse(await readFile(path.join(output, 'comparison.json'), 'utf8'));
  assert.equal(json.verdict, 'INSUFFICIENT_DATA');
  assert.ok(json.evidence.warnings.some((warning) => warning.includes('load profile 의미')));
});

test('어느 provider에서든 dropped iteration이 있으면 INCONCLUSIVE로 판정한다', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'commit-cache-compare-dropped-'));
  const output = path.join(root, 'output');
  await writeFixtureMatrix(root);
  const summaryPath = path.join(root, 'none', 'blocks-20', 'hot', 'vus-20', 'run-1', 'summary.json');
  const summary = JSON.parse(await readFile(summaryPath, 'utf8'));
  summary.metrics.metrics.dropped_iterations.values.count = 1;
  await writeFile(summaryPath, JSON.stringify(summary));

  const result = runCli(root, output, ['--redis-fail-open-passed']);
  assert.equal(result.status, 0, result.stderr);
  const json = JSON.parse(await readFile(path.join(output, 'comparison.json'), 'utf8'));
  assert.equal(json.verdict, 'INCONCLUSIVE');
  assert.ok(json.reasons.some((reason) => reason.includes('dropped')));
});

test('의미 있는 개선과 cache put error가 충돌하면 INCONCLUSIVE로 판정한다', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'commit-cache-compare-put-error-'));
  const output = path.join(root, 'output');
  await writeFixtureMatrix(root);
  const afterPath = path.join(root, 'caffeine', 'blocks-20', 'hot', 'vus-20', 'run-2', 'prometheus-after.txt');
  const after = await readFile(afterPath, 'utf8');
  await writeFile(afterPath, after.replace(
    'commit_content_cache_put_total{result="error"} 0',
    'commit_content_cache_put_total{result="error"} 1',
  ));

  const result = runCli(root, output, ['--redis-fail-open-passed']);
  assert.equal(result.status, 0, result.stderr);
  const json = JSON.parse(await readFile(path.join(output, 'comparison.json'), 'utf8'));
  assert.equal(json.verdict, 'INCONCLUSIVE');
  assert.equal(json.providers.caffeine.runs[1].resources.cachePutErrorDelta, 1);
  assert.equal(json.providers.caffeine.condition.gates.cacheErrors, false);
});

test('p95 개선 방향이 3회 중 한 번이라도 다르면 INCONCLUSIVE로 판정한다', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'commit-cache-compare-direction-'));
  const output = path.join(root, 'output');
  await writeFixtureMatrix(root);
  await setProviderP95(root, 'caffeine', [70, 110, 70]);
  await setProviderP95(root, 'redis', [100, 100, 100]);

  const result = runCli(root, output, ['--redis-fail-open-passed']);
  assert.equal(result.status, 0, result.stderr);
  const json = JSON.parse(await readFile(path.join(output, 'comparison.json'), 'utf8'));
  assert.equal(json.verdict, 'INCONCLUSIVE');
  assert.equal(json.providers.caffeine.sameDirection, false);
  assert.ok(json.providers.caffeine.reasons.some((reason) => reason.includes('방향')));
});

test('일관된 p95 개선이 임계치에 미달하면 NO_MEANINGFUL_IMPROVEMENT로 판정한다', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'commit-cache-compare-small-delta-'));
  const output = path.join(root, 'output');
  await writeFixtureMatrix(root);
  await setProviderP95(root, 'caffeine', [95, 95, 95]);
  await setProviderP95(root, 'redis', [95, 95, 95]);

  const result = runCli(root, output, ['--redis-fail-open-passed']);
  assert.equal(result.status, 0, result.stderr);
  const json = JSON.parse(await readFile(path.join(output, 'comparison.json'), 'utf8'));
  assert.equal(json.verdict, 'NO_MEANINGFUL_IMPROVEMENT');
});

test('Redis만 의미 있게 개선돼도 Fail-Open 증거가 없으면 INCONCLUSIVE로 판정한다', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'commit-cache-compare-no-fail-open-'));
  const output = path.join(root, 'output');
  await writeFixtureMatrix(root);
  await setProviderP95(root, 'caffeine', [95, 95, 95]);

  const result = runCli(root, output);
  assert.equal(result.status, 0, result.stderr);
  const json = JSON.parse(await readFile(path.join(output, 'comparison.json'), 'utf8'));
  assert.equal(json.verdict, 'INCONCLUSIVE');
  assert.ok(json.reasons.some((reason) => reason.includes('Fail-Open')));
});

test('load profile로 VU 조건을 정확히 선택한다', async () => {
  const root = await mkdtemp(path.join(tmpdir(), 'commit-cache-compare-profile-'));
  const output = path.join(root, 'output');
  await writeFixtureMatrix(root);
  const otherProfile = path.join(root, 'none', 'blocks-20', 'hot', 'vus-50', 'run-1');
  await mkdir(otherProfile, { recursive: true });
  await writeFile(path.join(otherProfile, 'summary.json'), '{"not":"selected"}');
  const result = spawnSync(process.execPath, [
    path.resolve('perf/read/compare_commit_cache_results.mjs'),
    '--result-root', root,
    '--blocks', '20',
    '--pattern', 'hot',
    '--load-profile', 'vus-20',
    '--output-dir', output,
    '--redis-fail-open-passed',
  ], { encoding: 'utf8' });

  assert.equal(result.status, 0, result.stderr);
  const json = JSON.parse(await readFile(path.join(output, 'comparison.json'), 'utf8'));
  assert.equal(json.condition.loadProfile, 'vus-20');
  assert.equal(json.providers.none.runs[0].k6.p95, 100);
});

test('경로 구분자로 탈출하려는 load profile을 거부한다', () => {
  const result = spawnSync(process.execPath, [
    path.resolve('perf/read/compare_commit_cache_results.mjs'),
    '--result-root', '/tmp/results',
    '--blocks', '20',
    '--pattern', 'hot',
    '--load-profile', '../vus-20',
  ], { encoding: 'utf8' });

  assert.equal(result.status, 2);
  assert.match(result.stderr, /--load-profile은/);
});

test('허용하지 않은 pattern 경로를 거부한다', () => {
  const result = spawnSync(process.execPath, [
    path.resolve('perf/read/compare_commit_cache_results.mjs'),
    '--result-root', '/tmp/results',
    '--blocks', '20',
    '--pattern', '../hot',
    '--load-profile', 'vus-20',
  ], { encoding: 'utf8' });

  assert.equal(result.status, 2);
  assert.match(result.stderr, /--pattern은/);
});

function runCli(root, output, extra = []) {
  return spawnSync(process.execPath, [
    path.resolve('perf/read/compare_commit_cache_results.mjs'),
    '--result-root', root,
    '--blocks', '20',
    '--pattern', 'hot',
    '--load-profile', 'vus-20',
    '--output-dir', output,
    ...extra,
  ], { encoding: 'utf8' });
}

async function writeFixtureMatrix(root) {
  const providers = {
    none: { p95: 100, throughput: 20, assemble: 100, hit: 0, heap: 1000, redis: null },
    caffeine: { p95: 70, throughput: 26, assemble: 10, hit: 90, heap: 1300, redis: null },
    redis: { p95: 75, throughput: 24, assemble: 12, hit: 88, heap: 1050, redis: 400 },
  };
  for (const [provider, values] of Object.entries(providers)) {
    for (let run = 1; run <= 3; run += 1) {
      const dir = path.join(root, provider, 'blocks-20', 'hot', 'vus-20', `run-${run}`);
      await mkdir(dir, { recursive: true });
      const summary = {
        provider, blockCount: 20, pattern: 'hot', loadProfile: 'vus-20', vus: 20, runNumber: run,
        metrics: { metrics: {
          op_commit_get_ms: { values: { 'p(95)': values.p95, 'p(99)': values.p95 + 10 } },
          iterations: { values: { count: 600, rate: values.throughput } },
          commit_get_failed: { values: { rate: 0 } },
          dropped_iterations: { values: { count: 0, rate: 0 } },
        } },
      };
      await writeFile(path.join(dir, 'summary.json'), JSON.stringify(summary));
      await writeFile(path.join(dir, 'environment.txt'), [
        `provider=${provider}`, 'blocks_per_commit=20', 'pattern=hot', 'load_profile=vus-20', `run_no=${run}`,
        'run_id=fixture', 'user_count=20', 'docs_per_user=2', 'main_commits=10',
      ].join('\n'));
      await writeFile(path.join(dir, 'git-revision.txt'), 'fixture-revision\n');
      await writeFile(path.join(dir, 'prometheus-before.txt'), prometheus(0, 0, 0, 100, 0, 0));
      await writeFile(path.join(dir, 'prometheus-after.txt'), prometheus(
        values.assemble, values.hit, 0, values.heap, 2, 0.2,
      ));
      if (values.redis !== null) {
        await writeFile(path.join(dir, 'redis-info-before.txt'), 'used_memory:1000\n');
        await writeFile(path.join(dir, 'redis-info-after.txt'), `used_memory:${1000 + values.redis}\n`);
      }
    }
  }
}

function prometheus(assemble, hit, error, heap, gcCount, gcSum) {
  return [
    `commit_content_assemble_seconds_count ${assemble}`,
    `commit_content_cache_get_total{result="hit"} ${hit}`,
    `commit_content_cache_get_total{result="error"} ${error}`,
    'commit_content_cache_put_total{result="error"} 0',
    `jvm_memory_used_bytes{area="heap",id="old"} ${heap}`,
    `jvm_gc_pause_seconds_count ${gcCount}`,
    `jvm_gc_pause_seconds_sum ${gcSum}`,
    '',
  ].join('\n');
}

async function setProviderP95(root, provider, values) {
  for (let run = 1; run <= 3; run += 1) {
    const summaryPath = path.join(root, provider, 'blocks-20', 'hot', 'vus-20', `run-${run}`, 'summary.json');
    const summary = JSON.parse(await readFile(summaryPath, 'utf8'));
    summary.metrics.metrics.op_commit_get_ms.values['p(95)'] = values[run - 1];
    await writeFile(summaryPath, JSON.stringify(summary));
  }
}

async function removeDroppedMetricFromFixtureMatrix(root) {
  for (const provider of ['none', 'caffeine', 'redis']) {
    for (let run = 1; run <= 3; run += 1) {
      const summaryPath = path.join(root, provider, 'blocks-20', 'hot', 'vus-20', `run-${run}`, 'summary.json');
      const summary = JSON.parse(await readFile(summaryPath, 'utf8'));
      delete summary.metrics.metrics.dropped_iterations;
      await writeFile(summaryPath, JSON.stringify(summary));
    }
  }
}
