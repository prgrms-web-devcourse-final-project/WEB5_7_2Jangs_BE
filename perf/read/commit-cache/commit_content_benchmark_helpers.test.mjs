import test from 'node:test';
import assert from 'node:assert/strict';
import {
  benchmarkTargetCount,
  mixedTargetIndex,
  summaryDataWithoutAuth,
  summaryLoad,
  utf8ByteLength,
  validBlock,
} from './commit_content_benchmark_helpers.mjs';

test('확장 데이터셋은 캐시 최대 크기보다 충분히 크다', () => {
  assert.equal(benchmarkTargetCount(20, 5, 20), 2000);
  assert.ok(benchmarkTargetCount(20, 5, 20) > 400);
});

test('Mixed는 요청의 80%를 4개 hot key로 보내고 20%는 cold pool 전체를 순회한다', () => {
  const indexes = Array.from({ length: 480 }, (_, iteration) => mixedTargetIndex(iteration, 100, 4));
  const hotIndexes = indexes.filter((index) => index < 4);
  const coldIndexes = indexes.filter((index) => index >= 4);

  assert.equal(hotIndexes.length, 384);
  assert.equal(coldIndexes.length, 96);
  assert.deepEqual([...new Set(hotIndexes)].sort((a, b) => a - b), [0, 1, 2, 3]);
  assert.deepEqual(
    [...new Set(coldIndexes)].sort((a, b) => a - b),
    Array.from({ length: 96 }, (_, index) => index + 4),
  );
});

test('UTF-8 바이트 길이는 ASCII, 한글, 보충 평면 문자를 정확히 계산한다', () => {
  assert.equal(utf8ByteLength('abc'), 3);
  assert.equal(utf8ByteLength('한글'), 6);
  assert.equal(utf8ByteLength('😀'), 4);
  assert.equal(utf8ByteLength('a한😀'), 8);
});

test('유효한 content 배열의 지정 block 형태를 검증한다', () => {
  const content = [
    { id: 'first', data: { text: '첫 블록' } },
    { id: 'middle', data: { text: '중간 블록' } },
    { id: 'last', data: { text: '마지막 블록' } },
  ];

  assert.equal(validBlock(content, 0), true);
  assert.equal(validBlock(content, 1), true);
  assert.equal(validBlock(content, 2), true);
});

test('잘못된 content 또는 block은 유효하지 않다', () => {
  assert.equal(validBlock(undefined, 0), false);
  assert.equal(validBlock({}, 0), false);
  assert.equal(validBlock([], 0), false);
  assert.equal(validBlock([{ data: { text: 'id 없음' } }], 0), false);
  assert.equal(validBlock([{ id: 'text 없음', data: {} }], 0), false);
  assert.equal(validBlock([{ id: 'text 타입 오류', data: { text: 1 } }], 0), false);
  assert.equal(validBlock([{ id: 'index 범위', data: { text: '정상' } }], 1), false);
});

test('constant-vus와 ramping-arrival-rate summary 부하 정보를 분리한다', () => {
  assert.deepEqual(summaryLoad({ executor: 'constant-vus', vus: 10 }), { vus: 10 });
  assert.deepEqual(summaryLoad({
    executor: 'ramping-arrival-rate',
    stages: [{ target: 25 }, { target: 50 }, { target: 200 }],
  }), { rate: 200 });
});

test('summary setup 데이터에서 인증 필드를 제거하고 비민감 target metadata를 보존한다', () => {
  const input = {
    metrics: { iterations: { values: { count: 3 } } },
    setup_data: {
      users: [{
        userNo: 1,
        cookie: 'test-auth-placeholder',
        commits: [{ userNo: 1, docId: 81, commitId: 810, cookie: 'test-auth-placeholder' }],
      }],
      sessionId: 'test-auth-placeholder',
    },
  };

  const summaryData = summaryDataWithoutAuth(input);

  assert.equal('cookie' in summaryData.setup_data.users[0], false);
  assert.equal('cookie' in summaryData.setup_data.users[0].commits[0], false);
  assert.equal('sessionId' in summaryData.setup_data, false);
  assert.deepEqual(summaryData.setup_data.users[0].commits[0], {
    userNo: 1,
    docId: 81,
    commitId: 810,
  });
  assert.equal(summaryData.metrics, input.metrics);
});
