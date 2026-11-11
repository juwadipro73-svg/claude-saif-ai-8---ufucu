# SaifAI Builder ↔ Gateway — REAL contract (v2)

स्रोत: app repo PR #4 @ `f29e032`, owner द्वारा 2026-11-08 को chat में दिया गया।
पहलेका अनुमानित **v1 schema हटा दिया गया है** — इस document और workflow में कोई
`prompt`/`callback_url`/`artifact_types` dispatch input, HMAC/X-Saif-* header,
`downloadTokens`, या `/d/<token>` claim link **नहीं** है।

## 1. Dispatch (gateway → builder)

```
POST /repos/shahudgg/SaifAI-Builder/actions/workflows/build.yml/dispatches
{ "ref": "main", "inputs": { "job_id": "<UUID>" } }
```
- केवल `job_id`। बाकी सब staged input में है।

## 2. Staged input (private GCS)

```
gs://<SAIF_GCS_BUCKET>/jobs/<job_id>/input.json
{
  "jobId": "<UUID>",           # dispatch से आए job_id से EXACT match होना चाहिए
  "ownerUid": "<uid>",
  "kind": "apk" | "zip" | "video",
  "prompt": "...",
  "project": { ... },
  "assets": [ {"name": "...", "size": <bytes>, "sha256": "<hex>"} , ... ]
         # या {"path": "<gcs-key>", "size": ..., "sha256": ...};
         # path न हो तो jobs/<job_id>/assets/<name> माना जाता है
}
```

Worker हर asset stage से download करके **byte size + SHA-256** verify करता है;
mismatch पर build रोककर `failed` callback जाती है। `ownerUid` logs में नहीं छपता।

## 3. Worker callbacks (केवल Bearer — कभी HMAC नहीं)

```
POST <SAIF_CLOUD_API_BASE_URL>/api/worker/progress
Authorization: Bearer <WORKER_CALLBACK_SECRET>
Content-Type: application/json
{"jobId":"<UUID>","status":"building"}

POST <SAIF_CLOUD_API_BASE_URL>/api/worker/complete
Authorization: Bearer <WORKER_CALLBACK_SECRET>
{"jobId":"<UUID>","status":"ready","driveFileId":"<PRIVATE_DRIVE_ID>"}
            या
{"jobId":"<UUID>","status":"failed","error":"<message>"}
```

- `API base` origin (`https://host`) या `/api` तक दोनों स्वीकार्य — worker normalize
  करता है (`/api/api/…` कभी नहीं बनता)।
- अगर कोई step fail हो जाए तो `error` में उस step का नाम + run URL रहता है।

## 4. Gateway-side (worker का काम **नहीं**)

complete मिलने के बाद **gateway**: private Drive file verify करता है, RTDB
`cloudJobs/<jobId>` और owner-visible `apps/<uid>/<jobId>` update करता है, claim token
मेंट करता है। One-time link:

```
<portal>/download/<jobId>#claim=<TOKEN>     (claim token URL fragment में)
portal → POST /api/inspectDownload {job, claim}
portal → POST /api/claimDownload  {job, claim}   (one-time consume)
```

Worker RTDB लिखता ही नहीं; इसलिए workflow में firebase scope और FIREBASE_DB_URL
इस्तेमाल नहीं होते (name भविष्य के किसी काम के लिए repo settings में रह सकता है)।

## 5. Phone file upload — अलग flow (यहाँ कुछ मत करो)

```
app → POST /api/push (authenticated) → signed GCS PUT → POST /api/push/complete
    → private Drive copy → one-time portal link
```
Builder workflow runs इसे प्रभावित नहीं करते। फोन पर live upload test तभी: जब
gateway+portal deployed हों, तीनों public Actions URL variables सेट हों, और
APK rebuild हो चुका हो।

## 6. Required NAMES (reuse-only policy)

| Name | Kind | Requirement |
|------|------|-------------|
| `GCP_CREDENTIALS` | secret | SA JSON; scopes `drive.file` + `devstorage.read_only`; SA को Drive folder पर **Editor** और jobs bucket पर **storage.objectViewer** |
| `GDRIVE_FOLDER_ID` | secret | private folder id |
| `WORKER_CALLBACK_SECRET` | secret | gateway callbacks की Bearer key (exact यही name) |
| `SAIF_CLOUD_API_BASE_URL` | secret **या** variable | gateway base (जैसा ऊपर) |
| `SAIF_GCS_BUCKET` | secret **या** variable | staged-jobs bucket (`GCS_BUCKET` fallback भी स्वीकार्य) |

## 7. Test ladder

| Level | क्या | स्थिति |
|-------|------|--------|
| L-offline | `tests/run_tests.sh` — stubbed curl पर पूरा contract (25+ asserts) | **इस commit में run किया गया — pass** |
| L1 | UI dispatch with real staged `jobs/<uuid>/input.json` | owner-side (से प्रत्यक्ष run) |
| L2 | gateway पर ready callback दिखे → `cloudJobs/` + `apps/` updated | gateway deployed होने पर |
| L3 | portal `/download/<jobId>#claim=…` एक बार चले, दूसरी बार reject | portal deployed होने पर |
| L4 | phone app → build request → claim link app में | तीनों URL variables + APK rebuild के बाद |
