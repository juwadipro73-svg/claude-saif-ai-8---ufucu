import test from 'node:test';
import assert from 'node:assert/strict';
import {
  generateId,
  hashString,
  generateRotatingToken,
  verifyRotatingToken,
  canAccessVault,
  formatBytes,
  getFileCategory,
} from './cryptoToken';

test('generateId generates unique strings with given prefix', () => {
  const id1 = generateId('saif');
  const id2 = generateId('saif');
  assert.ok(id1.startsWith('saif_'));
  assert.ok(id2.startsWith('saif_'));
  assert.notEqual(id1, id2);
  assert.ok(id1.length >= 20);
});

test('hashString produces deterministic 16-character hex hash', () => {
  const h1 = hashString('test-key-1');
  const h2 = hashString('test-key-1');
  const h3 = hashString('test-key-2');
  assert.equal(h1, h2);
  assert.notEqual(h1, h3);
  assert.equal(h1.length, 16);
});

test('generateRotatingToken produces time-aligned token and remaining time', () => {
  const fixedNow = 1700000000000;
  const result = generateRotatingToken('my-secret-key', 30, fixedNow);
  assert.ok(result.token);
  assert.ok(result.remainingSeconds > 0 && result.remainingSeconds <= 30);
  assert.equal(result.timeStepSeconds, 30);
});

test('verifyRotatingToken validates token in current window', () => {
  const fixedNow = 1700000000000;
  const { token } = generateRotatingToken('vault_secret_99', 30, fixedNow);
  
  // Valid in same window
  const isValid = verifyRotatingToken(token, 'vault_secret_99', 30, 1, fixedNow);
  assert.equal(isValid, true);

  // Invalid with wrong secret
  const isWrongSecret = verifyRotatingToken(token, 'wrong_secret', 30, 1, fixedNow);
  assert.equal(isWrongSecret, false);

  // Invalid with completely mismatched token
  const isInvalidToken = verifyRotatingToken('bad_token', 'vault_secret_99', 30, 1, fixedNow);
  assert.equal(isInvalidToken, false);
});

test('verifyRotatingToken handles tolerance window (±1 window)', () => {
  const timeStepMs = 30000;
  const t0 = 1700000000000;
  const { token } = generateRotatingToken('secret123', 30, t0);

  // t0 + 29s is still in window or within ±1 window tolerance
  const isValidNext = verifyRotatingToken(token, 'secret123', 30, 1, t0 + 25000);
  assert.equal(isValidNext, true);

  // t0 + 120s is far beyond tolerance of 1 window (30s)
  const isExpired = verifyRotatingToken(token, 'secret123', 30, 1, t0 + 120000);
  assert.equal(isExpired, false);
});

test('canAccessVault enforces one-time burn logic and expiry', () => {
  const now = 1700000000000;

  // Active vault
  const active = canAccessVault({
    isBurned: false,
    downloadCount: 0,
    maxDownloads: 1,
    expiresAt: now + 60000,
  }, now);
  assert.equal(active.allowed, true);

  // Burned vault (already viewed or downloaded)
  const burned = canAccessVault({
    isBurned: true,
    downloadCount: 1,
    maxDownloads: 1,
    expiresAt: now + 60000,
  }, now);
  assert.equal(burned.allowed, false);
  assert.equal(burned.reason, 'burned');

  // Expired vault
  const expired = canAccessVault({
    isBurned: false,
    downloadCount: 0,
    maxDownloads: 1,
    expiresAt: now - 5000,
  }, now);
  assert.equal(expired.allowed, false);
  assert.equal(expired.reason, 'expired');

  // Download limit reached
  const limitReached = canAccessVault({
    isBurned: false,
    downloadCount: 1,
    maxDownloads: 1,
    expiresAt: now + 60000,
  }, now);
  assert.equal(limitReached.allowed, false);
  assert.equal(limitReached.reason, 'download_limit_reached');
});

test('formatBytes handles multiple size orders', () => {
  assert.equal(formatBytes(0), '0 Bytes');
  assert.equal(formatBytes(1024), '1 KB');
  assert.equal(formatBytes(1024 * 1024 * 5.5), '5.5 MB');
  assert.equal(formatBytes(1024 * 1024 * 1024 * 2), '2 GB');
});

test('getFileCategory properly classifies extensions', () => {
  assert.equal(getFileCategory('report.pdf'), 'pdf');
  assert.equal(getFileCategory('picture.png'), 'image');
  assert.equal(getFileCategory('video.mp4'), 'video');
  assert.equal(getFileCategory('audio.mp3'), 'audio');
  assert.equal(getFileCategory('script.py'), 'code');
  assert.equal(getFileCategory('backup.zip'), 'archive');
  assert.equal(getFileCategory('document.docx'), 'document');
});
