export function buildCommitBlockPlan({
  key,
  commitIndex,
  blockCount,
  changeRate,
  previousBlockIds,
}) {
  if (!(changeRate > 0 && changeRate <= 1)) {
    throw new Error('changeRate must be greater than 0 and less than or equal to 1');
  }
  if (!Array.isArray(previousBlockIds)
    || (previousBlockIds.length !== 0 && previousBlockIds.length !== blockCount)) {
    throw new Error('previousBlockIds length must be 0 or blockCount');
  }

  const firstCommit = previousBlockIds.length === 0;
  const changedCount = firstCommit
    ? blockCount
    : Math.max(1, Math.round(blockCount * changeRate));
  const start = firstCommit ? 0 : (commitIndex * changedCount) % blockCount;
  const blockOrders = firstCommit ? new Array(blockCount) : previousBlockIds.slice();
  const blocks = [];

  for (let offset = 0; offset < changedCount; offset += 1) {
    const index = (start + offset) % blockCount;
    const id = `${key}-c${commitIndex}-b${index}`;
    blocks.push({
      id,
      type: 'paragraph',
      data: {
        text: `${key}-text-${index}`,
      },
    });
    blockOrders[index] = id;
  }

  return { blocks, blockOrders };
}
