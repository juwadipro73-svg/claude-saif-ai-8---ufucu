import test from 'node:test';
import assert from 'node:assert/strict';
import os from 'node:os';
import fs from 'node:fs/promises';
import path from 'node:path';
import { prepareApk, safeDestination } from './prepare-apk.mjs';

const payload = { kind: 'apk', prompt: 'Build my puzzle game', assets: [],
  project: { type: 'android', appName: 'Puzzle & Play', packageName: 'com.saif.puzzle',
    files: [{ path: 'app/src/main/assets/index.html', content: '<!doctype html><html><body><h1>Game</h1></body></html>' }] } };

test('isolated builder uses fixed Gradle template, copies HTML and writes a safe manifest', async t => {
  const scratch = await fs.mkdtemp(path.join(os.tmpdir(), 'saif-apk-test-'));
  t.after(() => fs.rm(scratch, { recursive: true, force: true }));
  const outputDir = path.join(scratch, 'apk');
  const result = await prepareApk({ input: payload, assetsRoot: scratch, outputDir });
  assert.equal(result.appId, 'com.saif.puzzle');
  const manifest = await fs.readFile(path.join(outputDir, 'app/src/main/AndroidManifest.xml'), 'utf8');
  assert.match(manifest, /Puzzle &amp; Play/);
  assert.match(manifest, /com.saif.puzzle.MainActivity/);
  assert.match(await fs.readFile(path.join(outputDir, 'app/src/main/java/com/saif/puzzle/MainActivity.java'), 'utf8'), /setBlockNetworkLoads\(true\)/);
  assert.match(await fs.readFile(path.join(outputDir, 'app/src/main/res/drawable/ic_launcher.xml'), 'utf8'), /<vector/);
  assert.match(await fs.readFile(path.join(outputDir, 'app/src/main/res/mipmap/ic_launcher.xml'), 'utf8'), /<vector/);
  assert.equal((await fs.stat(path.join(outputDir, 'gradlew'))).mode & 0o111, 0o111);
});

test('preserves a Studio manifest without allowing arbitrary root overrides', async t => {
  const scratch = await fs.mkdtemp(path.join(os.tmpdir(), 'saif-manifest-test-'));
  t.after(() => fs.rm(scratch, { recursive: true, force: true }));
  const outputDir = path.join(scratch, 'apk');
  const manifest = '<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application android:label="Studio" /></manifest>';
  const input = structuredClone(payload);
  input.project.files.push({ path: 'app/src/main/AndroidManifest.xml', content: manifest });
  await prepareApk({ input, assetsRoot: scratch, outputDir });
  assert.equal(await fs.readFile(path.join(outputDir, 'app/src/main/AndroidManifest.xml'), 'utf8'), manifest);
  assert.throws(() => safeDestination(outputDir, 'app/src/main/build.gradle.kts'), /Unsafe/);
});

test('custom launcher resource is not replaced by the fallback icon', async t => {
  const scratch = await fs.mkdtemp(path.join(os.tmpdir(), 'saif-custom-icon-test-'));
  t.after(() => fs.rm(scratch, { recursive: true, force: true }));
  const iconId = '123e4567-e89b-42d3-a456-426614174001';
  await fs.writeFile(path.join(scratch, iconId), Buffer.from([0x89, 0x50, 0x4e, 0x47]));
  const outputDir = path.join(scratch, 'apk');
  const input = structuredClone(payload);
  input.assets = [{ assetId: iconId, path: 'app/src/main/res/drawable/ic_launcher.png' }];
  await prepareApk({ input, assetsRoot: scratch, outputDir });
  assert.deepEqual(await fs.readFile(path.join(outputDir, input.assets[0].path)), Buffer.from([0x89, 0x50, 0x4e, 0x47]));
  await assert.rejects(fs.stat(path.join(outputDir, 'app/src/main/res/drawable/ic_launcher.xml')), { code: 'ENOENT' });
});

test('uploaded source cannot replace Gradle scripts or traverse the worker filesystem', () => {
  const root = '/tmp/saif-test';
  assert.throws(() => safeDestination(root, 'app/build.gradle.kts'), /Unsafe/);
  assert.throws(() => safeDestination(root, 'app/src/main/assets/../../../../etc/passwd'), /Unsafe/);
  assert.throws(() => safeDestination(root, 'app/src/main/assets/../AndroidManifest.xml'), /Unsafe/);
  assert.throws(() => safeDestination(root, '/etc/shadow'), /Unsafe/);
  assert.ok(safeDestination(root, 'app/src/main/res/layout/activity_main.xml').startsWith(root + '/'));
});
