import test from 'node:test';
import assert from 'node:assert/strict';
import { createApp } from '../src/server.mjs';

const job = '123e4567-e89b-42d3-a456-426614174000';
const file = 'private-file-id-123';
class Db {
  store = new Map([[`cloudJobs/${job}`, { ownerUid: 'uid', kind: 'apk', status: 'building' }]]);
  ref(path) { return { get: async () => ({ val: () => this.store.get(path) }),
    update: async data => this.store.set(path, { ...this.store.get(path), ...data }),
    child: sub => this.ref(path + '/' + sub) }; }
}

test('backend refuses Drive files with anyone-link permissions', async t => {
  const db = new Db();
  const drive = { files: { async get() { return { data: { name: 'artifact.apk', size: 6000,
    mimeType: 'application/vnd.android.package-archive',
    parents: ['private-folder'], trashed: false } }; } },
  permissions: { async list() { return { data: { permissions: [{ type: 'anyone', role: 'reader' }] } }; } } };
  const app = createApp({ db, drive, bucket: {}, dispatch() {}, config: {
    appCheckAppId: 'firebase-app-id', driveFolderId: 'private-folder', workerSecret: 'long-worker-secret-1234567890-123456',
    downloadSecret: 'long-download-secret-1234567890-1234'
  }, auth: {}, appCheck: {} });
  const server = app.listen(0, '127.0.0.1');
  await new Promise(resolve => server.once('listening', resolve));
  t.after(() => server.close());
  const result = await fetch(`http://127.0.0.1:${server.address().port}/api/worker/complete`, {
    method: 'POST', headers: { 'Content-Type': 'application/json',
      Authorization: 'Bearer long-worker-secret-1234567890-123456' },
    body: JSON.stringify({ jobId: job, status: 'ready', driveFileId: file })
  });
  assert.equal(result.status, 400);
  assert.equal(db.store.get(`cloudJobs/${job}`).status, 'building');
});
