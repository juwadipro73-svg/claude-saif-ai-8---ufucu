import crypto from 'node:crypto';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const SHA256 = /^[a-f0-9]{64}$/;
const PACKAGE = /^[a-zA-Z_][\w]*(?:\.[a-zA-Z_][\w]*){1,9}$/;
const SOURCE = /^app\/src\/main\/(?:AndroidManifest\.xml|(?:java|kotlin|res|assets)\/(?:[A-Za-z0-9_.-]+\/)*[A-Za-z0-9_.-]+\.(?:java|kt|xml|html|css|js|json|txt|svg))$/;
const BINARY = /^app\/src\/main\/(?:res\/(?:drawable|mipmap|raw|font)(?:-[a-z0-9-]+)?|assets)\/(?:[A-Za-z0-9_.-]+\/)*[A-Za-z0-9_.-]+\.(?:png|jpg|jpeg|webp|ttf|otf|mp3|wav)$/;
const MIME = /^(?:image\/(?:png|jpeg|webp)|video\/mp4|audio\/(?:mpeg|mp3|mp4|wav|ogg)|font\/(?:ttf|otf))$/;

export function badRequest(message) { return Object.assign(new Error(message), { status: 400 }); }
const assert = (valid, message) => { if (!valid) throw badRequest(message); };
const isObject = value => value !== null && typeof value === 'object' && !Array.isArray(value);
export const validJobId = value => typeof value === 'string' && UUID.test(value);
export const validDriveId = value => typeof value === 'string' && /^[A-Za-z0-9_-]{10,200}$/.test(value);
export const validClaimToken = value => typeof value === 'string' && /^[A-Za-z0-9_-]{32,128}$/.test(value);
export const validSourcePath = value => typeof value === 'string' && SOURCE.test(value) && !value.split('/').includes('..');

// User files are staged as opaque bytes; never extract APK/ZIP contents on the gateway.
// Accept only an explicit extension/MIME pair, not a client-supplied executable path or URL.
export const uploadMimeTypes = Object.freeze({
  apk: 'application/vnd.android.package-archive', zip: 'application/zip',
  mp4: 'video/mp4', m4v: 'video/mp4', mov: 'video/quicktime',
  webm: 'video/webm', mkv: 'video/x-matroska', '3gp': 'video/3gpp',
  png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', webp: 'image/webp',
  pdf: 'application/pdf', txt: 'text/plain', json: 'application/json',
  md: 'text/plain', kt: 'text/plain', java: 'text/plain', js: 'text/plain',
  xml: 'text/plain', html: 'text/plain', css: 'text/plain', py: 'text/plain',
  sh: 'text/plain', csv: 'text/plain'
});
export const MAX_UPLOAD_BYTES = 300 * 1024 * 1024;

export function validatePrivateUpload(body) {
  assert(isObject(body), 'Expected a JSON object');
  assert(validJobId(body.jobId), 'Invalid upload ID');
  assert(body.securityMode === 'onetime' && body.burnOnDownload === true,
    'Only one-time private website downloads are supported');
  assert(Array.isArray(body.files) && body.files.length === 1,
    'Upload exactly one APK, ZIP or video at a time');
  const file = body.files[0];
  assert(isObject(file) && validJobId(file.assetId), 'Invalid file ID');
  assert(!('data' in file) && !('base64' in file), 'Send file bytes via the signed upload URL, not Base64 JSON');
  assert(typeof file.name === 'string' && file.name.length <= 120 &&
    /^[A-Za-z0-9][A-Za-z0-9._ -]*\.[A-Za-z0-9]{2,5}$/.test(file.name) && !file.name.includes('..'),
    'Unsafe filename');
  const extension = file.name.split('.').pop().toLowerCase();
  assert(uploadMimeTypes[extension] === file.mimeType, 'Unsupported file extension/MIME');
  const deliverable = ['apk', 'zip', 'mp4', 'm4v', 'mov', 'webm', 'mkv', '3gp'].includes(extension);
  assert(Number.isSafeInteger(file.size) && file.size >= (deliverable ? 1 : 3 * 1024 * 1024) &&
    file.size <= MAX_UPLOAD_BYTES,
    'Private file must be an APK/ZIP/video or another supported file of at least 3 MB (max 300 MB)');
  assert(typeof file.sha256 === 'string' && SHA256.test(file.sha256), 'Missing file SHA-256');
  return { jobId: body.jobId, kind: 'upload', assets: [{ assetId: file.assetId,
    mimeType: file.mimeType, size: file.size, sha256: file.sha256, path: file.name }],
  prompt: 'Private file upload', project: null };
}

export function validateSubmission(body, mustHaveAssets = false) {
  assert(isObject(body), 'Expected a JSON object');
  const { jobId, kind, prompt, project, assets = [] } = body;
  assert(validJobId(jobId), 'Invalid job ID');
  assert(['apk', 'video', 'zip'].includes(kind), 'Only APK, ZIP or video jobs are supported');
  assert(typeof prompt === 'string' && prompt.trim().length > 0 && prompt.length <= 4000, 'Invalid build description');
  assert(Array.isArray(assets) && assets.length <= 20 && (mustHaveAssets ? assets.length > 0 : assets.length === 0),
    mustHaveAssets ? 'Declare 1–20 private assets' : 'Use createDraftJob to stage binary assets');
  const normalizedAssets = [];
  const seen = new Set();
  let sum = 0;
  for (const a of assets) {
    assert(isObject(a) && validJobId(a.assetId) && !seen.has(a.assetId), 'Invalid/duplicate asset ID');
    seen.add(a.assetId);
    assert(typeof a.mimeType === 'string' && MIME.test(a.mimeType), 'Unsupported asset MIME');
    assert(Number.isSafeInteger(a.size) && a.size > 0 && a.size <= 100 * 1024 * 1024, 'Asset limit: 100 MB');
    assert(typeof a.sha256 === 'string' && SHA256.test(a.sha256), 'Missing asset SHA-256');
    if (a.path !== undefined) assert(typeof a.path === 'string' && BINARY.test(a.path), 'Unsafe binary resource path');
    sum += a.size;
    normalizedAssets.push({ assetId: a.assetId, mimeType: a.mimeType, size: a.size,
      sha256: a.sha256, ...(a.path ? { path: a.path } : {}) });
  }
  assert(sum <= 300 * 1024 * 1024, 'Project assets exceed 300 MB');
  const safeProject = project === undefined || project === null ? null : validateProject(project, kind, normalizedAssets);
  const data = { jobId, kind, prompt: prompt.trim(), project: safeProject, assets: normalizedAssets };
  assert(Buffer.byteLength(JSON.stringify(data)) <= 1_800_000, 'Project description exceeds 1.8 MB');
  return data;
}

function validateProject(project, kind, assets) {
  assert(isObject(project) && project.type === (kind === 'video' ? 'video' : 'android'), 'Project type does not match job');
  if (kind === 'apk' || kind === 'zip') {
    const { appName, packageName, files = [] } = project;
    assert(typeof appName === 'string' && appName.trim().length > 0 && appName.length <= 80, 'Invalid app name');
    assert(typeof packageName === 'string' && PACKAGE.test(packageName), 'Invalid Android package');
    assert(Array.isArray(files) && files.length <= 80, 'Too many source files');
    const seen = new Set();
    let bytes = 0;
    const safeFiles = files.map(file => {
      assert(isObject(file) && validSourcePath(file.path) && !seen.has(file.path), 'Unsafe/duplicate source path');
      seen.add(file.path);
      assert(typeof file.content === 'string' && Buffer.byteLength(file.content) <= 512_000, 'Source file exceeds 512 KB');
      bytes += Buffer.byteLength(file.content);
      return { path: file.path, content: file.content };
    });
    assert(bytes <= 1_500_000, 'Source exceeds 1.5 MB');
    assert(assets.every(a => a.path && !seen.has(a.path)), 'Binary resource path is missing or collides');
    return { type: 'android', appName: appName.trim(), packageName, files: safeFiles };
  }
  const { name, width, height, fps, bitrate, durationUs, items } = project;
  assert(typeof name === 'string' && name.length <= 80, 'Invalid video project name');
  assert(Number.isInteger(width) && width >= 360 && width <= 2160 && width % 2 === 0 &&
    Number.isInteger(height) && height >= 360 && height <= 2160 && height % 2 === 0, 'Invalid video dimensions');
  assert(Number.isInteger(fps) && fps >= 24 && fps <= 60, 'Invalid video frame rate');
  if (bitrate !== undefined) assert(Number.isInteger(bitrate) && bitrate >= 100_000 && bitrate <= 50_000_000,
    'Invalid video bitrate');
  assert(Number.isSafeInteger(durationUs) && durationUs >= 1_000_000 && durationUs <= 180 * 60 * 1_000_000,
    'Invalid video duration');
  if (bitrate !== undefined) assert(((bitrate + 128_000) / 8) * (durationUs / 1_000_000) <= 2 * 1024 * 1024 * 1024,
    'Projected video exceeds the 2 GB private delivery limit; lower bitrate or shorten it');
  assert(Array.isArray(items) && items.length >= 1 && items.length <= 100, 'Video timeline must have 1–100 items');
  const ids = new Set(assets.map(a => a.assetId));
  const mediaTypes = new Map(assets.map(a => [a.assetId, a.mimeType]));
  const safeItems = items.map(item => {
    assert(isObject(item) && ['text', 'code', 'image', 'video', 'audio', 'voice'].includes(item.type), 'Unsupported timeline item');
    assert(Number.isSafeInteger(item.startUs) && item.startUs >= 0 &&
      Number.isSafeInteger(item.durationUs) && item.durationUs > 0 &&
      item.startUs + item.durationUs <= durationUs + 1000, 'Timeline item outside video');
    if (['image', 'video'].includes(item.type)) assert(ids.has(item.assetId), 'Timeline media asset missing');
    if (item.assetId !== undefined) assert(ids.has(item.assetId), 'Undeclared timeline asset');
    if (item.type === 'image') assert(mediaTypes.get(item.assetId)?.startsWith('image/'), 'Timeline image MIME mismatch');
    if (item.type === 'video') assert(mediaTypes.get(item.assetId) === 'video/mp4', 'Timeline video MIME mismatch');
    if (['audio', 'voice'].includes(item.type) && item.assetId)
      assert(mediaTypes.get(item.assetId)?.startsWith('audio/'), 'Timeline audio MIME mismatch');
    const safe = { type: item.type, startUs: item.startUs, durationUs: item.durationUs };
    if (item.assetId) safe.assetId = item.assetId;
    if (item.type === 'text' || item.type === 'voice') {
      assert(typeof item.text === 'string' && item.text.length <= 2000, 'Text/voice item too long');
      safe.text = item.text;
    }
    if (item.type === 'text') {
      assert(typeof item.fontSize === 'number' && item.fontSize >= 1 && item.fontSize <= 200,
        'Invalid text size');
      assert(typeof item.color === 'string' && /^#[0-9a-fA-F]{6}$/.test(item.color), 'Invalid text color');
      safe.fontSize = item.fontSize;
      safe.color = item.color;
    }
    if (item.type === 'code') {
      assert(typeof item.title === 'string' && item.title.length <= 200 &&
        typeof item.htmlCode === 'string' && item.htmlCode.length <= 80_000, 'Invalid code scene');
      safe.title = item.title;
      safe.htmlCode = item.htmlCode;
    }
    if (item.type === 'video') {
      assert(Number.isSafeInteger(item.sourceInUs) && item.sourceInUs >= 0, 'Invalid video in-point');
      safe.sourceInUs = item.sourceInUs;
    }
    return safe;
  });
  return { type: 'video', name: name.trim(), width, height, fps,
    ...(bitrate === undefined ? {} : { bitrate }), durationUs, items: safeItems };
}

export function claimHash(token, secret) {
  return crypto.createHmac('sha256', secret).update(token, 'utf8').digest('hex');
}
export function createClaim(secret, now = Date.now()) {
  const token = crypto.randomBytes(32).toString('base64url');
  return { token, stored: { hash: claimHash(token, secret), issuedAt: now,
    expiresAt: now + 10 * 60_000, used: false } };
}
export function claimMatches(claim, stored, secret, now = Date.now()) {
  if (!validClaimToken(claim) || !isObject(stored) || stored.used ||
    !Number.isSafeInteger(stored.expiresAt) || stored.expiresAt <= now ||
    typeof stored.hash !== 'string' || !SHA256.test(stored.hash)) return false;
  return crypto.timingSafeEqual(Buffer.from(stored.hash, 'hex'), Buffer.from(claimHash(claim, secret), 'hex'));
}
