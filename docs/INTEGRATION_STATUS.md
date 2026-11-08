# Integration Status — 2026-11-08 (ईमानदार ledger)

## Real contract v2 — owner ने PR #4 (f29e032) का सही contract दिया

**Mismatches fixed in PR #2 (v1 गलतियाँ → v2):**

| v1 (गलत) | v2 (असली contract) |
|-----------|---------------------|
| dispatch inputs: prompt, callback_url, artifact_types | केवल `job_id` (UUID) |
| prompt workflow input से | prompt/kind/project/assets को GCS `jobs/<job_id>/input.json` से |
| HMAC-SHA256 `X-Saif-Signature` header | केवल `Authorization: Bearer WORKER_CALLBACK_SECRET` |
| एक callback endpoint (any URL) | fixed endpoints `/api/worker/progress` + `/api/worker/complete` |
| कई artifacts + files{} map | एक kind → एक artifact → एक `driveFileId` |
| worker खुद token mint + RTDB `/downloadTokens`, `/jobs` लिखता | RTDB/claim पूरी तरह gateway-side (`cloudJobs/<jobId>`, `apps/<uid>/<jobId>`, `/download/<jobId>#claim=…`) |
| portal `/d/<token>` | portal `/download/<jobId>#claim=<TOKEN>` + `inspect/claimDownload` |

**Architecture change**: heavy logic अब `scripts/*.sh` में (workflow thin wrapper) —
इसलिए पूरा contract offline testable है। `tests/run_tests.sh` stubbed `curl` के साथ
~30 asserts चलाता है (dispatch shape, GCS staging, SHA-256/size verification,
Bearer callback bodies byte-for-byte, failure path, forbidden legacy patterns)।

Phone upload flow (`/api/push` → signed GCS PUT → `/api/push/complete` → Drive →
one-time link) अलग है — builder runs उसे fix नहीं करते (owner ने confirm किया)।

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

## 🔧 इस PR में किया गया (final, contract v2)

- `build.yml` thin wrapper: **केवल `job_id`** input; सारा काम `scripts/*.sh` में।
- `scripts/10_validate.sh` — UUID check + required NAMES (WORKER_CALLBACK_SECRET,
  SAIF_CLOUD_API_BASE_URL, SAIF_GCS_BUCKET included), missing name exact-error.
- `scripts/20_fetch_input.sh` — private GCS `jobs/<job_id>/input.json` (JSON API,
  SA bearer), jobId exact-match, kind ∈ apk|zip|video, prompt sanitize।
- `scripts/30_progress.sh` — `POST /api/worker/progress {"jobId","status":"building"}`
  Bearer से (non-fatal on failure)।
- `scripts/40_verify_assets.sh` — हर asset का size + SHA-256, mismatch → fail।
- `scripts/50_build.sh` — kind से एक artifact (apk/zip/video placeholder; credentials
  कभी embed नहीं)।
- `scripts/60_drive_upload.sh` — multipart Drive upload, file PRIVATE रहती है।
- `scripts/90_callback_complete.sh` — `ready` {jobId,status,driveFileId} या
  `failed` {jobId,status,error: <failing step + run URL>}, हमेशा (always())।
- `tests/run_tests.sh` — offline contract test-suite (stubbed curl)।
- 404-wाला third-party Drive action हटा दिया; `google-github-actions/auth@v2.1.13`।

## Owner answers (2026-11-08)

- ✅ `GCP_CREDENTIALS`, `GDRIVE_FOLDER_ID`, `FIREBASE_DB_URL` repo में मौजूद (FIREBASE_DB_URL workflow को अब चाहिए नहीं — RTDB gateway-side)।
- ✅ नई names create किए बिना reuse; contract के हिसाब से worker को चाहिए: + `WORKER_CALLBACK_SECRET`, `SAIF_CLOUD_API_BASE_URL`, `SAIF_GCS_BUCKET` (secret या variable)।
- ✅ Trigger: gateway से REST `workflow_dispatch` (inputs केवल job_id)।
- ✅ Real contract owner ने paste किया — v1 हटाकर v2 align हो गया।

## ❌ Blocked — मैं verify/run नहीं कर सकता (owner action चाहिए)

1. **Value-level sanity** — WORKER_CALLBACK_SECRET असल में gateway पर configured same key से match करता है या नहीं, bucket/folder ids valid हैं या नहीं — पहली live run में ही पता चलेगा।
2. **L1 live dispatch** — मेरे token से dispatch/403; owner: Actions UI → Run workflow, `job_id` = एक real staged UUID (gateway से) या manually staged `gs://<bucket>/jobs/<uuid>/input.json`।
3. **Drive/GCS/gateway reachability** — sandbox network block; runner से ही होगा।
4. **Phone-upload flow** — अलग codepath (app `POST /api/push` …); इस PR से अछूता; live test तभी जब gateway+portal deployed, तीनों public URL variables set, APK rebuilt।
5. **PR #4 repo read-access** — files मुझे सीधे नहीं खुलतीं (403); अगर schema और झुकाना हो तो snippets paste करें।

## अगला कदम (owner)

1. Repo secrets/vars में ये names की उपस्थिति confirm: `WORKER_CALLBACK_SECRET`, `SAIF_CLOUD_API_BASE_URL`, `SAIF_GCS_BUCKET` (+ पहले से confirmed तीनों)।
2. Gateway से एक real job stage करके (या हाथ से `input.json` डालकर) UI से L1 run।
3. L1 green → gateway पर job `ready` दिखे, RTDB `cloudJobs/<jobId>` update (gateway code), portal से claim एक बार/दो बार = L2/L3।
