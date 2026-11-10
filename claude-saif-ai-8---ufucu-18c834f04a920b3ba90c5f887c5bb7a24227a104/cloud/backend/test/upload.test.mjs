import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { Readable } from 'node:stream';
import { createApp } from '../src/server.mjs';
import { MAX_UPLOAD_BYTES, validatePrivateUpload } from '../src/policy.mjs';

const jobId = '123e4567-e89b-42d3-a456-426614174100';
const assetId = '123e4567-e89b-42d3-a456-426614174101';
const digest = bytes => createHash('sha256').update(bytes).digest('hex');
const clone = value => value === undefined ? undefined : structuredClone(value);

class MemoryDB {
  store = new Map();
  ref(path) {
    const key = String(path).replace(/^\/+|\/+$/g, '');
    return {
      child: sub => this.ref(`${key}/${sub}`),
      get: async () => ({ val: () => clone(this.store.get(key)) ?? null }),
      set: async value => { this.store.set(key, clone(value)); },
      update: async patch => { this.store.set(key, { ...this.store.get(key), ...clone(patch) }); },
      transaction: async fn => {
        const next = fn(clone(this.store.get(key)) ?? null);
        if (next === undefined) return { committed: false };
        this.store.set(key, clone(next));
        return { committed: true, snapshot: { val: () => clone(next) } };
      }
    };
  }
}

async function fixture(t, bytes = Buffer.from('a small test apk'), permission = 'user') {
  const db = new MemoryDB();
  const objects = new Map();
  const files = new Map();
  const bucket = { file: key => ({
    async save(text) { objects.set(key, { bytes: Buffer.from(text), mimeType: 'application/json' }); },
    async getSignedUrl() { return [`https://storage.googleapis.com/test-bucket/${key}?signed=1`]; },
    async getMetadata() {
      const object = objects.get(key);
      if (!object) throw new Error('missing upload');
      return [{ size: object.bytes.length, contentType: object.mimeType, generation: '1' }];
    },
    createReadStream() {
      if (!objects.has(key)) throw new Error('missing upload');
      return Readable.from([objects.get(key).bytes]);
    },
    async delete() { objects.delete(key); }
  }) };
  const drive = { files: {
    async create({ requestBody, media }) {
      const chunks = [];
      for await (const chunk of media.body) chunks.push(chunk);
      const id = 'private-upload-123456';
      files.set(id, { bytes: Buffer.concat(chunks), name: requestBody.name,
        mimeType: media.mimeType, parents: requestBody.parents });
      return { data: { id } };
    },
    async get({ fileId, alt }) {
      const item = files.get(fileId);
      if (!item) throw new Error('missing private Drive file');
      if (alt === 'media') return { data: Readable.from([item.bytes]) };
      return { data: { id: fileId, name: item.name, size: item.bytes.length,
        mimeType: item.mimeType, parents: item.parents, trashed: false } };
    },
    async delete({ fileId }) { files.delete(fileId); }
  }, permissions: { async list() { return { data: { permissions: [{ type: permission, role: 'owner' }] } }; } } };
  const app = createApp({ db, bucket, drive, dispatch: async () => {},
    config: { appCheckAppId: 'app-id', driveFolderId: 'private-folder',
      downloadSecret: 'download-secret-at-least-32-chars-1234', workerSecret: 'worker-secret-at-least-32-chars-1234' },
    auth: { async verifyIdToken(token) {
      if (!['ownerA', 'ownerB'].includes(token)) throw new Error('bad token');
      return { uid: token };
    } },
    appCheck: { async verifyToken(token) {
      if (token !== 'attested') throw new Error('bad attestation');
      return { appId: 'app-id' };
    } }
  });
  const server = app.listen(0, '127.0.0.1');
  await new Promise(resolve => server.once('listening', resolve));
  t.after(() => server.close());
  const url = `http://127.0.0.1:${server.address().port}`;
  const post = (route, body, user = 'ownerA', check = 'attested') => fetch(url + route, {
    method: 'POST', headers: { 'Content-Type': 'application/json',
      ...(user ? { Authorization: `Bearer ${user}` } : {}),
      ...(check ? { 'X-Firebase-AppCheck': check } : {}) },
    body: JSON.stringify(body)
  });
  const payload = (name = 'App.apk', mimeType = 'application/vnd.android.package-archive', hash = digest(bytes)) => ({
    jobId, securityMode: 'onetime', burnOnDownload: true,
    files: [{ assetId, name, mimeType, size: bytes.length, sha256: hash }]
  });
  const stage = (mimeType = 'application/vnd.android.package-archive') => {
    objects.set(`jobs/${jobId}/assets/${assetId}`, { bytes, mimeType });
  };
  return { db, files, objects, url, post, payload, stage, bytes };
}

test('APK, ZIP, video and large photos use a validated private upload manifest, not unbounded Base64 JSON', () => {
  const base = { jobId, securityMode: 'onetime', burnOnDownload: true,
    files: [{ assetId, name: 'App.apk', size: 2048, sha256: 'a'.repeat(64),
      mimeType: 'application/vnd.android.package-archive' }] };
  for (const [name, mime] of [['App.apk', 'application/vnd.android.package-archive'],
    ['Project.zip', 'application/zip'], ['Clip.mp4', 'video/mp4'], ['Clip.webm', 'video/webm']]) {
    assert.equal(validatePrivateUpload({ ...base, files: [{ ...base.files[0], name, mimeType: mime }] }).kind, 'upload');
  }
  for (const patch of [
    { files: [{ ...base.files[0], name: '../../bad.apk' }] },
    { files: [{ ...base.files[0], name: 'bad.apk', mimeType: 'application/zip' }] },
    { files: [{ ...base.files[0], size: MAX_UPLOAD_BYTES + 1 }] },
    { files: [{ ...base.files[0], data: 'data:application/zip;base64,eA==' }] },
    { securityMode: 'public' },
    { burnOnDownload: false },
    { files: [{ ...base.files[0], name: 'photo.jpg', mimeType: 'image/jpeg' }] }
  ]) {
    assert.throws(() => validatePrivateUpload({ ...base, ...patch }));
  }
  assert.equal(validatePrivateUpload({ ...base, files: [{ ...base.files[0], name: 'photo.jpg',
    mimeType: 'image/jpeg', size: 3 * 1024 * 1024 }] }).kind, 'upload');
});

test('verified file stages to private Drive and gets one website claim, never a public Drive ID', async t => {
  const { db, post, payload, stage, files, bytes, url } = await fixture(t);
  assert.equal((await post('/api/push', payload(), null)).status, 401);
  assert.equal((await post('/api/push', payload(), 'ownerA', null)).status, 401);
  const started = await post('/api/push', payload());
  assert.equal(started.status, 201);
  const manifest = await started.json();
  assert.equal(manifest.ownerUid, 'ownerA');
  assert.match(manifest.uploadUrls[0].url, /^https:\/\/storage\.googleapis\.com\//);
  assert.equal((await post('/api/push/complete', { jobId }, 'ownerB')).status, 404);
  assert.equal((await post('/api/push/complete', { jobId })).status, 409, 'missing staged file');
  stage();
  const complete = await post('/api/push/complete', { jobId });
  assert.equal(complete.status, 200);
  const final = await complete.json();
  assert.equal(final.status, 'ready');
  assert.ok(final.downloadClaim);
  assert.equal(db.store.get(`apps/ownerA/${jobId}`).driveFileId, undefined);
  assert.equal(db.store.get(`apps/ownerB/${jobId}`), undefined);
  assert.equal(files.size, 1);
  assert.equal((await post('/api/issueDownloadLink', { jobId }, 'ownerB')).status, 404);
  assert.equal((await post('/api/push/complete', { jobId })).status, 409);
  const claim = { job: jobId, claim: final.downloadClaim };
  const info = await post('/api/inspectDownload', claim);
  assert.equal((await info.json()).name, 'App.apk');
  const downloaded = await fetch(url + '/api/claimDownload', { method: 'POST', body: new URLSearchParams(claim) });
  assert.equal(downloaded.status, 200);
  assert.equal(downloaded.headers.get('content-type'), 'application/vnd.android.package-archive');
  assert.deepEqual(Buffer.from(await downloaded.arrayBuffer()), bytes);
  assert.equal((await post('/api/inspectDownload', claim)).status, 410);
});

for (const [filename, mimeType] of [['Archive.zip', 'application/zip'],
  ['Clip.mp4', 'video/mp4'], ['Movie.webm', 'video/webm']]) {
  test(`${filename} keeps its MIME type on the private website`, async t => {
    const { post, payload, stage, url, bytes } = await fixture(t);
    assert.equal((await post('/api/push', payload(filename, mimeType))).status, 201);
    stage(mimeType);
    const completed = await post('/api/push/complete', { jobId });
    assert.equal(completed.status, 200);
    const { downloadClaim } = await completed.json();
    const response = await fetch(url + '/api/claimDownload', { method: 'POST',
      body: new URLSearchParams({ job: jobId, claim: downloadClaim }) });
    assert.equal(response.status, 200);
    assert.equal(response.headers.get('content-type'), mimeType);
    assert.deepEqual(Buffer.from(await response.arrayBuffer()), bytes);
  });
}

test('public Drive permission fails closed without a website link', async t => {
  const { db, post, payload, stage, files } = await fixture(t, Buffer.from('zip-byte-content'), 'anyone');
  const started = await post('/api/push', payload('Archive.zip', 'application/zip'));
  assert.equal(started.status, 201);
  stage('application/zip');
  const response = await post('/api/push/complete', { jobId });
  assert.equal(response.status, 400);
  assert.equal(db.store.get(`apps/ownerA/${jobId}`).status, 'failed');
  assert.equal(db.store.get(`apps/ownerA/${jobId}`).downloadClaim, undefined);
  assert.equal(files.size, 0, 'reject and delete Drive file with public permissions');
});

test('source ZIP build callback gets its own private website ZIP link', async t => {
  const { db, files, post, url } = await fixture(t);
  const request = await post('/api/requestBuild', { jobId, kind: 'zip',
    prompt: 'Export the source project as ZIP', assets: [] });
  assert.equal(request.status, 202);
  const driveFileId = 'private-zip-artifact-123';
  files.set(driveFileId, { bytes: Buffer.from('zip source bytes'), name: 'source.zip',
    mimeType: 'application/zip', parents: ['private-folder'] });
  const callback = await fetch(url + '/api/worker/complete', { method: 'POST',
    headers: { Authorization: 'Bearer worker-secret-at-least-32-chars-1234',
      'Content-Type': 'application/json' },
    body: JSON.stringify({ jobId, status: 'ready', driveFileId }) });
  assert.equal(callback.status, 200);
  const claim = db.store.get(`apps/ownerA/${jobId}`).downloadClaim;
  const download = await fetch(url + '/api/claimDownload', { method: 'POST',
    body: new URLSearchParams({ job: jobId, claim }) });
  assert.equal(download.status, 200);
  assert.equal(download.headers.get('content-type'), 'application/zip');
});

test('checksum mismatch never reaches Drive or exposes a download claim', async t => {
  const { db, post, payload, stage, files } = await fixture(t);
  assert.equal((await post('/api/push', payload('Clip.mp4', 'video/mp4', 'f'.repeat(64)))).status, 201);
  stage('video/mp4');
  assert.equal((await post('/api/push/complete', { jobId })).status, 400);
  assert.equal(files.size, 0);
  assert.equal(db.store.get(`apps/ownerA/${jobId}`).status, 'failed');
  assert.equal(db.store.get(`apps/ownerA/${jobId}`).downloadClaim, undefined);
});
