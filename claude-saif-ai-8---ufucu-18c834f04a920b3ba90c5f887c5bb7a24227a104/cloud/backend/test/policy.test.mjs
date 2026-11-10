import test from 'node:test';
import assert from 'node:assert/strict';
import { claimMatches, createClaim, validateSubmission, validSourcePath } from '../src/policy.mjs';

const jobId = '123e4567-e89b-42d3-a456-426614174000';
const assetId = '123e4567-e89b-42d3-a456-426614174001';
const secret = 'test-secret-with-at-least-thirty-two-characters';
const source = { type: 'android', appName: 'Runner', packageName: 'com.saif.runner',
  files: [{ path: 'app/src/main/assets/index.html', content: '<html>Game</html>' }] };

const base = () => ({ jobId, kind: 'apk', prompt: 'Build the game APK', project: structuredClone(source), assets: [] });

test('allows only bounded, plain source; rejects traversal and build-system overrides', () => {
  assert.equal(validateSubmission(base()).project.files.length, 1);
  // Project Studio writes its manifest at the main source root. It is safe to
  // stage, unlike a user-supplied Gradle script or an arbitrary root file.
  assert.equal(validSourcePath('app/src/main/AndroidManifest.xml'), true);
  const withManifest = base();
  withManifest.project.files.push({ path: 'app/src/main/AndroidManifest.xml', content: '<manifest />' });
  assert.equal(validateSubmission(withManifest).project.files.length, 2);
  for (const path of ['../../secrets', 'app/src/main/assets/../../build.gradle.kts',
    'app/build.gradle.kts', 'app/src/main/java/MainActivity.sh', '/app/src/main/assets/index.html']) {
    assert.equal(validSourcePath(path), false, path);
    const bad = base(); bad.project.files[0].path = path;
    assert.throws(() => validateSubmission(bad), /Unsafe/);
  }
  const oversized = base(); oversized.project.files[0].content = 'x'.repeat(512_001);
  assert.throws(() => validateSubmission(oversized), /512 KB/);
  const wrong = base(); wrong.kind = 'video';
  assert.throws(() => validateSubmission(wrong), /type does not match/);
});

test('requires declared, private, bounded binary assets', () => {
  const body = base(); body.assets = [{ assetId, mimeType: 'image/png', size: 120,
    sha256: 'a'.repeat(64), path: 'app/src/main/res/drawable/logo.png' }];
  assert.throws(() => validateSubmission(body), /createDraftJob/);
  assert.equal(validateSubmission(body, true).assets.length, 1);
  body.assets[0].path = 'app/build.gradle.kts';
  assert.throws(() => validateSubmission(body, true), /Unsafe binary/);
  body.assets[0].path = 'app/src/main/res/drawable/logo.png';
  body.assets[0].size = 101 * 1024 * 1024;
  assert.throws(() => validateSubmission(body, true), /100 MB/);
});

test('video timelines require declared assets and a safe duration', () => {
  const body = { jobId, kind: 'video', prompt: 'Render video', assets: [],
    project: { type: 'video', name: 'Intro', width: 720, height: 1280, fps: 30,
      durationUs: 3_000_000, items: [{ type: 'image', startUs: 0, durationUs: 3_000_000, assetId }] } };
  assert.throws(() => validateSubmission(body), /media asset missing/);
  body.project.items[0] = { type: 'text', startUs: 0, durationUs: 3_000_000,
    text: 'Hello', fontSize: 24, color: '#00ffee' };
  assert.equal(validateSubmission(body).project.items[0].text, 'Hello');
  body.project.bitrate = 18_000_000;
  assert.equal(validateSubmission(body).project.bitrate, 18_000_000);
  body.project.bitrate = 80_000_000;
  assert.throws(() => validateSubmission(body), /video bitrate/);
  body.project.bitrate = 18_000_000;
  body.project.durationUs = 60 * 60 * 1_000_000;
  assert.throws(() => validateSubmission(body), /2 GB private delivery limit/);
  body.project.durationUs = 3_000_000;
  body.project.items[0].durationUs = 9_000_000;
  assert.throws(() => validateSubmission(body), /outside video/);
  body.project.items[0].durationUs = 3_000_000;
  body.assets = [{ assetId, mimeType: 'audio/mpeg', size: 120, sha256: 'a'.repeat(64) }];
  body.project.items[0] = { type: 'image', startUs: 0, durationUs: 3_000_000, assetId };
  assert.throws(() => validateSubmission(body, true), /image MIME mismatch/);
});

test('claims are unguessable, expire and cannot be checked after use', () => {
  const now = Date.now();
  const first = createClaim(secret, now);
  const second = createClaim(secret, now);
  assert.notEqual(first.token, second.token);
  assert.equal(first.token.length >= 32, true);
  assert.equal(claimMatches(first.token, first.stored, secret, now + 30_000), true);
  assert.equal(claimMatches(second.token, first.stored, secret, now), false);
  assert.equal(claimMatches(first.token, first.stored, secret, now + 10 * 60_000), false);
  assert.equal(claimMatches(first.token, { ...first.stored, used: true }, secret, now), false);
});
