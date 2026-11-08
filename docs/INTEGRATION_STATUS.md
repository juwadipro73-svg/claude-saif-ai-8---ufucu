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

## Owner answers (2026-11-08)

- ✅ `GCP_CREDENTIALS`, `GDRIVE_FOLDER_ID`, `FIREBASE_DB_URL` — **तीनों names repo में मौजूद हैं** (owner confirmed)।
- ✅ नई names create नहीं करनी — **reuse-only**: `SAIF_DOWNLOAD_PORTAL_URL`, `SAIF_CLOUD_API_BASE_URL`, `SAIF_FIREBASE_DATABASE_URL` को secret/variable जैसे भी हैं first-nonempty resolve होगा; callback key के लिए `SAIF_GATEWAY_CALLBACK_TOKEN` / `SAIF_CALLBACK_TOKEN` / `SAIF_GATEWAY_TOKEN` में से जो मौजूद हो (none → unsigned callback + warning)।
- ✅ Build trigger: **app backend/website से REST `workflow_dispatch`** (job_id + callback_url inputs) — wiring इसी पर है।
- ⏳ Owner PR #4 के key gateway files **paste** करेंगे → schema final align होगा।

## ❌ Blocked — मैं verify/run नहीं कर सकता (owner action चाहिए)

1. **Secret/variable values की live sanity** — value-level जाँच (folder id valid, RTDB URL reachable) पहली live run में ही होगी; मैं names list तक नहीं पढ़ सकता (403)।
2. **Manual test dispatch (L1)** — REST dispatch मेरे token से 403; owner को Actions → Run workflow से L1 चलाना होगा (दोबारा: पहले L0 नहीं — secrets confirmed हैं, सीधे `prompt=TestApp` से L1 उचित)।
3. **Drive/RTDB/portal reachability** — sandbox network block; L2–L4 केवल GitHub runner / owner environment से।
4. **App-side contract match** — `cloud/backend` + `cloud/builder-template` files की copy आते ही callback schema/dispatch shape final match करूँगा।

## अगला कदम (owner)

1. Actions → "SaifAI Auto Builder Master" → Run workflow, `prompt=TestApp` = **L1**: pass पर private Drive files + RTDB `/downloadTokens/<t>` दोनों बनेंगे।
2. L1 pass → `callback_url=https://webhook.site/<uuid>` डालकर job_id के साथ run = **L2** signed/unsigned callback देखें।
3. Gateway files paste करें → मैं portal one-time-consume (`/d/{token}` → Drive stream + `used:true`) और dispatch code वहीं complete/align कर दूँगा।
