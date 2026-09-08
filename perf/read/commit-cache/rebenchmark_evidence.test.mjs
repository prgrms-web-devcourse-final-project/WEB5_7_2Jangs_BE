import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const evidencePath = new URL(
  './results/evidence/rebenchmark-20260827-summary.json',
  import.meta.url,
);
const evidence = JSON.parse(await readFile(evidencePath, 'utf8'));
const fieldIndex = Object.fromEntries(evidence.runFields.map((field, index) => [field, index]));

function values(pattern, provider, field) {
  return evidence.runs[pattern][provider].map((run) => run[fieldIndex[field]]);
}

function mean(numbers) {
  return numbers.reduce((sum, number) => sum + number, 0) / numbers.length;
}

function pairedImprovement(pattern, field) {
  const none = values(pattern, 'none', field);
  const caffeine = values(pattern, 'caffeine', field);
  return none.map((baseline, index) => (baseline - caffeine[index]) / baseline * 100);
}

function pairedThroughputImprovement(pattern) {
  const none = values(pattern, 'none', 'iterations');
  const caffeine = values(pattern, 'caffeine', 'iterations');
  return none.map((baseline, index) => (caffeine[index] - baseline) / baseline * 100);
}

function assertApprox(actual, expected, precision = 0.001) {
  assert.ok(Math.abs(actual - expected) <= precision, `expected ${expected}, actual ${actual}`);
}

test('재측정 evidence는 조건별 provider 5회를 보존한다', () => {
  assert.equal(evidence.condition.targetCount, 2000);
  assert.equal(evidence.condition.cacheMaximumSize, 400);
  assert.equal(evidence.condition.durationSeconds, 60);
  for (const pattern of ['hot', 'mixed']) {
    assert.equal(evidence.runs[pattern].none.length, 5);
    assert.equal(evidence.runs[pattern].caffeine.length, 5);
  }
});

test('보고서의 p50과 처리량 paired 평균을 원자료에서 재계산한다', () => {
  assertApprox(mean(pairedImprovement('hot', 'p50Ms')), 22.4632);
  assertApprox(mean(pairedThroughputImprovement('hot')), 12.8576);
  assertApprox(mean(pairedImprovement('mixed', 'p50Ms')), 15.7227);
  assertApprox(mean(pairedThroughputImprovement('mixed')), 6.7254);
});

test('보고서의 provider별 절대 처리량을 원자료에서 재계산한다', () => {
  assertApprox(mean(values('hot', 'none', 'iterations')) / 60, 321.5233);
  assertApprox(mean(values('hot', 'caffeine', 'iterations')) / 60, 362.7233);
  assertApprox(mean(values('mixed', 'none', 'iterations')) / 60, 312.8533);
  assertApprox(mean(values('mixed', 'caffeine', 'iterations')) / 60, 333.8933);
});

test('20개 실행 모두 요청 실패와 dropped iteration이 없다', () => {
  for (const pattern of ['hot', 'mixed']) {
    for (const provider of ['none', 'caffeine']) {
      assert.deepEqual(values(pattern, provider, 'commitGetFailureRate'), [0, 0, 0, 0, 0]);
      assert.deepEqual(values(pattern, provider, 'droppedIterations'), [0, 0, 0, 0, 0]);
    }
  }
});

test('Hot과 Mixed의 cache 동작 및 assemble 감소를 검산한다', () => {
  assert.deepEqual(values('hot', 'caffeine', 'cacheMissDelta'), [0, 0, 0, 0, 0]);
  assert.deepEqual(values('hot', 'caffeine', 'assembleDelta'), [0, 0, 0, 0, 0]);

  const mixedHits = values('mixed', 'caffeine', 'cacheHitDelta');
  const mixedMisses = values('mixed', 'caffeine', 'cacheMissDelta');
  const mixedEvictions = values('mixed', 'caffeine', 'cacheEvictionDelta');
  const mixedAssembles = values('mixed', 'caffeine', 'assembleDelta');
  assert.deepEqual(mixedMisses, mixedAssembles);
  assert.ok(mixedEvictions.every((value) => value > 0));

  const hitRatios = mixedHits.map((hit, index) => hit / (hit + mixedMisses[index]) * 100);
  assertApprox(Math.min(...hitRatios), evidence.derived.mixed.cacheHitRatioPercentRange[0], 0.01);
  assertApprox(Math.max(...hitRatios), evidence.derived.mixed.cacheHitRatioPercentRange[1], 0.01);

  const assembleReduction = (1 - mean(mixedAssembles) / mean(values('mixed', 'none', 'assembleDelta'))) * 100;
  assertApprox(assembleReduction, evidence.derived.mixed.assembleReductionPercent, 0.01);
});
