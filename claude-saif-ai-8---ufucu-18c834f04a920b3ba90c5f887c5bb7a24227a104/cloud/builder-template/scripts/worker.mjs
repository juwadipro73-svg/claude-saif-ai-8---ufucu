import fs from 'node:fs';
import fsp from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import crypto from 'node:crypto';
import { spawn } from 'node:child_process';
import { pipeline } from 'node:stream/promises';
import { Storage } from '@google-cloud/storage';
import { google } from 'googleapis';
import { prepareApk } from './prepare-apk.mjs';
import { saveVideo } from './prepare-video.mjs';

const id = process.argv[2];
if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id || ''))
  throw new Error('Workflow needs a valid job_id');
const api = process.env.CLOUD_API_BASE_URL?.replace(/\/$/, '');
if (!api?.startsWith('https://') || !process.env.WORKER_CALLBACK_SECRET || !process.env.STAGING_BUCKET)
  throw new Error('Configure CLOUD_API_BASE_URL, WORKER_CALLBACK_SECRET and STAGING_BUCKET');
const bucket = new Storage().bucket(process.env.STAGING_BUCKET);
const root = path.join(os.tmpdir(), `saif-worker-${id}`);
const assetsRoot = path.join(root, 'assets');

async function callback(endpoint, payload) {
  const response = await fetch(`${api}/api/worker/${endpoint}`, {
    method: 'POST', headers: { Authorization: `Bearer ${process.env.WORKER_CALLBACK_SECRET}`,
      'Content-Type': 'application/json' }, body: JSON.stringify({ jobId: id, ...payload })
  });
  if (!response.ok) throw new Error(`Worker callback ${endpoint} failed (${response.status})`);
}

async function run(program, args, cwd) {
  // No backend callback, Drive or model credentials belong in compiler/FFmpeg
  // subprocess environments. Treat user-controlled source/media as untrusted.
  const env = { ...process.env };
  for (const key of ['WORKER_CALLBACK_SECRET', 'GEMINI_API_KEY', 'DRIVE_REFRESH_TOKEN',
    'DRIVE_CLIENT_ID', 'DRIVE_CLIENT_SECRET', 'GCP_SERVICE_ACCOUNT_JSON',
    'GOOGLE_APPLICATION_CREDENTIALS', 'GH_TOKEN', 'GITHUB_TOKEN']) delete env[key];
  await new Promise((resolve, reject) => {
    const child = spawn(program, args, { cwd, stdio: 'inherit', env });
    child.on('error', reject);
    child.on('exit', code => code === 0 ? resolve() : reject(new Error(`${path.basename(program)} failed (exit ${code})`)));
  });
}

async function downloadAsset(asset) {
  if (!/^[0-9a-f-]{36}$/i.test(asset.assetId) || !/^[a-f0-9]{64}$/.test(asset.sha256) ||
    !Number.isSafeInteger(asset.size) || asset.size > 100 * 1024 * 1024)
    throw new Error('Invalid private asset declaration');
  const target = path.join(assetsRoot, asset.assetId);
  const sha = crypto.createHash('sha256');
  let bytes = 0;
  const source = bucket.file(`jobs/${id}/assets/${asset.assetId}`).createReadStream();
  source.on('data', chunk => { bytes += chunk.length; sha.update(chunk);
    if (bytes > 100 * 1024 * 1024) source.destroy(new Error('Asset exceeds limit')); });
  await pipeline(source, fs.createWriteStream(target, { flags: 'wx', mode: 0o600 }));
  if (bytes !== asset.size || sha.digest('hex') !== asset.sha256) throw new Error('Private staged asset checksum failed');
}

async function uploadDrive(file, kind) {
  const folder = process.env.DRIVE_FOLDER_ID;
  if (!folder || !/^[A-Za-z0-9_-]{10,200}$/.test(folder)) throw new Error('Configure a private DRIVE_FOLDER_ID');
  let auth;
  if (process.env.DRIVE_REFRESH_TOKEN) {
    auth = new google.auth.OAuth2(process.env.DRIVE_CLIENT_ID, process.env.DRIVE_CLIENT_SECRET);
    auth.setCredentials({ refresh_token: process.env.DRIVE_REFRESH_TOKEN });
  } else {
    auth = new google.auth.GoogleAuth({ scopes: ['https://www.googleapis.com/auth/drive'] });
  }
  const drive = google.drive({ version: 'v3', auth });
  const mimeType = kind === 'apk' ? 'application/vnd.android.package-archive' :
    kind === 'zip' ? 'application/zip' : 'video/mp4';
  const metadata = { name: `saif-${id}.${kind === 'video' ? 'mp4' : kind}`, parents: [folder], mimeType };
  const response = await drive.files.create({ requestBody: metadata,
    media: { mimeType, body: fs.createReadStream(file) }, fields: 'id,name,parents,size',
    supportsAllDrives: true, uploadType: 'resumable' });
  const fileId = response.data.id;
  if (typeof fileId !== 'string' || !/^[A-Za-z0-9_-]{10,200}$/.test(fileId))
    throw new Error('Google Drive did not return a private file ID');
  // This worker never calls permissions.create; no anyone-with-link grants are made.
  return fileId;
}

async function main() {
  await fsp.mkdir(assetsRoot, { recursive: true, mode: 0o700 });
  const [raw] = await bucket.file(`jobs/${id}/input.json`).download();
  if (raw.length > 1_900_000) throw new Error('Job description exceeds staging limit');
  const input = JSON.parse(raw.toString('utf8'));
  if (input.jobId !== id || !['apk', 'video', 'zip'].includes(input.kind) ||
    !Array.isArray(input.assets) || input.assets.length > 20 || !input.ownerUid)
    throw new Error('Private job metadata is invalid');
  await callback('progress', { status: 'building' });
  for (const asset of input.assets) await downloadAsset(asset);
  const outFile = path.join(root, `saif-${id}.${input.kind === 'video' ? 'mp4' : input.kind}`);
  if (input.kind === 'apk' || input.kind === 'zip') {
    const apk = path.join(root, 'apk');
    await prepareApk({ input, assetsRoot, outputDir: apk });
    if (input.kind === 'zip') {
      await run('python3', [path.join(process.cwd(), 'scripts/package_source.py'), apk, outFile], process.cwd());
    } else {
      process.env.SAIF_VERIFIED_MAVEN_DIR = path.join(root, 'verified-maven');
      await run('bash', [path.join(process.cwd(), 'scripts/verify-compiler.sh')], process.cwd());
      // ubuntu-24.04 supplies Gradle. The wrapper distribution may be blocked by the runner CDN.
      await run('gradle', [':app:assembleDebug', '--no-daemon', '--no-configuration-cache', '--stacktrace'], apk);
      await fsp.copyFile(path.join(apk, 'app/build/outputs/apk/debug/app-debug.apk'), outFile);
    }
  } else {
    const inputJson = path.join(root, 'video.json');
    const scratch = path.join(root, 'frames');
    await fsp.mkdir(scratch);
    await saveVideo(input, inputJson);
    await run('python3', [path.join(process.cwd(), 'scripts/render_video.py'), inputJson, assetsRoot, outFile, scratch], process.cwd());
  }
  const stats = await fsp.stat(outFile);
  if (stats.size <= 1024 || stats.size > 2 * 1024 * 1024 * 1024) throw new Error('Invalid artifact size');
  const driveFileId = await uploadDrive(outFile, input.kind);
  await callback('complete', { status: 'ready', driveFileId });
}

try { await main(); }
catch (error) {
  // Never log prompt, bearer tokens, private download claims, or Drive credentials.
  const message = String(error.message || 'Cloud worker failed').replace(/[\r\n<>]/g, ' ').slice(0, 180);
  console.error('SAIF AI worker:', message);
  try { await callback('complete', { status: 'failed', error: message }); } catch { /* setup errors may precede job creation */ }
  process.exitCode = 1;
} finally { await fsp.rm(root, { recursive: true, force: true }); }
