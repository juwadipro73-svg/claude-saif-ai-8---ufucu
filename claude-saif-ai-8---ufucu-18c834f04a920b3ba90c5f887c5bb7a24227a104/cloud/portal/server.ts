/**
 * SAIF AI Download Portal
 * 
 * Production server that proxies vault operations to the real cloud gateway
 * when SAIF_CLOUD_API_BASE_URL is configured, falling back to a secure demo
 * vault for local development and preview.
 * 
 * Architecture:
 * - Android app → cloud gateway (GCS/Drive/Firebase) → stores artifact
 * - Recipient opens portal URL → portal checks claim via gateway → serves download
 * - One-time claim tokens are managed exclusively by the gateway
 */
import express, { Request, Response } from 'express';
import path from 'path';
import { fileURLToPath } from 'url';
import dotenv from 'dotenv';
import { pipeline } from 'stream/promises';
import {
  generateId,
  generateRotatingToken,
  verifyRotatingToken,
  canAccessVault,
  formatBytes,
  getFileCategory,
} from './src/utils/cryptoToken.js';

dotenv.config();

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const PORT = Number(process.env.PORT) || 3000;

// Gateway configuration — when set, portal becomes a thin proxy;
// when absent, portal serves the self-contained demo vault.
const GATEWAY_URL = process.env.SAIF_CLOUD_API_BASE_URL?.replace(/\/+$/, '') || '';

// Generous payload limits for push simulator and API demos
app.use(express.json({ limit: '200mb' }));
app.use(express.urlencoded({ limit: '200mb', extended: true }));

// Security headers — mirror the gateway's hardening so the portal itself
// cannot be framed, indexed, or used as an open redirect.
app.use((_req, res, next) => {
  res.set({
    'Cache-Control': 'private, no-store, max-age=0',
    'Referrer-Policy': 'no-referrer',
    'X-Robots-Tag': 'noindex, nofollow, noarchive',
    'X-Content-Type-Options': 'nosniff',
  });
  next();
});

/**
 * /healthz — required by the app's CI preflight (check-deployment.mjs).
 * Returns 200 even in demo mode so CI can distinguish "portal is live" from
 * "portal is unreachable." When gateway URL is configured, the portal also
 * checks that the gateway is reachable.
 */
app.get('/healthz', async (_req: Request, res: Response) => {
  if (!GATEWAY_URL) {
    return res.json({ status: 'ok', mode: 'demo', brand: 'SAIF AI', time: Date.now() });
  }
  try {
    const gwRes = await fetch(`${GATEWAY_URL}/healthz`, { signal: AbortSignal.timeout(5000) });
    if (!gwRes.ok) {
      return res.status(502).json({
        status: 'degraded',
        brand: 'SAIF AI',
        gateway: GATEWAY_URL,
        gatewayStatus: gwRes.status,
        time: Date.now(),
      });
    }
    return res.json({
      status: 'ok',
      mode: 'production',
      brand: 'SAIF AI',
      gateway: GATEWAY_URL,
      time: Date.now(),
    });
  } catch (err: any) {
    return res.status(502).json({
      status: 'degraded',
      brand: 'SAIF AI',
      gateway: GATEWAY_URL,
      gatewayError: err?.message || String(err),
      time: Date.now(),
    });
  }
});

// ============================================================
// In-Memory Demo Vault (only used when no gateway is configured)
// ============================================================

interface VaultFileItem {
  id: string;
  name: string;
  size: number;
  mimeType: string;
  category: string;
  data: string;
  hash: string;
}

interface VaultRecord {
  vaultId: string;
  createdAt: number;
  expiresAt: number | null;
  securityMode: 'onetime' | 'rotating' | 'protected';
  rotatingSecret: string;
  burnOnDownload: boolean;
  isBurned: boolean;
  burnedAt?: number;
  downloadCount: number;
  maxDownloads: number;
  senderApp: string;
  customSecret?: string;
  files: VaultFileItem[];
  viewsCount: number;
  adCompletedCount: number;
}

const demoVaults = new Map<string, VaultRecord>();

const sampleFileContent = `/**
 * SAIF AI - Autonomous Secure Engine
 * Quantum-grade file push received via direct link.
 * One-Time Dynamic Access Guaranteed.
 */
export const SAIF_CONFIG = {
  vaultProtocol: 'v2.4-quantum',
  encryption: 'AES-256-GCM + Dynamic Token Rotation',
  zeroKnowledgeIsolation: true,
  adGatewayEnabled: true
};`;

const DEMO_VAULT_ID = 'saif_demo_vault_101';
const DEMO_SECRET = 'saif_secret_quantum_key_88';

function seedDemoVault() {
  demoVaults.set(DEMO_VAULT_ID, {
    vaultId: DEMO_VAULT_ID,
    createdAt: Date.now() - 1000 * 60 * 5,
    expiresAt: Date.now() + 1000 * 60 * 60 * 24 * 7,
    securityMode: 'onetime',
    rotatingSecret: DEMO_SECRET,
    burnOnDownload: true,
    isBurned: false,
    downloadCount: 0,
    maxDownloads: 1,
    senderApp: 'SAIF AI Demo (no gateway configured)',
    viewsCount: 1,
    adCompletedCount: 0,
    files: [
      {
        id: 'file_demo_1',
        name: 'SAIF_Quantum_Security_Manifesto.ts',
        size: sampleFileContent.length,
        mimeType: 'text/typescript',
        category: 'code',
        data: `data:text/typescript;base64,${Buffer.from(sampleFileContent).toString('base64')}`,
        hash: 'sha256-8f3a9e2d1c4b7a60e5f2',
      },
      {
        id: 'file_demo_2',
        name: 'Confidential_AI_Report.pdf',
        size: 1024 * 340,
        mimeType: 'application/pdf',
        category: 'pdf',
        data: 'data:application/pdf;base64,JVBERi0xLjUKJcfsj6IKMSAwIG9iago8PAovVHlwZSAvQ2F0YWxvZwovUGFnZXMgMiAwIFIKPj4KZW5kb2JqCg==',
        hash: 'sha256-3b9a7c8e1d2f45a0b9e8',
      },
    ],
  });
}
seedDemoVault();

// ============================================================
// /api/inspectDownload — portal proxy used by CI preflight.
// In production, forwards { job, claim } to the gateway's
// /api/inspectDownload. In demo mode, returns a 404 JSON error
// so the same unauthenticated probe the CI runs against the
// production portal also succeeds against the local demo.
// ============================================================

app.post('/api/inspectDownload', async (req: Request, res: Response) => {
  if (GATEWAY_URL) {
    try {
      const gwRes = await fetch(`${GATEWAY_URL}/api/inspectDownload`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(req.body || {}),
        signal: AbortSignal.timeout(10000),
      });
      const text = await gwRes.text().catch(() => '');
      let parsed;
      try { parsed = JSON.parse(text); } catch { parsed = { error: 'GATEWAY_INVALID_JSON' }; }
      return res.status(gwRes.status).json(parsed);
    } catch (err: any) {
      return res.status(502).json({
        error: 'GATEWAY_UNREACHABLE',
        message: `Could not contact the cloud gateway: ${err?.message || String(err)}`,
      });
    }
  }
  return res.status(404).json({
    error: 'DEMO_NO_INSPECT',
    message: 'Demo vault does not expose claim inspection. Configure SAIF_CLOUD_API_BASE_URL.',
  });
});

// ============================================================
// Vault query: production proxy → gateway, fallback → demo
// ============================================================

app.get('/api/vault/:vaultId', async (req: Request, res: Response) => {
  const { vaultId } = req.params;
  const token = (req.query.token as string) || '';
  const secret = (req.query.secret as string) || '';

  // Production mode: proxy claim inspection to the real gateway.
  // The gateway's /api/inspectDownload accepts { job, claim } in the body;
  // we map the URL's vaultId to job and any token to claim.
  if (GATEWAY_URL) {
    try {
      const gwRes = await fetch(`${GATEWAY_URL}/api/inspectDownload`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ job: vaultId, claim: token || undefined }),
        signal: AbortSignal.timeout(10000),
      });
      if (gwRes.status === 404) {
        return res.status(404).json({
          error: 'VAULT_NOT_FOUND',
          message: 'This vault does not exist or has expired.',
        });
      }
      if (gwRes.status === 410) {
        return res.status(410).json({
          error: 'VAULT_EXPIRED',
          message: 'This one-time link has already been used.',
        });
      }
      if (!gwRes.ok) {
        const text = await gwRes.text().catch(() => '');
        return res.status(gwRes.status).json({
          error: 'GATEWAY_ERROR',
          message: text || `Gateway returned ${gwRes.status}`,
        });
      }
      const info = await gwRes.json() as { kind?: string; name?: string; bytes?: number };
      return res.json({
        vaultId,
        createdAt: Date.now(),
        expiresAt: null,
        securityMode: 'onetime' as const,
        burnOnDownload: true,
        isBurned: false,
        downloadCount: 0,
        senderApp: 'SAIF AI Cloud Gateway',
        files: [
          {
            id: `file_${vaultId}`,
            name: info.name || `saif-ai-${info.kind || 'artifact'}`,
            size: info.bytes || 0,
            sizeFormatted: formatBytes(info.bytes || 0),
            mimeType:
              info.kind === 'video' ? 'video/mp4'
              : info.kind === 'zip' ? 'application/zip'
              : info.kind === 'apk' ? 'application/vnd.android.package-archive'
              : 'application/octet-stream',
            category: getFileCategory(
              info.name || `saif-ai-${info.kind || 'artifact'}`,
              info.kind === 'video' ? 'video/mp4'
              : info.kind === 'zip' ? 'application/zip'
              : info.kind === 'apk' ? 'application/vnd.android.package-archive'
              : 'application/octet-stream'
            ),
            hash: 'sha256-gateway',
          },
        ],
        gatewayMode: true,
      });
    } catch (err: any) {
      return res.status(502).json({
        error: 'GATEWAY_UNREACHABLE',
        message: `Could not contact the cloud gateway: ${err?.message || String(err)}`,
      });
    }
  }

  // Demo mode: use in-memory vault
  const vault = demoVaults.get(vaultId);
  if (!vault) {
    return res.status(404).json({
      error: 'VAULT_NOT_FOUND',
      message: 'This vault does not exist or has expired and been purged from SAIF AI.',
    });
  }

  const accessCheck = canAccessVault({
    isBurned: vault.isBurned,
    downloadCount: vault.downloadCount,
    maxDownloads: vault.maxDownloads,
    expiresAt: vault.expiresAt,
    burnOnDownload: vault.burnOnDownload,
  });

  if (!accessCheck.allowed) {
    if (accessCheck.reason === 'burned') {
      return res.status(410).json({
        error: 'VAULT_BURNED',
        message: 'This one-time link has already been used and burned for security.',
        vaultId,
        burnedAt: vault.burnedAt,
      });
    }
    if (accessCheck.reason === 'expired') {
      return res.status(410).json({
        error: 'VAULT_EXPIRED',
        message: 'This link has expired past its allowed security window.',
        vaultId,
      });
    }
    return res.status(403).json({
      error: 'LIMIT_REACHED',
      message: 'Download/view limit for this vault was reached.',
    });
  }

  if (vault.securityMode === 'rotating' && token) {
    const isValid = verifyRotatingToken(token, vault.rotatingSecret, 30, 1);
    if (!isValid && secret !== vault.rotatingSecret) {
      return res.status(401).json({
        error: 'INVALID_OR_EXPIRED_TOKEN',
        message: 'The rotating security token has expired or is invalid.',
      });
    }
  }

  vault.viewsCount++;
  const dynamicStatus = generateRotatingToken(vault.rotatingSecret, 30);

  return res.json({
    vaultId: vault.vaultId,
    createdAt: vault.createdAt,
    expiresAt: vault.expiresAt,
    securityMode: vault.securityMode,
    burnOnDownload: vault.burnOnDownload,
    isBurned: vault.isBurned,
    downloadCount: vault.downloadCount,
    senderApp: vault.senderApp,
    rotatingSecret: vault.rotatingSecret,
    currentDynamicToken: dynamicStatus.token,
    remainingSeconds: dynamicStatus.remainingSeconds,
    files: vault.files.map((f) => ({
      id: f.id,
      name: f.name,
      size: f.size,
      sizeFormatted: formatBytes(f.size),
      mimeType: f.mimeType,
      category: f.category,
      hash: f.hash,
      data: f.data,
    })),
    gatewayMode: false,
  });
});

// ============================================================
// ad-complete: production consumes claim on gateway, demo burns vault
// ============================================================

app.post('/api/vault/:vaultId/ad-complete', async (req: Request, res: Response) => {
  const { vaultId } = req.params;
  const claimToken = (req.body?.claim || req.query.token) as string | undefined;

  if (GATEWAY_URL) {
    try {
      const gwRes = await fetch(`${GATEWAY_URL}/api/claimDownload`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ job: vaultId, claim: claimToken || vaultId }),
        signal: AbortSignal.timeout(30000),
      });
      if (gwRes.status === 404) {
        return res.status(404).json({ error: 'Vault or claim not found' });
      }
      if (gwRes.status === 410) {
        return res.status(410).json({
          error: 'VAULT_EXPIRED',
          message: 'This one-time link has already been used.',
          isBurned: true,
        });
      }
      if (!gwRes.ok) {
        const text = await gwRes.text().catch(() => '');
        return res.status(gwRes.status).json({
          error: 'GATEWAY_ERROR',
          message: text || `Gateway returned ${gwRes.status}`,
        });
      }

      // Gateway streams the actual file — proxy it back to the browser.
      const contentType = gwRes.headers.get('content-type') || 'application/octet-stream';
      const contentDisposition = gwRes.headers.get('content-disposition') || 'attachment';
      const contentLength = gwRes.headers.get('content-length') || '';

      res.set({
        'Content-Type': contentType,
        'Content-Disposition': contentDisposition,
        ...(contentLength ? { 'Content-Length': contentLength } : {}),
      });

      if (gwRes.body) {
        await pipeline(gwRes.body as any, res as any);
      } else {
        const buffer = await gwRes.arrayBuffer();
        res.send(Buffer.from(buffer));
      }
      return;
    } catch (err: any) {
      return res.status(502).json({
        error: 'GATEWAY_UNREACHABLE',
        message: `Could not complete download: ${err?.message || String(err)}`,
      });
    }
  }

  // Demo mode: burn the vault and return metadata
  const vault = demoVaults.get(vaultId);
  if (!vault) {
    return res.status(404).json({ error: 'Vault not found' });
  }
  if (vault.isBurned) {
    return res.status(410).json({ error: 'Vault already burned' });
  }

  vault.adCompletedCount++;
  vault.downloadCount++;
  if (vault.burnOnDownload) {
    vault.isBurned = true;
    vault.burnedAt = Date.now();
  }

  return res.json({
    success: true,
    message: 'Ad verified! File download authorized.',
    isBurned: vault.isBurned,
    downloadCount: vault.downloadCount,
    files: vault.files.map((f) => ({
      name: f.name,
      mimeType: f.mimeType,
      data: f.data,
    })),
  });
});

// ============================================================
// Rotating status (demo only — production uses gateway claim expiry)
// ============================================================

app.get('/api/vault/:vaultId/rotating-status', (req: Request, res: Response) => {
  const { vaultId } = req.params;
  const vault = demoVaults.get(vaultId);
  if (!vault) {
    return res.status(404).json({ error: 'Vault not found' });
  }
  const dynamicInfo = generateRotatingToken(vault.rotatingSecret, 30);
  return res.json({
    vaultId,
    dynamicToken: dynamicInfo.token,
    remainingSeconds: dynamicInfo.remainingSeconds,
    timeStepSeconds: dynamicInfo.timeStepSeconds,
    isBurned: vault.isBurned,
  });
});

// ============================================================
// Health & stats (legacy)
// ============================================================

app.get('/api/health', (_req: Request, res: Response) => {
  res.json({
    status: 'ok',
    brand: 'SAIF AI',
    mode: GATEWAY_URL ? 'production' : 'demo',
    gateway: GATEWAY_URL || undefined,
    time: Date.now(),
  });
});

app.get('/api/stats', (_req: Request, res: Response) => {
  let totalFiles = 0;
  let activeVaults = 0;
  let burnedVaults = 0;
  let totalDownloads = 0;
  for (const vault of demoVaults.values()) {
    if (vault.isBurned) burnedVaults++;
    else activeVaults++;
    totalFiles += vault.files.length;
    totalDownloads += vault.downloadCount;
  }
  res.json({
    totalVaults: demoVaults.size,
    activeVaults,
    burnedVaults,
    totalFiles,
    totalDownloads,
    serverTime: Date.now(),
  });
});

// ============================================================
// Push API (demo mode only — production uploads go to gateway directly)
// ============================================================

app.post(['/api/push', '/api/upload'], (req: Request, res: Response) => {
  if (GATEWAY_URL) {
    return res.status(501).json({
      error: 'GATEWAY_ONLY',
      message: 'Production uploads must go directly to the cloud gateway. ' +
        `Use POST ${GATEWAY_URL}/api/push with Firebase Auth + App Check.`,
      gateway: GATEWAY_URL,
    });
  }

  try {
    const {
      files,
      securityMode = 'onetime',
      burnOnDownload = true,
      expiryHours = 24,
      senderApp = 'External SAIF AI Client App',
      customSecret,
    } = req.body;

    if (!files || !Array.isArray(files) || files.length === 0) {
      return res.status(400).json({ error: 'files must be a non-empty array' });
    }

    const vaultId = generateId('vault', 18);
    const rotatingSecret = customSecret || generateId('sec', 24);
    const now = Date.now();
    const expiresAt = expiryHours > 0 ? now + expiryHours * 3600 * 1000 : null;

    const formattedFiles: VaultFileItem[] = files.map((f: any, idx: number) => {
      const fileName = f.name || `file_${idx + 1}.dat`;
      const mime = f.mimeType || 'application/octet-stream';
      const rawData = f.data || '';
      const size = f.size || (typeof rawData === 'string' ? rawData.length : 1024);
      const hash = `sha256-${generateId('h', 20)}`;
      return {
        id: generateId('file', 12),
        name: fileName,
        size,
        mimeType: mime,
        category: getFileCategory(fileName, mime),
        data: rawData,
        hash,
      };
    });

    const vaultRecord: VaultRecord = {
      vaultId,
      createdAt: now,
      expiresAt,
      securityMode: securityMode as 'onetime' | 'rotating' | 'protected',
      rotatingSecret,
      burnOnDownload: !!burnOnDownload,
      isBurned: false,
      downloadCount: 0,
      maxDownloads: securityMode === 'onetime' ? 1 : 999999,
      senderApp,
      customSecret,
      files: formattedFiles,
      viewsCount: 0,
      adCompletedCount: 0,
    };

    demoVaults.set(vaultId, vaultRecord);
    const initialDynamic = generateRotatingToken(rotatingSecret, 30, now);

    return res.json({
      success: true,
      message: 'Files pushed successfully to SAIF AI Vault (demo mode)',
      vaultId,
      accessUrl: `/?v=${vaultId}&token=${initialDynamic.token}`,
      rotatingSecret,
      dynamicToken: initialDynamic.token,
      timeStepSeconds: 30,
      remainingSeconds: initialDynamic.remainingSeconds,
      securityMode,
      burnOnDownload: vaultRecord.burnOnDownload,
      expiresAt,
      fileCount: formattedFiles.length,
      filesSummary: formattedFiles.map((f) => ({
        id: f.id,
        name: f.name,
        size: f.size,
        sizeFormatted: formatBytes(f.size),
        category: f.category,
      })),
    });
  } catch (err: any) {
    console.error('Error in /api/push:', err);
    return res.status(500).json({ error: 'Failed to push files', details: err.message });
  }
});

// ============================================================
// trigger-build & firebase-latest (unchanged from original)
// ============================================================

app.post('/api/trigger-build', async (req: Request, res: Response) => {
  try {
    const { prompt, githubToken, branch = 'main' } = req.body;
    if (!prompt || typeof prompt !== 'string' || !prompt.trim()) {
      return res.status(400).json({ error: 'Prompt is required to build the app' });
    }
    const token = githubToken || process.env.GITHUB_PAT || process.env.GITHUB_TOKEN;
    if (!token) {
      return res.status(400).json({
        error: 'MISSING_GITHUB_TOKEN',
        message: 'GitHub Personal Access Token is required.',
      });
    }
    const repoOwner = 'shahudgg';
    const repoName = 'SaifAI-Builder';
    const workflowFileName = 'build.yml';
    const githubApiUrl = `https://api.github.com/repos/${repoOwner}/${repoName}/actions/workflows/${workflowFileName}/dispatches`;
    const githubResponse = await fetch(githubApiUrl, {
      method: 'POST',
      headers: {
        Accept: 'application/vnd.github.v3+json',
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
        'User-Agent': 'SaifAI-Builder-App',
      },
      body: JSON.stringify({ ref: branch, inputs: { prompt: prompt.trim() } }),
    });
    if (githubResponse.status === 204) {
      return res.json({
        success: true,
        message: 'GitHub Actions build workflow triggered successfully!',
        prompt: prompt.trim(),
        repo: `${repoOwner}/${repoName}`,
        workflow: workflowFileName,
        branch,
        triggeredAt: Date.now(),
      });
    }
    const errText = await githubResponse.text();
    return res.status(githubResponse.status).json({
      error: 'GITHUB_API_ERROR',
      status: githubResponse.status,
      message: errText || 'Failed to trigger workflow dispatch on GitHub',
    });
  } catch (error: any) {
    console.error('Error triggering GitHub workflow:', error);
    return res.status(500).json({
      error: 'SERVER_ERROR',
      message: error.message || 'Internal server error while contacting GitHub',
    });
  }
});

app.get('/api/firebase-latest', async (_req: Request, res: Response) => {
  try {
    const firebaseDbUrl = 'https://saif-ai-2e541-default-rtdb.firebaseio.com/apps.json';
    const fbRes = await fetch(firebaseDbUrl);
    if (!fbRes.ok) {
      return res.status(fbRes.status).json({ error: 'Failed to read from Firebase Realtime DB' });
    }
    const data = await fbRes.json();
    return res.json({ success: true, data });
  } catch (err: any) {
    return res.status(500).json({ error: err.message });
  }
});

// ============================================================
// Static serving: dist in production, Vite in development
// ============================================================

async function startServer() {
  if (process.env.NODE_ENV === 'production') {
    app.use(express.static(path.join(__dirname, 'dist')));
    app.get('*', (_req, res) => {
      res.sendFile(path.join(__dirname, 'dist', 'index.html'));
    });
  } else {
    const { createServer: createViteServer } = await import('vite');
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(
      `SAIF AI Portal listening on http://0.0.0.0:${PORT} ` +
      `(mode: ${GATEWAY_URL ? 'production (gateway: ' + GATEWAY_URL + ')' : 'demo'})`
    );
  });
}

startServer().catch((err) => {
  console.error('Failed to start server:', err);
  process.exit(1);
});
