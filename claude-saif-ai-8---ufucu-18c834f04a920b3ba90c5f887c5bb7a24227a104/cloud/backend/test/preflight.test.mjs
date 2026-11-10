import test from 'node:test';
import assert from 'node:assert/strict';
import { checkDeployment, rootUrl } from '../src/preflight.mjs';

const apiBase = 'https://gateway.example';
const portalBase = 'https://downloads.example';
const json = (status, value) => new Response(JSON.stringify(value), {
  status, headers: { 'Content-Type': 'application/json; charset=utf-8' }
});

function fixture(patch = {}) {
  const calls = [];
  const request = async (url, options) => {
    calls.push({ url, options });
    if (url === `${apiBase}/healthz`) return patch.health ?? json(200, { ok: true });
    if (url === `${apiBase}/api/push`) return patch.upload ?? json(401, { error: 'Sign in' });
    if (url === `${portalBase}/api/inspectDownload`) return patch.claim ?? json(404, { error: 'Invalid download link' });
    throw new Error('unexpected network request');
  };
  return { calls, request };
}

test('readiness check probes live API and portal routes without creating a job or burning a claim', async () => {
  const { calls, request } = fixture();
  assert.deepEqual(await checkDeployment({ apiBase, portalBase, request }), { gateway: true, portal: true });
  assert.equal(calls.length, 3);
  assert.equal(calls[0].url, `${apiBase}/healthz`);
  assert.equal(calls[0].options.redirect, 'manual');
  assert.equal(calls[1].url, `${apiBase}/api/push`);
  assert.equal(calls[1].options.headers.Authorization, undefined);
  assert.equal(calls[1].options.body, '{}');
  assert.equal(calls[2].url, `${portalBase}/api/inspectDownload`);
  assert.deepEqual(JSON.parse(calls[2].options.body), {
    job: '123e4567-e89b-42d3-a456-426614174000', claim: 'invalid'
  });
});

test('demo SPA, 404 healthz and interactive login redirects cannot pass readiness', async () => {
  for (const patch of [
    { health: json(404, { error: 'not found' }) },
    { health: new Response(null, { status: 302, headers: { Location: 'https://accounts.google.com/' } }) },
    { health: new Response('SAIF demo HTML', { status: 200, headers: { 'Content-Type': 'text/html' } }) },
    { upload: new Response('demo page', { status: 200, headers: { 'Content-Type': 'text/html' } }) },
    { claim: new Response('demo page', { status: 200, headers: { 'Content-Type': 'text/html' } }) }
  ]) {
    const { request } = fixture(patch);
    await assert.rejects(checkDeployment({ apiBase, portalBase, request }), /Gateway|Website|Unauthenticated/);
  }
});

test('only clean HTTPS origins are allowed; a URL secret cannot carry credentials or paths', () => {
  assert.equal(rootUrl('https://gateway.example/', 'api'), apiBase);
  for (const value of ['http://gateway.example', 'https://gateway.example/api',
    'https://gateway.example/?key=secret', 'https://username:password@gateway.example',
    'https://gateway.example/#claim=secret', ''])
    assert.throws(() => rootUrl(value, 'api'), /HTTPS origin/);
});
