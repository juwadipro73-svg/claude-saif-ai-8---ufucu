# Integration Status — 2026-11-08 (ईमानदार ledger)

## ✅ Verified इस session में (sandbox से)

| Item | Fact | Proof |
|------|------|-------|
| Builder repo | private, default branch `main`, single file `.github/workflows/build.yml` | `gh repo view` |
| Workflow state | `SaifAI Auto Builder Master` active, **1 historical run** `42459024082` success (उसमें केवल placeholder build + "Setup Environment" step — कोई Drive upload step नहीं था, यानी पिछली success ने कुछ upload नहीं किया) | actions API + run/job listing |
| `adityak74/google-drive-upload-action` | **repo deleted/404** — existing yml आज fail होगा Drive step पर | api.github.com/repos/... 404 |
| `google-github-actions/auth` tags | v2.1.13 / v3 उपलब्ध | api.github.com |
| App repo access | `shahudgg/claude-saif-ai-8---ufucu` — इस session token से **पूरी तरह inaccessible** (HTTP 403 Resource not accessible by integration) — PR #4, files, runs कुछ भी read नहीं हो सकता | gh api |
| Secret names list | **403** — token को `actions/secrets` read scope नहीं; names confirm करना owner-side UI काम है | gh secret list |
| External network | sandbox से `ai.studio`, `run.app`, `firebaseio.com`, `googleapis.com` सब पर TLS block (HTTP 000) — केवल github.com + npm/pypi allowlist खुले हैं | curl SSL_ERROR_SYSCALL |

## 🔧 इस PR में किया गया

- `build.yml` पूरी तरह rewrite: gateway contract v1 (`job_id`, `callback_url`,
  `artifact_types`), validated inputs, private Drive multipart upload (private files,
  no public links), one-time token mint + masked logs, authenticated RTDB writes to
  `/downloadTokens/{token}` + `/jobs/{jobId}` (not `/apps.json`), HMAC-signed
  completion callback (always), encrypted Actions artifact fallback, `permissions:
  contents: read`, permissions/injection fixes (prompt sanitize, quoted curl, `-sfS` fail-fast)।
- `docs/GATEWAY_CONTRACT.md`: dispatch/callback/RTDB schemas + secret-name mapping
  table + L0–L4 test plan।
- `docs/INTEGRATION_STATUS.md`: यह file।

## ❌ Blocked — मैं verify/run नहीं कर सकता (owner action चाहिए)

1. **Secret/variable names live-validate** — UI में जाकर names confirm करना होगा
   (§6 table में exact names दिए हैं)।
2. **नए names create** — `SAIF_GATEWAY_CALLBACK_TOKEN` (secret),
   `SAIF_DOWNLOAD_PORTAL_URL`, `SAIF_CLOUD_API_BASE_URL` (variables)।
3. **Manual test dispatch (L1)** — मैं REST dispatch नहीं कर सकता (dispatch call 403);
   owner को Actions → Run workflow से L1 चलाना होगा।
4. **Drive/RTDB/portal reachability** — sandbox network block; L2–L4 केवल GitHub
   runner / owner environment से होगा।
5. **App-side contract match** — PR #4 के gateway file (`cloud/backend`,
   `cloud/builder-template`) मुझे repo access न होने से पढ़े नहीं जा सके। अगर वहाँ
   callback schema अलग है, yml को उसी से align करना होगा — file paste/push सबसे तेज़ रास्ता।

## अगला कदम (owner)

1. §6 की names create/confirm करें (values आपके पास ही रहें)।
2. Actions → "SaifAI Auto Builder Master" → Run workflow (inputs खाली) = **L0**;
   फिर secrets डालकर `prompt=TestApp` = **L1**।
3. L1 pass होते ही `webhook.site` पर L2 (signed callback)।
4. Portal deploy कोड app repo में है → मुझे वो files दिखाएँ तो मैं portal का
   one-time-consume logic verify/complete करूँगा।
