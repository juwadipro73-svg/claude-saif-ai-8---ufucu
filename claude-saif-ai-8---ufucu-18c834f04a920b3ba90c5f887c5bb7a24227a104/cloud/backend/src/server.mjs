import express from 'express';
import helmet from 'helmet';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { pipeline } from 'node:stream/promises';
import { createHash, randomUUID, timingSafeEqual } from 'node:crypto';
import { claimMatches, createClaim, validClaimToken, validDriveId, validJobId,
  validatePrivateUpload, validateSubmission } from './policy.mjs';

const publicDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../public');
const addNoStore = (_req, res, next) => {
  res.set({ 'Cache-Control': 'private, no-store, max-age=0', 'Referrer-Policy': 'no-referrer',
    'X-Robots-Tag': 'noindex, nofollow, noarchive' });
  next();
};
const problem = (message, status = 400) => Object.assign(new Error(message), { status });
const safeEquals = (a, b) => {
  if (typeof a !== 'string' || typeof b !== 'string' || !a || !b) return false;
  const A = Buffer.from(a), B = Buffer.from(b);
  return A.length === B.length && timingSafeEqual(A, B);
};

/** Dependency-injected for contract tests; real Firebase/Drive/Storage secrets stay server-side. */
export function createApp({ db, auth, appCheck, bucket, drive, config, dispatch }) {
  if (!db || !auth || !appCheck || !bucket || !drive || !dispatch || !config?.appCheckAppId || !config?.downloadSecret || !config?.driveFolderId || !config?.workerSecret)
    throw new Error('Private cloud gateway is not fully configured');
  const app = express();
  app.disable('x-powered-by');
  app.use(helmet({ contentSecurityPolicy: {
    directives: { defaultSrc: ["'none'"], scriptSrc: ["'self'"], styleSrc: ["'self'"],
      imgSrc: ["'self'"], connectSrc: ["'self'"], formAction: ["'self'"],
      frameAncestors: ["'none'"], baseUri: ["'none'"] }
  }, referrerPolicy: { policy: 'no-referrer' } }));
  app.use(addNoStore);
  app.use(express.json({ limit: '1900kb' }));
  app.use(express.urlencoded({ extended: false, limit: '2kb' }));
  app.get('/healthz', (_req, res) => res.json({ ok: true }));
  app.get('/', (_req, res) => res.sendFile(path.join(publicDir, 'index.html')));
  app.get('/download', (_req, res) => res.sendFile(path.join(publicDir, 'download.html')));
  // A unique job-specific website page, not a Drive ID or a direct artifact URL.
  app.get('/download/:jobId', (req, res) => {
    if (!validJobId(req.params.jobId)) return res.status(404).end();
    res.sendFile(path.join(publicDir, 'download.html'));
  });
  app.use('/static', express.static(publicDir, { maxAge: 0, etag: false, index: false }));

  async function requireAppUser(req) {
    const bearer = req.get('Authorization')?.match(/^Bearer ([A-Za-z0-9._~-]+)$/)?.[1];
    if (!bearer) throw problem('Sign in to SAIF AI to start a cloud job', 401);
    const appCheckToken = req.get('X-Firebase-AppCheck');
    if (!appCheckToken) throw problem('Firebase App Check is required', 401);
    const [identity, attested] = await Promise.all([
      auth.verifyIdToken(bearer, true), appCheck.verifyToken(appCheckToken)
    ]).catch(() => { throw problem('Invalid Firebase or App Check token', 401); });
    if (!identity?.uid || attested?.appId !== config.appCheckAppId)
      throw problem('Unverified Android app identity', 401);
    return identity.uid;
  }

  async function limitJobs(uid) {
    const ref = db.ref(`cloudLimits/${uid}`);
    const now = Date.now();
    const result = await ref.transaction(value => {
      const start = Number(value?.windowStart) || 0;
      const count = now - start < 60 * 60_000 ? Number(value?.count) || 0 : 0;
      if (count >= 5) return; // atomic, per-Firebase-UID quota; add edge IP limits for anonymous users
      return { windowStart: count === 0 ? now : start, count: count + 1 };
    }, undefined, false);
    if (!result.committed) throw problem('Cloud job limit reached; try again later', 429);
  }

  const privateRef = id => db.ref(`cloudJobs/${id}`);
  const visibleRef = (uid, id) => db.ref(`apps/${uid}/${id}`);
  const inputFile = id => bucket.file(`jobs/${id}/input.json`);
  const assetFile = (id, assetId) => bucket.file(`jobs/${id}/assets/${assetId}`);

  async function createJob(uid, payload) {
    await limitJobs(uid);
    const { jobId, kind, assets } = payload;
    const ref = privateRef(jobId);
    const start = await ref.transaction(value => value === null ?
      { ownerUid: uid, kind, createdAt: Date.now(), status: 'staging', assets, driveFileId: null } : undefined,
    undefined, false);
    if (!start.committed) throw problem('Job ID already exists', 409);
    try {
      await inputFile(jobId).save(JSON.stringify({ ...payload, ownerUid: uid }), {
        resumable: false, contentType: 'application/json', metadata: { cacheControl: 'private,no-store' }
      });
      await ref.update({ status: 'uploading' });
      await visibleRef(uid, jobId).set({ kind, status: 'uploading', createdAt: Date.now() });
      return { jobId, ownerUid: uid, kind };
    } catch (err) {
      await ref.update({ status: 'failed' });
      throw err;
    }
  }

  async function dispatchJob(uid, jobId) {
    if (!validJobId(jobId)) throw problem('Invalid job ID');
    const ref = privateRef(jobId);
    const job = (await ref.get()).val();
    if (!job || job.ownerUid !== uid) throw problem('Job not found', 404);
    if (job.status !== 'uploading') throw problem('Job is already queued or failed', 409);
    for (const asset of job.assets || []) {
      const [metadata] = await assetFile(jobId, asset.assetId).getMetadata().catch(() => { throw problem('Project asset upload is incomplete', 409); });
      if (Number(metadata.size) !== asset.size || metadata.contentType !== asset.mimeType)
        throw problem('Project asset size/type does not match declaration', 400);
    }
    const lock = await ref.transaction(value => value?.ownerUid === uid && value?.status === 'uploading' ?
      { ...value, status: 'dispatching' } : undefined, undefined, false);
    if (!lock.committed) throw problem('Job was already dispatched', 409);
    try {
      await dispatch(jobId);
      const queued = await ref.transaction(value => value?.status === 'dispatching' ?
        { ...value, status: 'queued' } : undefined, undefined, false);
      if (queued.committed) await visibleRef(uid, jobId).update({ status: 'queued' });
    } catch (err) {
      const failed = await ref.transaction(value => value?.status === 'dispatching' ?
        { ...value, status: 'failed', error: 'Could not dispatch cloud worker' } : undefined,
      undefined, false);
      if (failed.committed) await visibleRef(uid, jobId).update({ status: 'failed', error: 'Could not dispatch cloud worker' });
      throw err;
    }
    return { jobId, ownerUid: uid, status: 'queued' };
  }

  function api(handler) {
    return (req, res, next) => Promise.resolve().then(() => handler(req, res)).catch(next);
  }
  app.post('/api/requestBuild', api(async (req, res) => {
    const uid = await requireAppUser(req);
    const payload = validateSubmission(req.body);
    await createJob(uid, payload);
    res.status(202).json(await dispatchJob(uid, payload.jobId));
  }));
  // Compatibility with the owner's prompt/branch shape, but never anonymous: it creates a
  // job_id for the private builder callback. A prompt-only legacy workflow cannot complete it.
  app.post('/api/trigger-build', api(async (req, res) => {
    const uid = await requireAppUser(req);
    if (req.body?.branch !== (config.builderRef || 'main'))
      throw problem('Build branch does not match the configured private builder');
    const payload = validateSubmission({ jobId: randomUUID(), kind: req.body?.kind || 'apk',
      prompt: req.body?.prompt, assets: [] });
    await createJob(uid, payload);
    res.status(202).json(await dispatchJob(uid, payload.jobId));
  }));

  app.post('/api/createDraftJob', api(async (req, res) => {
    const uid = await requireAppUser(req);
    const payload = validateSubmission(req.body, true);
    const result = await createJob(uid, payload);
    const uploadUrls = [];
    try {
      for (const asset of payload.assets) {
        const [url] = await assetFile(result.jobId, asset.assetId).getSignedUrl({
          version: 'v4', action: 'write', expires: Date.now() + 15 * 60_000, contentType: asset.mimeType
        });
        uploadUrls.push({ assetId: asset.assetId, url });
      }
    } catch (error) {
      await privateRef(result.jobId).update({ status: 'failed', error: 'Private asset staging unavailable' });
      await visibleRef(uid, result.jobId).update({ status: 'failed', error: 'Private asset staging unavailable' });
      throw error;
    }
    res.status(201).json({ ...result, uploadUrls });
  }));
  // POST /api/push creates one private staged file; never accept a Base64 JSON blob. A signed
  // GCS PUT keeps 300 MB videos outside Cloud Run's request body/memory limits. Only the same
  // verified Firebase/App Check owner can finalize and obtain a one-time website download.
  app.post('/api/push', api(async (req, res) => {
    const uid = await requireAppUser(req);
    const payload = validatePrivateUpload(req.body);
    const result = await createJob(uid, payload);
    try {
      const file = payload.assets[0];
      const [url] = await assetFile(result.jobId, file.assetId).getSignedUrl({
        version: 'v4', action: 'write', expires: Date.now() + 60 * 60_000, contentType: file.mimeType
      });
      res.status(201).json({ ...result, uploadUrls: [{ assetId: file.assetId, url }] });
    } catch (error) {
      await privateRef(result.jobId).update({ status: 'failed', error: 'Private file staging unavailable' });
      await visibleRef(uid, result.jobId).update({ status: 'failed', error: 'Private file staging unavailable' });
      throw error;
    }
  }));

  app.post('/api/dispatchJob', api(async (req, res) => {
    const uid = await requireAppUser(req);
    res.status(202).json(await dispatchJob(uid, req.body?.jobId));
  }));

  function requireWorker(req) {
    if (!safeEquals(req.get('Authorization'), `Bearer ${config.workerSecret}`))
      throw problem('Worker authentication required', 401);
  }
  app.post('/api/worker/progress', api(async (req, res) => {
    requireWorker(req);
    const { jobId, status } = req.body || {};
    if (!validJobId(jobId) || status !== 'building') throw problem('Invalid progress');
    const updated = await privateRef(jobId).transaction(value =>
      value && ['queued', 'building', 'dispatching'].includes(value.status) ?
        { ...value, status: 'building' } : undefined, undefined, false);
    if (!updated.committed) throw problem('Job not queued', 409);
    await visibleRef(updated.snapshot.val().ownerUid, jobId).update({ status: 'building' });
    res.json({ ok: true });
  }));

  async function issueLink(jobId, job) {
    const ref = privateRef(jobId).child('claim');
    const { token, stored } = createClaim(config.downloadSecret);
    const claim = await ref.transaction(old =>
      old?.issuedAt && stored.issuedAt - old.issuedAt < 5000 ? undefined : stored,
    undefined, false);
    if (!claim.committed) throw problem('Wait a few seconds before refreshing the link', 429);
    // Never expose Drive IDs in client-visible RTDB nodes or link responses.
    await visibleRef(job.ownerUid, jobId).update({
      downloadClaim: token, claimExpiresAt: stored.expiresAt, status: 'ready' });
    return { jobId, downloadClaim: token, claimExpiresAt: stored.expiresAt };
  }

  async function verifyPrivateArtifact(driveFileId, kind, asset = null) {
    if (!validDriveId(driveFileId)) throw problem('Invalid private Drive file ID');
    const { data } = await drive.files.get({ fileId: driveFileId,
      fields: 'id,name,mimeType,size,parents,trashed', supportsAllDrives: true });
    const expectedType = asset?.mimeType || ({ apk: 'application/vnd.android.package-archive',
      zip: 'application/zip', video: 'video/mp4' })[kind];
    const expectedName = asset?.path;
    const extension = kind === 'apk' ? '.apk' : kind === 'zip' ? '.zip' : '.mp4';
    if (data.trashed || !data.parents?.includes(config.driveFolderId) ||
        !(expectedName ? data.name === expectedName : data.name?.endsWith(extension)) ||
        data.mimeType !== expectedType || !Number(data.size) || Number(data.size) > 2 * 1024 * 1024 * 1024 ||
        (asset && Number(data.size) !== asset.size))
      throw problem('Output is not a private artifact in the configured Drive folder', 400);
    const permissions = await drive.permissions.list({ fileId: driveFileId,
      fields: 'nextPageToken,permissions(type,role)', pageSize: 100, supportsAllDrives: true });
    if (permissions.data.nextPageToken || !Array.isArray(permissions.data.permissions) ||
      permissions.data.permissions.some(p => p.type === 'anyone' || p.type === 'domain'))
      throw problem('Drive file has public or domain-wide permissions', 400);
    return data;
  }

  app.post('/api/worker/complete', api(async (req, res) => {
    requireWorker(req);
    const { jobId, status, driveFileId } = req.body || {};
    if (!validJobId(jobId) || !['ready', 'failed'].includes(status)) throw problem('Invalid completion');
    const ref = privateRef(jobId);
    const job = (await ref.get()).val();
    if (!job || job.kind === 'upload' || !['queued', 'building', 'dispatching'].includes(job.status)) throw problem('Job not active', 409);
    if (status === 'failed') {
      const error = String(req.body.error || 'The cloud worker failed').replace(/[\r\n<>]/g, ' ').slice(0, 200);
      const failed = await ref.transaction(value =>
        value && ['queued', 'building', 'dispatching'].includes(value.status) ?
          { ...value, status: 'failed', error } : undefined, undefined, false);
      if (!failed.committed) throw problem('Job already finished', 409);
      await visibleRef(job.ownerUid, jobId).update({ status: 'failed', error });
      return res.json({ ok: true });
    }
    const data = await verifyPrivateArtifact(driveFileId, job.kind);
    const ready = await ref.transaction(value =>
      value && ['queued', 'building', 'dispatching'].includes(value.status) ?
        { ...value, status: 'ready', driveFileId, fileName: data.name, fileSize: Number(data.size) } : undefined,
    undefined, false);
    if (!ready.committed) throw problem('Job already finished', 409);
    try { await issueLink(jobId, { ...job, driveFileId }); }
    catch (error) {
      await ref.update({ status: 'failed', error: 'Could not issue private download claim' });
      await visibleRef(job.ownerUid, jobId).update({ status: 'failed', error: 'Could not issue private download claim' });
      throw error;
    }
    res.json({ ok: true });
  }));

  app.post('/api/push/complete', api(async (req, res) => {
    const uid = await requireAppUser(req);
    const jobId = req.body?.jobId;
    if (!validJobId(jobId)) throw problem('Invalid upload ID');
    const ref = privateRef(jobId);
    const job = (await ref.get()).val();
    if (!job || job.ownerUid !== uid || job.kind !== 'upload') throw problem('Upload not found', 404);
    if (job.status !== 'uploading') throw problem('Upload is not awaiting a file', 409);
    const asset = job.assets?.[0];
    if (!asset) throw problem('Missing upload declaration', 409);
    const staged = assetFile(jobId, asset.assetId);
    const [metadata] = await staged.getMetadata().catch(() => { throw problem('File upload is incomplete', 409); });
    if (Number(metadata.size) !== asset.size || metadata.contentType !== asset.mimeType)
      throw problem('Uploaded file size/type differs from the declaration');
    const lock = await ref.transaction(value => value?.ownerUid === uid && value?.status === 'uploading' ?
      { ...value, status: 'dispatching' } : undefined, undefined, false);
    if (!lock.committed) throw problem('Upload already being finalized', 409);
    let driveFileId;
    try {
      await visibleRef(uid, jobId).update({ status: 'building' });
      // Bind both reads to the exact staged generation. Never promote a changed or truncated PUT.
      const source = metadata.generation ? bucket.file(`jobs/${jobId}/assets/${asset.assetId}`,
        { generation: metadata.generation }) : staged;
      const hash = createHash('sha256');
      let received = 0;
      for await (const chunk of source.createReadStream()) {
        received += chunk.length;
        if (received > asset.size) throw problem('Uploaded file exceeds the declared size');
        hash.update(chunk);
      }
      if (received !== asset.size || hash.digest('hex') !== asset.sha256)
        throw problem('Uploaded file checksum does not match; select the file again');
      const created = await drive.files.create({
        requestBody: { name: asset.path, mimeType: asset.mimeType, parents: [config.driveFolderId] },
        media: { mimeType: asset.mimeType, body: source.createReadStream() },
        fields: 'id', supportsAllDrives: true
      });
      driveFileId = created.data?.id;
      const data = await verifyPrivateArtifact(driveFileId, 'upload', asset);
      const ready = await ref.transaction(value => value?.ownerUid === uid && value?.status === 'dispatching' ?
        { ...value, status: 'ready', driveFileId, fileName: data.name,
          fileSize: Number(data.size), mimeType: asset.mimeType } : undefined, undefined, false);
      if (!ready.committed) throw problem('Upload was already finalized', 409);
      const link = await issueLink(jobId, { ...job, driveFileId });
      res.json({ jobId, ownerUid: uid, status: 'ready', ...link });
    } catch (error) {
      if (driveFileId && drive.files.delete) await drive.files.delete({ fileId: driveFileId, supportsAllDrives: true })
        .catch(() => console.error('Could not delete a failed private upload'));
      await ref.update({ status: 'failed', error: 'Private upload could not be finalized' });
      await visibleRef(uid, jobId).update({ status: 'failed', error: 'Private upload could not be finalized' });
      throw error;
    } finally {
      if (staged.delete) await staged.delete().catch(() => console.error('Could not delete a staged upload'));
    }
  }));

  app.post('/api/issueDownloadLink', api(async (req, res) => {
    const uid = await requireAppUser(req);
    const jobId = req.body?.jobId;
    if (!validJobId(jobId)) throw problem('Invalid job ID');
    const job = (await privateRef(jobId).get()).val();
    if (!job || job.ownerUid !== uid || job.status !== 'ready' || !validDriveId(job.driveFileId))
      throw problem('Private download is not available for this account', 404);
    res.json(await issueLink(jobId, job));
  }));

  async function checkClaim(body, consume = false) {
    const { job: jobId, claim } = body || {};
    if (!validJobId(jobId) || !validClaimToken(claim)) throw problem('Invalid download link', 404);
    const ref = privateRef(jobId);
    const job = (await ref.get()).val();
    if (!job || job.status !== 'ready' || !validDriveId(job.driveFileId))
      throw problem('Private download is not available', 404);
    const claimRef = ref.child('claim');
    const current = (await claimRef.get()).val();
    if (!claimMatches(claim, current, config.downloadSecret)) throw problem('Link expired or already used. Refresh it in the app.', 410);
    if (consume) {
      const result = await claimRef.transaction(value => claimMatches(claim, value, config.downloadSecret) ?
        { ...value, used: true } : undefined, undefined, false);
      if (!result.committed) throw problem('Link expired or already used. Refresh it in the app.', 410);
      await visibleRef(job.ownerUid, jobId).transaction(value =>
        value?.downloadClaim === claim ? { ...value, downloadClaim: null, claimExpiresAt: 0 } : undefined,
      undefined, false);
    }
    return job;
  }

  app.post('/api/inspectDownload', api(async (req, res) => {
    const job = await checkClaim(req.body);
    res.json({ kind: job.kind, name: job.fileName, bytes: job.fileSize });
  }));
  app.post('/api/claimDownload', api(async (req, res) => {
    const job = await checkClaim(req.body, true);
    const fileName = String(job.fileName || `saif-ai-${job.kind}`).replace(/[^a-zA-Z0-9._-]/g, '_');
    res.set({ 'Content-Type': job.kind === 'upload' ? job.mimeType :
      job.kind === 'video' ? 'video/mp4' : job.kind === 'zip' ? 'application/zip' :
        'application/vnd.android.package-archive',
      'Content-Disposition': `attachment; filename="${fileName}"`,
      'Content-Length': String(job.fileSize), 'X-Content-Type-Options': 'nosniff' });
    const upstream = await drive.files.get({ fileId: job.driveFileId, alt: 'media', supportsAllDrives: true },
      { responseType: 'stream' });
    await pipeline(upstream.data, res);
  }));

  app.use((error, _req, res, _next) => {
    const status = Number.isInteger(error.status) && error.status >= 400 && error.status < 500 ? error.status : 500;
    if (status === 500) console.error('Cloud gateway error:', error.message); // never log bearer/claim/prompt
    if (!res.headersSent) res.status(status).json({ error: status === 500 ? 'Cloud gateway error. Retry later.' : error.message });
  });
  return app;
}
