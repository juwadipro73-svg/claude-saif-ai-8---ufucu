/**
 * Portal contract tests.
 * Spin up the portal server in a child process with and without
 * SAIF_CLOUD_API_BASE_URL, then probe the endpoints the app's CI
 * preflight (check-deployment.mjs) relies on.
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import net from 'node:net';

function startPortal(env = {}) {
  const child = spawn('npx', ['tsx', 'server.ts'], {
    cwd: new URL('..', import.meta.url).pathname,
    env: { ...process.env, ...env, DISABLE_HMR: 'true' },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  let stdout = '', stderr = '';
  child.stdout.on('data', (c) => (stdout += c));
  child.stderr.on('data', (c) => (stderr += c));
  return { child, getStdout: () => stdout, getStderr: () => stderr };
}

function waitFor(portal, marker = 'Portal listening', timeoutMs = 15000) {
  return new Promise((resolve, reject) => {
    const start = Date.now();
    const check = () => {
      if (portal.getStdout().includes(marker) || portal.getStderr().includes(marker)) {
        return resolve();
      }
      if (Date.now() - start > timeoutMs) {
        return reject(new Error('Portal did not start: ' + portal.getStderr().slice(-500)));
      }
      setTimeout(check, 100);
    };
    check();
  });
}

function httpGet(port, path) {
  return fetch(`http://127.0.0.1:${port}${path}`, {
    signal: AbortSignal.timeout(8000),
  });
}

function httpPost(port, path, body) {
  return fetch(`http://127.0.0.1:${port}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(8000),
  });
}

function getPort() {
  return new Promise((resolve, reject) => {
    const srv = net.createServer();
    srv.unref();
    srv.listen(0, '127.0.0.1', () => {
      const port = srv.address().port;
      srv.close(() => resolve(port));
    });
    srv.on('error', reject);
  });
}

test('demo mode: /healthz returns 200', async () => {
  const port = await getPort();
  const portal = startPortal({ PORT: String(port) });
  await waitFor(portal);
  const res = await httpGet(port, '/healthz');
  assert.equal(res.status, 200);
  const body = await res.json();
  assert.equal(body.status, 'ok');
  assert.equal(body.mode, 'demo');
  portal.child.kill();
});

test('demo mode: /api/vault/:id returns demo vault', async () => {
  const port = await getPort();
  const portal = startPortal({ PORT: String(port) });
  await waitFor(portal);
  const res = await httpGet(port, '/api/vault/saif_demo_vault_101');
  assert.equal(res.status, 200);
  const body = await res.json();
  assert.equal(body.vaultId, 'saif_demo_vault_101');
  assert.equal(body.gatewayMode, false);
  assert.ok(body.files.length > 0);
  portal.child.kill();
});

test('demo mode: missing vault returns 404', async () => {
  const port = await getPort();
  const portal = startPortal({ PORT: String(port) });
  await waitFor(portal);
  const res = await httpGet(port, '/api/vault/nonexistent');
  assert.equal(res.status, 404);
  portal.child.kill();
});

test('production mode: /healthz returns degraded when gateway unreachable', async () => {
  const port = await getPort();
  const portal = startPortal({ PORT: String(port), SAIF_CLOUD_API_BASE_URL: 'http://127.0.0.1:1' });
  await waitFor(portal);
  const res = await httpGet(port, '/healthz');
  assert.equal(res.status, 502);
  const body = await res.json();
  assert.equal(body.status, 'degraded');
  assert.ok(body.gatewayError);
  portal.child.kill();
});

test('production mode: /api/push returns 501 (uploads must go to gateway)', async () => {
  const port = await getPort();
  const portal = startPortal({ PORT: String(port), SAIF_CLOUD_API_BASE_URL: 'http://127.0.0.1:1' });
  await waitFor(portal);
  const res = await httpPost(port, '/api/push', {
    files: [{ name: 'x.apk', data: 'x', mimeType: 'application/vnd.android.package-archive' }],
  });
  assert.equal(res.status, 501);
  const body = await res.json();
  assert.equal(body.error, 'GATEWAY_ONLY');
  portal.child.kill();
});

test('production mode: /api/vault/:id returns GATEWAY_UNREACHABLE when gateway is down', async () => {
  const port = await getPort();
  const portal = startPortal({ PORT: String(port), SAIF_CLOUD_API_BASE_URL: 'http://127.0.0.1:1' });
  await waitFor(portal);
  const res = await httpGet(port, '/api/vault/test-job-id');
  assert.equal(res.status, 502);
  const body = await res.json();
  assert.equal(body.error, 'GATEWAY_UNREACHABLE');
  portal.child.kill();
});
