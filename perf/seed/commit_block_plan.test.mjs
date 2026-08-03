import assert from 'node:assert/strict';
import test from 'node:test';

import { buildCommitBlockPlan } from './commit_block_plan.mjs';

test('첫 commit은 changeRate와 관계없이 모든 block을 신규 생성한다', () => {
  const plan = buildCommitBlockPlan({
    key: 'doc',
    commitIndex: 0,
    blockCount: 3,
    changeRate: 0.1,
    previousBlockIds: [],
  });

  assert.deepEqual(plan.blockOrders, ['doc-c0-b0', 'doc-c0-b1', 'doc-c0-b2']);
  assert.deepEqual(plan.blocks, [
    { id: 'doc-c0-b0', type: 'paragraph', data: { text: 'doc-text-0' } },
    { id: 'doc-c0-b1', type: 'paragraph', data: { text: 'doc-text-1' } },
    { id: 'doc-c0-b2', type: 'paragraph', data: { text: 'doc-text-2' } },
  ]);
});

test('10% 변경률은 10개 중 1개만 신규 생성하고 9개를 재사용한다', () => {
  const previousBlockIds = Array.from({ length: 10 }, (_, index) => `previous-${index}`);
  const plan = buildCommitBlockPlan({
    key: 'doc',
    commitIndex: 1,
    blockCount: 10,
    changeRate: 0.1,
    previousBlockIds,
  });

  assert.equal(plan.blocks.length, 1);
  assert.equal(plan.blocks[0].id, 'doc-c1-b1');
  assert.deepEqual(plan.blockOrders, [
    'previous-0',
    'doc-c1-b1',
    ...previousBlockIds.slice(2),
  ]);
});

test('변경 구간은 commit 번호에 따라 순환하며 회전한다', () => {
  const previousBlockIds = Array.from({ length: 5 }, (_, index) => `previous-${index}`);
  const plan = buildCommitBlockPlan({
    key: 'doc',
    commitIndex: 2,
    blockCount: 5,
    changeRate: 0.4,
    previousBlockIds,
  });

  assert.deepEqual(plan.blocks.map((block) => block.id), ['doc-c2-b4', 'doc-c2-b0']);
  assert.deepEqual(plan.blockOrders, [
    'doc-c2-b0',
    'previous-1',
    'previous-2',
    'previous-3',
    'doc-c2-b4',
  ]);
});

test('변경률 1.0은 이후 commit에서도 모든 block을 신규 생성한다', () => {
  const plan = buildCommitBlockPlan({
    key: 'doc',
    commitIndex: 3,
    blockCount: 3,
    changeRate: 1,
    previousBlockIds: ['previous-0', 'previous-1', 'previous-2'],
  });

  assert.deepEqual(plan.blockOrders, ['doc-c3-b0', 'doc-c3-b1', 'doc-c3-b2']);
  assert.equal(plan.blocks.length, 3);
});

test('changeRate가 0 초과 1 이하가 아니면 예외를 던진다', () => {
  for (const changeRate of [0, -0.1, 1.1]) {
    assert.throws(() => buildCommitBlockPlan({
      key: 'doc',
      commitIndex: 0,
      blockCount: 3,
      changeRate,
      previousBlockIds: [],
    }), /changeRate/);
  }
});

test('previousBlockIds 길이가 0 또는 blockCount가 아니면 예외를 던진다', () => {
  assert.throws(() => buildCommitBlockPlan({
    key: 'doc',
    commitIndex: 1,
    blockCount: 3,
    changeRate: 0.5,
    previousBlockIds: ['previous-0'],
  }), /previousBlockIds/);
});
