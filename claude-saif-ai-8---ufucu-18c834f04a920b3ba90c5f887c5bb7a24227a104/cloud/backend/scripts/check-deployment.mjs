import { checkDeployment } from '../src/preflight.mjs';

try {
  await checkDeployment({ apiBase: process.env.SAIF_CLOUD_API_BASE_URL,
    portalBase: process.env.SAIF_DOWNLOAD_PORTAL_URL });
  console.log('Gateway and same-origin portal passed the safe unauthenticated route checks.');
  console.log('This is NOT an end-to-end upload, App Check, Drive, Builder or one-time-claim test.');
} catch (error) {
  console.error(`Cloud deployment preflight failed: ${error.message}`);
  process.exitCode = 1;
}
