# SaifAI Builder 🤖📱

Prompt likho → **GitHub Actions** build karta hai → APK ka link Firebase me aata hai →
app me **Download APK** button dikhta hai → browser (ad page) khulta hai → auto download.

Ye repo do hisson me hai:

| Path | Kya hai |
|---|---|
| `app/` | Android app (Kotlin, Material 3) — build trigger + status + download button |
| `.github/workflows/build.yml` | Builder: APK banata hai, Drive par upload karta hai, link Firebase me likhta hai |
| `web/download.html` | Ad-gated download landing page (host karke `AD_GATE_URL` me daalo) |

---

## 1. Flow kaise chalta hai

```
[App] --POST /actions/.../dispatches--> [GitHub Actions]
                                              |
                                              | build + upload to Drive
                                              v
[App] <--GET /apps/<key>.json---------- [Firebase RTDB]
  |
  | "Download APK" tap
  v
[Browser] -> download.html?url=...&name=...  (ad + countdown)
  |
  v
Auto download ✅
```

Aapka original snippet isi ka last part tha — ab poora wired hai:

```kotlin
// MainActivity.kt / BuildViewModel.kt (actual code)
val result = repository.buildAndPublish(appName) { step -> push(step) }
// build complete 🎉
_state.value = UiState.Ready(result.downloadUrl, result.appName, result.runUrl, log)
```

```kotlin
// MainActivity.render() -> UiState.Ready
downloadUrl = state.downloadUrl
binding.btnDownload.isVisible = true      // 👈 button visible
```

```kotlin
// MainActivity.openDownloadPage()
val browserUrl = AdGate.resolve(url, builtAppName, viewModel.adGateUrl)
startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(browserUrl)))  // ad → auto download
```

---

## 2. Firebase + secrets setup (ek baar)

1. **Firebase Realtime Database** banao → URL milega jaise
   `https://saifai-builder-default-rtdb.firebaseio.com`
2. Rules (public read, sirf workflow likhega — write band rakho):
   ```json
   { "rules": { "apps": { ".read": true, ".write": false } } }
   ```
   Likhne ke liye `FIREBASE_DB_URL` secret me DB secret lagao (ya rules me
   auth-based write allow karo).
3. GitHub repo → **Settings → Secrets and variables → Actions** me ye 3 secrets:
   - `GCP_CREDENTIALS` — Google service account JSON (Drive API enabled)
   - `GDRIVE_FOLDER_ID` — Drive folder ka id
   - `FIREBASE_DB_URL` — upar wala DB URL

Workflow record yahan likhta hai: `PUT /apps/<app_key>.json`

```json
{
  "appName": "Saif_AI_Test_App",
  "appKey": "saif_ai_test_app",
  "driveFileId": "1AbC...",
  "downloadUrl": "https://drive.google.com/uc?export=download&id=1AbC...",
  "runUrl": "https://github.com/.../actions/runs/123",
  "status": "Ready to Download",
  "createdAt": "2026-11-08T10:00:00Z"
}
```

`app_key` = lowercase + har non-alphanumeric → `_` (Kotlin `FirebaseClient.appKeyFor()`
aur workflow ka `sed` — dono same).

---

## 3. App config

`gradle.properties` me values bharo (ya `local.properties` me same keys — wo override karta hai):

```properties
GITHUB_OWNER=shahudgg
GITHUB_REPO=SaifAI-Builder
GITHUB_WORKFLOW=build.yml
GITHUB_BRANCH=main
FIREBASE_DB_URL=https://saifai-builder-default-rtdb.firebaseio.com
AD_GATE_URL=            # blank = seedha drive link khulega
SAIF_UPLOAD_URL=        # optional apna upload server
SAIF_API_KEY=
```

In-app: **⋮ → Settings** me GitHub PAT + Firebase URL + ad page URL (runtime override).

**GitHub token** (fine-grained PAT):
- Repository access: sirf `SaifAI-Builder`
- Permissions: **Actions: Read and write**, **Contents: Read**

Token sirf device ke private SharedPreferences me rehta hai, kahin bheja nahi jata.

---

## 4. Ad-gated download page (Ad chalu karne ke liye)

`web/download.html` ko host karo (Firebase Hosting / GitHub Pages / Netlify), fir:

```properties
AD_GATE_URL=https://your-project.web.app/download.html
```

Page `?url=<apk-link>&name=<app>` leta hai, ad slot dikhata hai, 5s countdown ke
baad download start karta hai. AdSense approve hone par HTML ke `<head>` wala
script aur `AD SLOT` block uncomment karo.

> AdSense approval se pehle page bhi kaam karta hai (sirf placeholder dikhega).

---

## 5. Build karna

```bash
# Android Studio me open karo (recommended) — Gradle sync khud ho jayegi.
# CLI se pehli baar:
gradle wrapper --gradle-version 8.2     # gradlew/gradle-wrapper.jar generate karta hai
./gradlew assembleDebug
```

APK path: `app/build/outputs/apk/debug/app-debug.apk`

> `gradle/wrapper/gradle-wrapper.jar` binary is repo me nahi hai — ek baar
> `gradle wrapper` chala kar (ya Android Studio se) generate kar lo.

Requirements: JDK 17, compileSdk 34, minSdk 24.

---

## 6. `uploadToSaif()` (optional)

Agar aapka apna hosting server hai, `SAIF_UPLOAD_URL` set kar do:

```
POST <SAIF_UPLOAD_URL>       (multipart/form-data, field: file)
Header: Authorization: Bearer <SAIF_API_KEY>   (optional)
Response: {"url": "https://cdn.../app.apk"}
```

Tab flow hoga: Drive se APK download → Saif par upload → **wahi URL** download
button me use hoga (`SaifUploader.uploadToSaif()`), warna Drive link.

---

## 7. Files

```
app/src/main/java/com/saif/ai/builder/
├── MainActivity.kt              # UI + download button (snippet wala behaviour)
├── AppConfig.kt                 # BuildConfig se config
├── ui/BuildViewModel.kt         # state machine: Idle → Running → Ready/Failed
├── ui/UiState.kt
├── data/BuildRepository.kt      # dispatch → poll → Firebase → publish
├── data/SettingsStore.kt        # token + URL overrides
├── data/AdGate.kt               # ad page URL builder + browser open
└── net/                         # Http, GitHubClient, FirebaseClient, SaifUploader
```

## 8. Troubleshooting

| Problem | Fix |
|---|---|
| `HTTP 401` dispatch par | PAT galat/expired |
| `HTTP 403` | Token me **Actions: write** nahi hai |
| `HTTP 404` | `GITHUB_OWNER/REPO/WORKFLOW` ya branch galat |
| `Firebase me apps/<key> par link nahi mila` | Workflow ka "Save Link to Firebase" step + `FIREBASE_DB_URL` secret check karo |
| Timeout (30 min) | Build bahut lamba — `AppConfig.BUILD_TIMEOUT_MS` badhao |
| Drive link HTML de raha | Bada APK par confirm page — `Http.downloadFile()` handle karta hai, direct browser download bhi chalega |
