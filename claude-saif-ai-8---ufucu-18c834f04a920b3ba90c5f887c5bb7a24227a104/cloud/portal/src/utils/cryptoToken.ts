/**
 * SAIF AI - Cryptographic & Token Utility Module
 * Handles rotating token generation, one-time link validation,
 * checksum computation, and link security logic.
 */

export interface VaultAccessRule {
  isBurned?: boolean;
  downloadCount: number;
  maxDownloads?: number; // 1 for strictly one-time view/download
  expiresAt: number | null; // timestamp in ms, or null for no expiry
  burnOnDownload?: boolean;
}

export interface DynamicLinkInfo {
  token: string;
  timeSlot: number;
  remainingSeconds: number;
  timeStepSeconds: number;
  expiresInMs: number;
}

/**
 * Generates a high-entropy cryptographically safe random ID
 */
export function generateId(prefix: string = 'saif', length: number = 16): string {
  const chars = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
  let result = '';
  // Works in both browser (window.crypto) and Node (globalThis.crypto)
  if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
    const values = new Uint8Array(length);
    crypto.getRandomValues(values);
    for (let i = 0; i < length; i++) {
      result += chars[values[i] % chars.length];
    }
  } else {
    for (let i = 0; i < length; i++) {
      result += chars.charAt(Math.floor(Math.random() * chars.length));
    }
  }
  return `${prefix}_${result}`;
}

/**
 * Simple yet robust hash function for deterministic time-based token generation
 */
export function hashString(str: string): string {
  let hash1 = 5381;
  let hash2 = 52711;
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i);
    hash1 = ((hash1 << 5) + hash1) ^ char;
    hash2 = ((hash2 << 5) + hash2) ^ char;
  }
  const h1 = (hash1 >>> 0).toString(16).padStart(8, '0');
  const h2 = (hash2 >>> 0).toString(16).padStart(8, '0');
  return `${h1}${h2}`;
}

/**
 * Generates a dynamic rotating link token based on a secret and current time window
 * Changes every `timeStepSeconds` (default: 30 seconds)
 */
export function generateRotatingToken(
  secret: string,
  timeStepSeconds: number = 30,
  nowMs: number = Date.now()
): DynamicLinkInfo {
  const timeStepMs = timeStepSeconds * 1000;
  const timeSlot = Math.floor(nowMs / timeStepMs);
  const rawKey = `saif_rotate:${secret}:${timeSlot}`;
  const token = hashString(rawKey);

  const slotStartMs = timeSlot * timeStepMs;
  const nextSlotMs = slotStartMs + timeStepMs;
  const remainingSeconds = Math.max(0, Math.ceil((nextSlotMs - nowMs) / 1000));

  return {
    token,
    timeSlot,
    remainingSeconds,
    timeStepSeconds,
    expiresInMs: nextSlotMs - nowMs,
  };
}

/**
 * Verifies a rotating token with configurable window tolerance (e.g. ±1 window for network latency)
 */
export function verifyRotatingToken(
  providedToken: string,
  secret: string,
  timeStepSeconds: number = 30,
  toleranceWindows: number = 1,
  nowMs: number = Date.now()
): boolean {
  if (!providedToken || !secret) return false;
  const timeStepMs = timeStepSeconds * 1000;
  const currentSlot = Math.floor(nowMs / timeStepMs);

  for (let offset = -toleranceWindows; offset <= toleranceWindows; offset++) {
    const slot = currentSlot + offset;
    const expected = hashString(`saif_rotate:${secret}:${slot}`);
    if (providedToken.toLowerCase() === expected.toLowerCase()) {
      return true;
    }
  }
  return false;
}

/**
 * Checks if a user can access or view a vault file
 */
export function canAccessVault(
  rule: VaultAccessRule,
  nowMs: number = Date.now()
): { allowed: boolean; reason?: 'burned' | 'expired' | 'download_limit_reached' } {
  if (rule.isBurned) {
    return { allowed: false, reason: 'burned' };
  }

  if (rule.expiresAt !== null && nowMs > rule.expiresAt) {
    return { allowed: false, reason: 'expired' };
  }

  if (rule.maxDownloads !== undefined && rule.downloadCount >= rule.maxDownloads) {
    return { allowed: false, reason: 'download_limit_reached' };
  }

  return { allowed: true };
}

/**
 * Helper to format file sizes with clean unit labels
 */
export function formatBytes(bytes: number, decimals: number = 2): string {
  if (bytes === 0) return '0 Bytes';
  const k = 1024;
  const dm = decimals < 0 ? 0 : decimals;
  const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(dm))} ${sizes[i]}`;
}

/**
 * Categorizes a file extension into visual display types
 */
export function getFileCategory(filename: string, mimeType?: string): 'image' | 'video' | 'audio' | 'pdf' | 'code' | 'archive' | 'document' | 'other' {
  const ext = filename.split('.').pop()?.toLowerCase() || '';
  if (['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg', 'avif', 'bmp'].includes(ext) || mimeType?.startsWith('image/')) {
    return 'image';
  }
  if (['mp4', 'webm', 'ogg', 'mov', 'mkv', 'avi'].includes(ext) || mimeType?.startsWith('video/')) {
    return 'video';
  }
  if (['mp3', 'wav', 'flac', 'aac', 'm4a'].includes(ext) || mimeType?.startsWith('audio/')) {
    return 'audio';
  }
  if (ext === 'pdf' || mimeType === 'application/pdf') {
    return 'pdf';
  }
  if (['js', 'ts', 'jsx', 'tsx', 'py', 'json', 'html', 'css', 'sql', 'cpp', 'java', 'go', 'rs', 'sh', 'yaml', 'yml'].includes(ext)) {
    return 'code';
  }
  if (['zip', 'rar', '7z', 'tar', 'gz', 'bz2'].includes(ext)) {
    return 'archive';
  }
  if (['txt', 'md', 'doc', 'docx', 'csv', 'xls', 'xlsx', 'ppt', 'pptx'].includes(ext)) {
    return 'document';
  }
  return 'other';
}
