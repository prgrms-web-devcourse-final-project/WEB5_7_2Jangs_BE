#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

if (process.argv.length < 4) {
  console.error('Usage: node perf/delete/bulk/compare_delete_summary.mjs <dev-summary.json> <refactor-summary.json>');
  process.exit(1);
}

const dev = JSON.parse(fs.readFileSync(path.resolve(process.argv[2]), 'utf-8'));
const ref = JSON.parse(fs.readFileSync(path.resolve(process.argv[3]), 'utf-8'));

function getMetric(summary, name, key) {
  const m = summary.metrics?.[name];
  if (!m) return null;
  if (m.values && typeof m.values === 'object') return m.values[key] ?? null;
  if (key === 'rate') return m.value ?? null;
  return m[key] ?? null;
}

function pct(base, target) {
  if (base === null || target === null || base === 0) return null;
  return ((target - base) / base) * 100;
}

function f(v, d = 2) {
  if (v === null || Number.isNaN(v)) return '-';
  return Number(v).toFixed(d);
}

function row(label, metric, key, digits = 2) {
  const b = getMetric(dev, metric, key);
  const t = getMetric(ref, metric, key);
  const delta = pct(b, t);
  const direction = delta === null ? '-' : delta < 0 ? 'improved' : delta > 0 ? 'regressed' : 'same';

  return {
    label,
    base: f(b, digits),
    target: f(t, digits),
    delta: delta === null ? '-' : `${f(delta, 2)}%`,
    direction,
  };
}

const rows = [
  row('http_req_duration p95 (ms)', 'http_req_duration', 'p(95)'),
  row('http_req_duration avg (ms)', 'http_req_duration', 'avg'),
  row('doc search p95 (ms)', 'op_doc_search_ms', 'p(95)'),
  row('doc delete p50 (ms)', 'op_doc_delete_ms', 'med'),
  row('doc delete p95 (ms)', 'op_doc_delete_ms', 'p(95)'),
  row('doc delete avg (ms)', 'op_doc_delete_ms', 'avg'),
  row('http_req_failed rate', 'http_req_failed', 'rate', 4),
  row('delete_failed rate', 'delete_failed', 'rate', 4),
];

console.log('# Delete Benchmark Comparison');
console.log(`- base(dev): ${path.resolve(process.argv[2])}`);
console.log(`- target(refactor): ${path.resolve(process.argv[3])}`);
console.log('');
console.log('| Metric | dev | refactor | Delta | Direction |');
console.log('|---|---:|---:|---:|---|');
for (const r of rows) {
  console.log(`| ${r.label} | ${r.base} | ${r.target} | ${r.delta} | ${r.direction} |`);
}
