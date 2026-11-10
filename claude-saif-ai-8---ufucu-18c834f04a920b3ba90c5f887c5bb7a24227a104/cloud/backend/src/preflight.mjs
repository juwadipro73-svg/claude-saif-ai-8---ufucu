// Harmless, unauthenticated probes only. No test job, file upload, claim, or private
// credential is created here. A successful GET of a demo SPA is NOT an API check.
const jobId = '123e4567-e89b-42d3-a456-426614174000';

export function rootUrl(value, name) {
  try {
    const url = new URL(value);
    if (url.protocol !== 'https:' || !url.hostname || url.pathname !== '/' || url.search ||
        url.hash || url.username || url.password) throw new Error('not an HTTPS origin');
    return url.origin;
  } catch {
    throw new Error(`${name} must be an HTTPS origin without a path, query or credentials`);
  }
}

async function probeJson(request, url, options, expectedStatuses, description) {
  let response;
  try {
    response = await request(url, { ...options, redirect: 'manual', signal: AbortSignal.timeout(12_000) });
  } catch {
    throw new Error(`${description}: cannot reach the configured host`);
  }
  const expected = Array.isArray(expectedStatuses) ? expectedStatuses : [expectedStatuses];
  if (!expected.includes(response.status))
    throw new Error(`${description}: expected HTTP ${expected.join('/')}, got HTTP ${response.status} (demo or sign-in redirect is not the gateway)`);
  if (!response.headers.get('content-type')?.toLowerCase().includes('application/json'))
    throw new Error(`${description}: expected JSON, got a webpage or a different content type`);
  try { return await response.json(); }
  catch { throw new Error(`${description}: invalid JSON`); }
}

export async function checkDeployment({ apiBase, portalBase, request = fetch }) {
  const api = rootUrl(apiBase, 'SAIF_CLOUD_API_BASE_URL');
  const portal = rootUrl(portalBase, 'SAIF_DOWNLOAD_PORTAL_URL');
  // The gateway may expose /healthz at root or /api/healthz (portal-as-gateway mode)
  let health;
  try {
    health = await probeJson(request, `${api}/healthz`, { method: 'GET' }, 200, 'Gateway /healthz');
  } catch {
    health = await probeJson(request, `${api}/api/healthz`, { method: 'GET' }, 200, 'Gateway /api/healthz');
  }
  if (health?.ok !== true && health?.status !== 'ok') throw new Error('Gateway health endpoint did not report ok');

  // No bearer/App Check header: the real gateway must reject this BEFORE creating a job.
  const headers = { 'Content-Type': 'application/json' };
  const denied = await probeJson(request, `${api}/api/push`, { method: 'POST', headers,
    body: '{}' }, [401, 501], 'Unauthenticated gateway /api/push');
  if (typeof denied?.error !== 'string') throw new Error('Gateway /api/push did not return a JSON error');

  // A bogus claim cannot consume or inspect a real file; proves the website proxies /api/*.
  // When api === portal (portal acts as both gateway and website), skip this check
  // to avoid infinite self-proxy recursion.
  if (api !== portal) {
    const claim = await probeJson(request, `${portal}/api/inspectDownload`, { method: 'POST', headers,
      body: JSON.stringify({ job: jobId, claim: 'invalid' }) }, [404, 502], 'Website /api/inspectDownload');
    if (typeof claim?.error !== 'string') throw new Error('Website /api/inspectDownload did not return a JSON error');
  }

  return { gateway: true, portal: true };
}
