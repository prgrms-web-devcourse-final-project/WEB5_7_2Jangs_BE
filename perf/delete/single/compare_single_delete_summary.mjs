#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

if (process.argv.length < 4) {
  console.error('Usage: node compare_single_delete_summary.mjs <base-summary.json> <target-summary.json>');
  process.exit(1);
}

const basePath = path.resolve(process.argv[2]);
const targetPath = path.resolve(process.argv[3]);

const base = JSON.parse(fs.readFileSync(basePath, 'utf-8'));
const target = JSON.parse(fs.readFileSync(targetPath, 'utf-8'));

function pickDeleteMetric(summary) {
  const keys = Object.keys(summary.metrics || {});
  const metric = keys.find((k) => /^op_(doc|branch|commit)_delete_ms$/.test(k));
  if (!metric) {
    throw new Error('Could not find delete metric (op_doc_delete_ms / op_branch_delete_ms / op_commit_delete_ms)');
  }
  return metric;
}

function get(summary, metric, key) {
  const m = summary.metrics?.[metric];
  if (!m) return null;
  if (typeof m[key] !== 'undefined') return m[key];
  if (m.values && typeof m.values === 'object') return m.values[key] ?? null;
  return null;
}

function pct(baseValue, targetValue) {
  if (baseValue === null || targetValue === null || baseValue === 0) return null;
  return ((targetValue - baseValue) / baseValue) * 100;
}

function fmt(v, d = 2) {
  if (v === null || Number.isNaN(v)) return '-';
  return Number(v).toFixed(d);
}

const baseDeleteMetric = pickDeleteMetric(base);
const targetDeleteMetric = pickDeleteMetric(target);

if (baseDeleteMetric !== targetDeleteMetric) {
  console.error(`Delete metric mismatch: ${baseDeleteMetric} vs ${targetDeleteMetric}`);
  process.exit(2);
}

const rows = [
  ['delete p50 (ms)', baseDeleteMetric, 'med'],
  ['delete p95 (ms)', baseDeleteMetric, 'p(95)'],
  ['delete avg (ms)', baseDeleteMetric, 'avg'],
  ['http_req_duration p95 (ms)', 'http_req_duration', 'p(95)'],
  ['http_req_duration avg (ms)', 'http_req_duration', 'avg'],
  ['http_req_failed rate', 'http_req_failed', 'rate'],
  ['delete_failed rate', 'delete_failed', 'rate'],
];

console.log('# Single-User Heavy Delete Comparison');
console.log(`- base:   ${basePath}`);
console.log(`- target: ${targetPath}`);
console.log(`- metric: ${baseDeleteMetric}`);
console.log('');
console.log('| Metric | base | target | Delta | Direction |');
console.log('|---|---:|---:|---:|---|');

for (const [label, metric, key] of rows) {
  const b = get(base, metric, key);
  const t = get(target, metric, key);
  const d = pct(b, t);
  const dir = d === null ? '-' : d < 0 ? 'improved' : d > 0 ? 'regressed' : 'same';
  const digits = key === 'rate' ? 4 : 2;
  console.log(`| ${label} | ${fmt(b, digits)} | ${fmt(t, digits)} | ${d === null ? '-' : `${fmt(d, 2)}%`} | ${dir} |`);
}
