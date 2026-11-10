import test from 'node:test';
import assert from 'node:assert/strict';
import { Readable } from 'node:stream';
import { createApp } from '../src/server.mjs';

const secret = 'backend-worker-callback-secret-long-enough';
const jobId = '123e4567-e89b-42d3-a456-426614174000';
const driveFileId = 'drive-file-abc123';
const folderId = 'private-folder';
const copy = value => value === undefined ? undefined : structuredClone(value);

class MemoryDB {
  constructor() { this.store = new Map(); }
  ref(path) {
    const db = this;
    const key = String(path).replace(/^\/+|\/+$/g, '');
    return {
      child(sub) { return db.ref(`${key}/${sub}`); },
      async get() { return { val: () => copy(db.store.get(key)) ?? null }; },
      async set(value) { db.store.set(key, copy(value)); },
      async update(patch) { db.store.set(key, { ...db.store.get(key), ...copy(patch) }); },
      async transaction(fn) {
        const next = fn(copy(db.store.get(key)) ?? null);
        if (next === undefined) return { committed: false };
        db.store.set(key, copy(next));
        return { committed: true, snapshot: { val: () => copy(next) } };
      }
    };
  }
}

async function fixture(t) {
  const db = new MemoryDB();
  const uploaded = new Set();
  const bucket = { file: key => ({
    async save(content) { db.store.set(`gcs/${key}`, String(content)); },
    async getMetadata() {
      if (!uploaded.has(key)) throw new Error('Missing private upload');
      return [{ size: 120, contentType: 'image/png' }];
    },
    async getSignedUrl() { return [`https://storage.googleapis.com/private/${key}?short-signed-url=yes`]; }
  }) };
  const drive = { files: { async get(options) {
    if (options.fileId !== driveFileId) throw new Error('Unknown file');
    if (options.alt === 'media') return { data: Readable.from([Buffer.from('private-apk-bytes')]) };
    return { data: { id: driveFileId, name: 'Runner.apk', size: 17,
      mimeType: 'application/vnd.android.package-archive', parents: [folderId], trashed: false } };
  } }, permissions: { async list() { return { data: { permissions: [{ type: 'user', role: 'owner' }] } }; } } };
  const app = createApp({ db, bucket, drive,
    config: { appCheckAppId: 'firebase-app-id', downloadSecret: 'download-claim-secret-must-be-long-enough', workerSecret: secret, driveFolderId: folderId },
    auth: { async verifyIdToken(token) { if (!['ownerA', 'ownerB'].includes(token)) throw new Error('invalid'); return { uid: token }; } },
    appCheck: { async verifyToken(token) {
      if (!['attested', 'attested-other'].includes(token)) throw new Error('invalid');
      return { appId: token === 'attested' ? 'firebase-app-id' : 'other-app-id' };
    } },
    dispatch: async id => { db.store.set(`dispatched/${id}`, true); }
  });
  const server = app.listen(0, '127.0.0.1');
  await new Promise(resolve => server.once('listening', resolve));
  t.after(() => server.close());
  const url = `http://127.0.0.1:${server.address().port}`;
  const post = (path, body, token = 'ownerA', check = 'attested') => fetch(url + path, {
    method: 'POST', headers: { 'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(check ? { 'X-Firebase-AppCheck': check } : {}) }, body: JSON.stringify(body)
  });
  const worker = (path, body, bearer = secret) => fetch(url + path, {
    method: 'POST', headers: { Authorization: `Bearer ${bearer}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  const start = () => post('/api/requestBuild', { jobId, kind: 'apk', prompt: 'Build Runner APK',
    project: { type: 'android', appName: 'Runner', packageName: 'com.saif.runner', files: [] }, assets: [] });
  return { db, drive, uploaded, url, post, worker, start };
}

test('dispatch requires Firebase identity AND attested app; only owner reads private status', async t => {
  const { db, post, start } = await fixture(t);
  const body = { jobId, kind: 'apk', prompt: 'Build Runner APK', assets: [] };
  assert.equal((await post('/api/requestBuild', body, null)).status, 401);
  assert.equal((await post('/api/requestBuild', body, 'ownerA', null)).status, 401);
  assert.equal((await post('/api/requestBuild', body, 'wrong')).status, 401);
  assert.equal((await post('/api/requestBuild', body, 'ownerA', 'attested-other')).status, 401);
  const queued = await start();
  assert.equal(queued.status, 202);
  assert.deepEqual((await queued.json()).ownerUid, 'ownerA');
  assert.equal(db.store.get(`apps/ownerA/${jobId}`).status, 'queued');
  assert.equal(db.store.get(`apps/ownerB/${jobId}`), undefined);
  assert.equal(db.store.get(`dispatched/${jobId}`), true);
  assert.equal((await start()).status, 409);
  assert.equal((await post('/api/issueDownloadLink', { jobId }, 'ownerB')).status, 404);
});

test('prompt/branch compatibility trigger requires Firebase and App Check and dispatches a tracked job', async t => {
  const { db, post } = await fixture(t);
  const body = { prompt: 'Build an app', branch: 'main' };
  assert.equal((await post('/api/trigger-build', body, null)).status, 401);
  assert.equal((await post('/api/trigger-build', body, 'ownerA', null)).status, 401);
  assert.equal((await post('/api/trigger-build', { ...body, branch: 'unknown' })).status, 400);
  const request = await post('/api/trigger-build', body);
  assert.equal(request.status, 202);
  const result = await request.json();
  assert.equal(result.ownerUid, 'ownerA');
  assert.equal(db.store.get(`apps/ownerA/${result.jobId}`).kind, 'apk');
  assert.equal(db.store.get(`dispatched/${result.jobId}`), true);
});

test('private Drive response requires correct one-time claim, folder and owner; refresh issues a fresh claim', async t => {
  const { db, url, post, worker, start } = await fixture(t);
  await start();
  assert.equal((await worker('/api/worker/progress', { jobId, status: 'building' }, 'wrong')).status, 401);
  assert.equal((await worker('/api/worker/progress', { jobId, status: 'building' })).status, 200);
  assert.equal((await worker('/api/worker/complete', { jobId, status: 'ready', driveFileId })).status, 200);
  const visible = db.store.get(`apps/ownerA/${jobId}`);
  assert.equal(visible.status, 'ready');
  assert.ok(visible.downloadClaim);
  assert.equal(visible.driveFileId, undefined, 'private Drive IDs must not appear in the app node');
  const link = { job: jobId, claim: visible.downloadClaim };
  assert.equal((await post('/api/inspectDownload', { ...link, job: '123e4567-e89b-42d3-a456-426614174999' })).status, 404);
  assert.equal((await post('/api/inspectDownload', { ...link, claim: 'not-a-real-claim' })).status, 404);
  assert.equal((await post('/api/inspectDownload', link)).status, 200);
  // A Drive ID alone must not become a public download endpoint.
  assert.equal((await fetch(url + `/api/claimDownload?id=${driveFileId}&job=${jobId}`)).status, 404);
  const form = new URLSearchParams(link);
  const download = await fetch(url + '/api/claimDownload', { method: 'POST', body: form });
  assert.equal(download.status, 200);
  assert.equal(await download.text(), 'private-apk-bytes');
  assert.equal(download.headers.get('content-disposition'), 'attachment; filename="Runner.apk"');
  assert.equal((await post('/api/inspectDownload', link)).status, 410);
  assert.equal((await post('/api/issueDownloadLink', { jobId }, 'ownerB')).status, 404);
  assert.equal((await post('/api/issueDownloadLink', { jobId })).status, 429);
  const claimPath = `cloudJobs/${jobId}/claim`;
  db.store.set(claimPath, { ...db.store.get(claimPath), issuedAt: Date.now() - 6000 });
  const refreshed = await post('/api/issueDownloadLink', { jobId });
  assert.equal(refreshed.status, 200);
  const newLink = await refreshed.json();
  assert.notEqual(newLink.downloadClaim, visible.downloadClaim);
  assert.equal(newLink.driveFileId, undefined, 'link refresh never leaks the private file ID');
  assert.equal((await post('/api/inspectDownload', { ...link, claim: newLink.downloadClaim })).status, 200);
});

test('portal has a sponsor placeholder but no public Drive share URL or third-party scripts', async t => {
  const { url } = await fixture(t);
  assert.equal((await fetch(url + '/download/not-a-job-id')).status, 404);
  const response = await fetch(url + `/download/${jobId}`);
  const html = await response.text();
  assert.match(html, /SAIF AI DOWNLOADS/);
  assert.match(html, /SPONSOR SPOTLIGHT/);
  assert.doesNotMatch(html, /uc\?export=download|drive\.google\.com|<script[^>]+https:/);
  assert.equal(response.headers.get('referrer-policy'), 'no-referrer');
  assert.match(response.headers.get('cache-control'), /no-store/);
});

test('signed asset staging is owner-bound; dispatch refuses missing binary files', async t => {
  const { db, uploaded, post } = await fixture(t);
  const assetId = '123e4567-e89b-42d3-a456-426614174001';
  const draft = await post('/api/createDraftJob', { jobId, kind: 'apk', prompt: 'Build my app',
    project: { type: 'android', appName: 'Runner', packageName: 'com.saif.runner', files: [] },
    assets: [{ assetId, mimeType: 'image/png', size: 120, sha256: 'a'.repeat(64),
      path: 'app/src/main/res/drawable/banner.png' }] });
  assert.equal(draft.status, 201);
  const response = await draft.json();
  assert.match(response.uploadUrls[0].url, /^https:\/\/storage.googleapis.com\//);
  assert.equal(db.store.get(`apps/ownerA/${jobId}`).status, 'uploading');
  assert.equal((await post('/api/dispatchJob', { jobId }, 'ownerB')).status, 404);
  assert.equal((await post('/api/dispatchJob', { jobId })).status, 409);
  uploaded.add(`jobs/${jobId}/assets/${assetId}`);
  assert.equal((await post('/api/dispatchJob', { jobId })).status, 202);
  assert.equal((await post('/api/dispatchJob', { jobId })).status, 409);
});

test('two simultaneous claims cannot stream the same file twice', async t => {
  const { db, url, worker, start } = await fixture(t);
  await start();
  await worker('/api/worker/complete', { jobId, status: 'ready', driveFileId });
  const claim = db.store.get(`apps/ownerA/${jobId}`).downloadClaim;
  const form = new URLSearchParams({ job: jobId, claim });
  const results = await Promise.all([0, 1].map(() => fetch(url + '/api/claimDownload', {
    method: 'POST', body: form
  })));
  assert.deepEqual(results.map(r => r.status).sort(), [200, 410]);
});
