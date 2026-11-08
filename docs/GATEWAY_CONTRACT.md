# SaifAI Builder ↔ App Gateway Contract (v1)

कैसे काम करता है: app/Website gateway एक private `job_id` देकर इस repo के workflow को
REST dispatch करता है; builder APK + project ZIP + demo video बनाकर **private** Drive
folder में रखता है, one-time token mint करके Firebase RTDB में sync करता है, और
**HMAC-signed completion callback** gateway को भेजता है। Website इन्हीं टोकन से
one-time download links देती है। Public `drive.google.com/uc?id=...` लिंक कभी नहीं बनती,
और APK/ZIP में credentials कभी embed नहीं होते।

## 1. Dispatch (gateway → इस repo)

```
POST https://api.github.com/repos/shahudgg/SaifAI-Builder/actions/workflows/build.yml/dispatches
Authorization: Bearer <github token with actions:write on this repo>
{
  "ref": "main",
  "inputs": {
    "prompt": "<user prompt / app name>",
    "job_id": "<gateway-generated private job id, [a-zA-Z0-9-]{6,64}>",
    "callback_url": "https://<gateway>/api/build-complete",
    "artifact_types": "apk zip video"
  }
}
```

Notes:
- `workflow_dispatch` inputs repo के collaborator को Actions UI में दिखते हैं — repo
  **private** रखें (वर्तमान में private है ✓)। callback **token** को input में कभी न डालें;
  वह केवल Actions secret में रहे।
- Manual run (inputs खाली) भी काम करता है: `job_id` गायब होने तो `manual-<run_id>`
  माना जाता है।

## 2. Lifecycle

`queued → building → uploaded → completed | failed`

Builder callback body में final status देता है। Drive ids + one-time token केवल
success पर भरे होते हैं।

## 3. Completion callback (builder → gateway)

```
POST {callback_url}
Content-Type: application/json
X-Saif-Signature: sha256=<hmac_sha256_hex(body, SAIF_GATEWAY_CALLBACK_TOKEN)>
X-Saif-Job: <job_id>

{
  "schema": "saif-builder.callback/v1",
  "jobId": "...",
  "status": "completed" | "failed",
  "portalUrl": "<PORTAL_BASE>/d/<one-time-token>",
  "files": { "apk": "<drive file id>", "zip": "...", "video": "..." },
  "run":   { "repo": "shahudgg/SaifAI-Builder", "id": "<run id>" }
}
```

- Gateway उपरोक्त header से HMAC verify करे: `HMAC_SHA256(body, callback_token)`।
- Builder 3 retries (5s spacing) करता है; gateway idempotent रहे (same jobId को
  दोबारा process करके duplicate link न बनाए)।
- failure पर भी callback जाता है (`status: "failed"`, खाली `files`)।

## 4. Firebase RTDB sync (builder → RTDB, authenticated REST)

Paths (root `https://<db>.firebaseio.com`, कभी `/apps.json` नहीं):

```
/downloadTokens/{token}.json   (PUT, full record)
  { token, jobId, appName,
    files:{apk, zip, video},   # Drive file ids
    used:false, createdAt: server-ts, runUrl }
/jobs/{jobId}.json             (PATCH, mirror; non-fatal)
  { status:"ready", token, portalUrl, updatedAt: server-ts }
```

Website `/d/{token}` request पर `used:true` mark करके one-time delivery करे।
Write के लिए service-account OAuth token (`firebase.database` scope + RTDB में
project permission) लगेगा — unauthenticated `POST /apps.json` अब use नहीं होता।

## 5. Drive privacy

- Upload: multipart `drive.file` scope, file default (PRIVATE) रहती है — कोई
  `permissions.create` public call नहीं, कोई `uc?id=` link नहीं।
- Website delivery: portal server-side Drive से fetch करके stream करे, या expiring
  signed redirect — implementation portal की तरफ; builder सिर्फ file id भेजता है।

## 6. Secret/Variable NAME mapping (owner decisions, 2026-11-08)

**Confirmed present** (owner ने confirm किया, workflow इन्हीं पर rely करता है):

| Name | Kind | Scope / requirement |
|------|------|---------------------|
| `GCP_CREDENTIALS` | secret | SA JSON; scopes `drive.file`, `firebase.database`, `userinfo.email`; SA को Drive folder पर **Editor**, GCP project पर **roles/firebasedatabase.admin** |
| `GDRIVE_FOLDER_ID` | secret | private folder id string |
| `FIREBASE_DB_URL` | secret | RTDB **origin only**, e.g. `https://<db>.firebaseio.com` |

**Reuse-only policy** (owner: "मौजूदा names से ही continue") — कोई नया name create करना
अनिवार्य नहीं। Workflow इन candidates को **secret-first, फिर variable** क्रम में resolve
करता है; सब missing होने पर वो feature degrade होता है (fatal नहीं), summary में flag होता है:

| Feature | Candidate names (order) | Missing होने पर |
|---------|------------------------|-----------------|
| Portal base (one-time link URL) | `SAIF_DOWNLOAD_PORTAL_URL` | `portalUrl` खाली; Drive ids फिर भी RTDB में sync |
| API base → default callback | `SAIF_CLOUD_API_BASE_URL` | callback सिर्फ dispatch input `callback_url` से |
| RTDB alt (with `FIREBASE_DB_URL`) | `SAIF_FIREBASE_DATABASE_URL` | n/a (primary confirmed) |
| Callback HMAC key | `SAIF_GATEWAY_CALLBACK_TOKEN`, `SAIF_CALLBACK_TOKEN`, `SAIF_GATEWAY_TOKEN` | callback **UNSIGNED** जाता है + warning; बाद में कोई-एक name add करते ही signed |

> NOTE: जिस भी name को secret की जगह variable बनाया गया है, workflow दोनों context में
> देखता है — value कभी log नहीं होती। Names लिस्ट करने का GitHub API access इस sandbox
> में 403 है, इसलिए ऊपर की तालिका owner-confirmation पर आधारित है।

## 7. Test plan (claim करने से पहले)

| Level | क्या | पास-criteria |
|-------|------|-------------|
| L0 | Manual dispatch (secrets missing) | Run fail हो पर error message exact missing NAME बताए |
| L1 | Manual dispatch `prompt=Test` (Drive + RTDB configured) | Drive में 3 private files; RTDB `/downloadTokens/<t>` मौजूद; portal_url summary में |
| L2 | `job_id` + `callback_url` fake HTTPS endpoint (e.g. webhook.site) | Signed callback पहुँचे, HMAC verify हो |
| L3 | Portal से one-time link GET | पहली बार file stream, दूसरी बार 410/404 |
| L4 | App → gateway end-to-end | App में build request → portal link app में दिखे |

## 8. सुरक्षा नियम (unchanged, enforced)

- कभी भी logs में secrets नहीं (`::add-mask::` one-time token पर भी)।
- APK/ZIP में कोई credential/endpoint token embed नहीं — build placeholder सिर्फ
  prompt text लिखता है।
- Secrets के साथ third-party action नहीं: पुराना `adityak74/google-drive-upload-action`
  **404 (repo deleted)** था — इसलिए upload अब plain `curl` से Google API पर, संभाला
  हुआ `google-github-actions/auth@v2.1.13` के साथ।
- Workflow `permissions: contents: read` (कम-से-कम अधिकार)।
