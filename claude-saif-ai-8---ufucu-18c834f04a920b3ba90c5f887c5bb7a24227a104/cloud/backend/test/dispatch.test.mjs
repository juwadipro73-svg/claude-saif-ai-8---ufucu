import test from 'node:test';
import assert from 'node:assert/strict';
import { createGithubDispatch, DEFAULT_BUILDER_WORKFLOW } from '../src/dispatch.mjs';

const jobId = '123e4567-e89b-42d3-a456-426614174000';
const repo = 'shahudgg/SaifAI-Builder';

test('gateway targets the actual Builder build.yml and dispatches only a private job UUID', async () => {
  assert.equal(DEFAULT_BUILDER_WORKFLOW, 'build.yml');
  const calls = [];
  const dispatch = createGithubDispatch({ repo, token: 'server-only-token', request: async (url, options) => {
    calls.push({ url, options });
    return { status: 204 };
  } });
  await dispatch(jobId);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, `https://api.github.com/repos/${repo}/actions/workflows/build.yml/dispatches`);
  assert.equal(calls[0].options.method, 'POST');
  assert.equal(calls[0].options.headers.Authorization, 'Bearer server-only-token');
  assert.deepEqual(JSON.parse(calls[0].options.body), { ref: 'main', inputs: { job_id: jobId } });
  await assert.rejects(dispatch('not-a-job-uuid'), /Invalid job ID/);
  assert.equal(calls.length, 1, 'invalid job IDs cannot reach GitHub');
});

test('dispatch is configurable but hides private GitHub error responses', async () => {
  const dispatch = createGithubDispatch({ repo, ref: 'release/staging', workflow: 'custom-build.yml',
    token: 'server-only-token', request: async (_url, options) => {
      assert.deepEqual(JSON.parse(options.body), { ref: 'release/staging', inputs: { job_id: jobId } });
      return { status: 422, text: async () => 'private GitHub response' };
    } });
  await assert.rejects(dispatch(jobId), error => {
    assert.equal(error.message, 'GitHub dispatch failed with status 422');
    assert.doesNotMatch(error.message, /private GitHub response|server-only-token/);
    return true;
  });
});

test('dispatch configuration refuses invalid repo, token, ref or workflow', () => {
  for (const patch of [
    { repo: '../bad' }, { token: '' }, { ref: '../main' },
    { workflow: '../build.yml' }, { workflow: 'build.yml?secret=bad' }
  ]) assert.throws(() => createGithubDispatch({ repo, token: 'server-only-token', ...patch }));
});
