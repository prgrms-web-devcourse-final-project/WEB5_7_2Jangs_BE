import http from 'node:http';
import { writeFileSync } from 'node:fs';
import path from 'node:path';

function readInteger(flag, fallback, { min, max }) {
  const index = process.argv.indexOf(flag);
  if (index >= 0 && !process.argv[index + 1]) {
    throw new Error(`${flag} requires a value`);
  }
  const value = index >= 0 ? Number(process.argv[index + 1]) : fallback;
  if (!Number.isInteger(value) || value < min || value > max) {
    throw new Error(`${flag} must be an integer between ${min} and ${max}`);
  }
  return value;
}

function readRequiredPath(flag) {
  const index = process.argv.indexOf(flag);
  const value = index >= 0 ? process.argv[index + 1] : '';
  if (!value || !path.isAbsolute(value)) {
    throw new Error(`${flag} requires an absolute path`);
  }
  return value;
}

const port = readInteger('--port', 0, { min: 0, max: 65_535 });
const lifetimeMs = readInteger('--lifetime-ms', 600_000, { min: 1, max: 3_600_000 });
const readyFile = readRequiredPath('--ready-file');
let setupComplete = false;
let released = false;

function json(response, status, body) {
  response.writeHead(status, { 'Content-Type': 'application/json' });
  response.end(JSON.stringify(body));
}

const server = http.createServer((request, response) => {
  if (request.method === 'GET' && request.url === '/gate/health') {
    json(response, 200, { status: 'UP' });
    return;
  }
  if (request.method === 'POST' && request.url === '/gate/setup-complete') {
    setupComplete = true;
    json(response, 200, { setupComplete });
    return;
  }
  if (request.method === 'GET' && request.url === '/gate/status') {
    json(response, 200, { setupComplete, released });
    return;
  }
  if (request.method === 'POST' && request.url === '/gate/release') {
    released = true;
    json(response, 200, { released });
    return;
  }
  if (request.method === 'GET' && request.url === '/gate/release') {
    json(response, released ? 200 : 425, { released });
    return;
  }
  json(response, 404, { error: 'not_found' });
});

server.listen(port, '127.0.0.1', () => {
  const address = server.address();
  try {
    writeFileSync(readyFile, `${address.port}\n`, { flag: 'wx', mode: 0o600 });
  } catch (error) {
    server.close();
    throw error;
  }
  process.stdout.write(`measurement gate listening on http://127.0.0.1:${address.port}\n`);
});

const lifetime = setTimeout(() => {
  server.close(() => process.exit(2));
}, lifetimeMs);

function shutdown() {
  clearTimeout(lifetime);
  server.close(() => process.exit(0));
}

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
