/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.cloud

import android.content.Context
import android.net.Uri
import com.example.data.local.AuthManager
import com.example.gemini.BuildConfig
import com.google.android.gms.tasks.Task
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okio.BufferedSink
import java.security.MessageDigest
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Cloud job state. Only the Firebase-authenticated owner may read /apps/<firebaseUid>/<jobId>. */
data class CloudAsset(val assetId: String, val uri: Uri, val mimeType: String, val path: String? = null)

data class CloudBuildState(
    val jobId: String,
    val kind: CloudRequestFilter.Route,
    val status: String,
    val message: String,
    val downloadUrl: String? = null
)

/**
 * No PAT, Google Drive credential, or Base64 project upload exists on the phone. The backend
 * validates a Firebase ID token and App Check, dispatches GitHub, and writes the owner's RTDB
 * job node. The cloud URLs are public build configuration, never private bearer secrets.
 */
object SaifAiCloudManager {
    private const val PREFS = "saif_cloud_builds"
    private const val MAX_PROMPT_CHARS = 4000
    private val claimPattern = Regex("[A-Za-z0-9_-]{32,128}")
    private val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false)
        .readTimeout(25, TimeUnit.SECONDS).writeTimeout(2, TimeUnit.MINUTES).build()
    private val largeUploadClient = client.newBuilder().writeTimeout(30, TimeUnit.MINUTES)
        .readTimeout(30, TimeUnit.MINUTES).build()

    private val _current = MutableStateFlow<CloudBuildState?>(null)
    val current: StateFlow<CloudBuildState?> = _current.asStateFlow()
    private var watching: Pair<DatabaseReference, ValueEventListener>? = null
    private var boundAppUid: String? = null
    private var boundFirebaseUid: String? = null

    private fun isHttpsEndpoint(value: String): Boolean = runCatching {
        val url = Uri.parse(value)
        url.scheme.equals("https", true) && !url.host.isNullOrBlank() &&
            (url.path.isNullOrBlank() || url.path == "/") && url.query == null && url.fragment == null &&
            url.encodedAuthority?.contains('@') != true
    }.getOrDefault(false)

    val isConfigured: Boolean get() = isHttpsEndpoint(BuildConfig.SAIF_CLOUD_API_BASE_URL) &&
        !BuildConfig.SAIF_CLOUD_API_BASE_URL.trimEnd('/').equals("https://saifai-downloads.ai.studio", ignoreCase = true) &&
        isHttpsEndpoint(BuildConfig.SAIF_DOWNLOAD_PORTAL_URL) &&
        isHttpsEndpoint(BuildConfig.SAIF_FIREBASE_DATABASE_URL)

    fun dismiss() {
        stopWatching()
        _current.value = null
    }

    private fun configured() {
        check(isConfigured) {
            "Cloud builds need a deployed backend, portal and Firebase RTDB. Set SAIF_CLOUD_API_BASE_URL, SAIF_DOWNLOAD_PORTAL_URL and SAIF_FIREBASE_DATABASE_URL in Actions variables, then rebuild."
        }
    }

    private suspend fun <T> Task<T>.awaitCloud(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { if (cont.isActive) cont.resume(it) }
        addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        addOnCanceledListener { if (cont.isActive) cont.cancel() }
    }

    private suspend fun firebaseUser(appUid: String): FirebaseUser = withTimeout(20_000L) {
        val app = AuthManager.currentUser.value
        check(app?.uid == appUid && app.isActive && !app.isBanned) { "SAIF AI account changed" }
        val auth = FirebaseAuth.getInstance()
        val user = if (app.isFirebaseUser) {
            auth.currentUser?.takeIf { it.uid == app.uid }
                ?: error("Firebase session expired. Sign in to SAIF AI again.")
        } else {
            if (auth.currentUser?.isAnonymous == false) auth.signOut()
            auth.currentUser ?: auth.signInAnonymously().awaitCloud().user
                ?: error("Enable Firebase Anonymous Authentication for guest/local cloud builds.")
        }
        check(AuthManager.currentUser.value?.uid == appUid) { "SAIF AI account changed" }
        user
    }

    private suspend fun authHeaders(appUid: String): Triple<String, String?, String> {
        val fb = firebaseUser(appUid)
        val token = withTimeout(20_000L) { fb.getIdToken(false).awaitCloud().token }
            ?.takeIf { it.isNotBlank() } ?: error("Firebase could not authorize this cloud request.")
        val appCheck = runCatching { withTimeout(12_000L) {
            FirebaseAppCheck.getInstance().getAppCheckToken(false).awaitCloud().token
        } }.getOrNull()?.takeIf { it.isNotBlank() }
        return Triple(token, appCheck, fb.uid)
    }

    private suspend fun post(path: String, body: JSONObject, appUid: String,
        longRequest: Boolean = false): JSONObject {
        val (token, appCheck, _) = authHeaders(appUid)
        val request = Request.Builder().url(BuildConfig.SAIF_CLOUD_API_BASE_URL.trimEnd('/') + path)
            .header("Authorization", "Bearer $token")
            .apply { if (appCheck != null) header("X-Firebase-AppCheck", appCheck) }
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return withContext(Dispatchers.IO) {
            (if (longRequest) largeUploadClient else client).newCall(request).execute().use { response ->
                if (response.code in 300..399) throw IOException(
                    "Website redirected to a sign-in page. A public HTTPS API gateway with Firebase/App Check verification must serve /api/*; Google login redirects cannot upload Android files."
                )
                val text = response.body?.string()?.take(4096).orEmpty()
                if (!response.isSuccessful) {
                    val reason = runCatching { JSONObject(text).optString("error") }.getOrDefault("")
                    throw IOException(reason.ifBlank { "Cloud request failed (HTTP ${response.code})" })
                }
                if (response.header("Content-Type")?.contains("application/json", true) != true) {
                    throw IOException("Website returned a webpage instead of SAIF AI Cloud API JSON. Deploy /api/* routes on the configured backend.")
                }
                runCatching { JSONObject(text) }.getOrElse {
                    throw IOException("Website returned invalid cloud API JSON", it)
                }
            }
        }
    }

    private suspend fun describeAsset(context: Context, asset: CloudAsset,
        limitBytes: Long = 100L * 1024 * 1024): JSONObject = withContext(Dispatchers.IO) {
        require(runCatching { UUID.fromString(asset.assetId) }.isSuccess) { "Invalid asset ID" }
        require(asset.mimeType.matches(Regex("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+"))) { "Invalid asset type" }
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        context.contentResolver.openInputStream(asset.uri)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                size += n
                require(size <= limitBytes) { "File exceeds ${limitBytes / (1024 * 1024)} MB. Choose a smaller file." }
                digest.update(buffer, 0, n)
            }
        } ?: error("Could not open the selected project asset")
        require(size > 0) { "An asset is empty" }
        JSONObject().put("assetId", asset.assetId).put("mimeType", asset.mimeType)
            .put("size", size).put("sha256", digest.digest().joinToString("") { "%02x".format(it) })
            .apply { asset.path?.let { put("path", it) } }
    }

    private suspend fun uploadAsset(context: Context, asset: CloudAsset, description: JSONObject,
        url: String, largeFile: Boolean = false) {
        val target = Uri.parse(url)
        require(target.scheme == "https" && target.host == "storage.googleapis.com") { "Invalid private staging URL" }
        val bytes = description.getLong("size")
        val body = object : RequestBody() {
            override fun contentType() = asset.mimeType.toMediaType()
            override fun contentLength() = bytes
            override fun writeTo(sink: BufferedSink) {
                var written = 0L
                context.contentResolver.openInputStream(asset.uri)?.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        written += count
                        require(written <= bytes) { "Selected asset changed during upload" }
                        sink.write(buffer, 0, count)
                    }
                } ?: error("Could not read the selected asset")
                require(written == bytes) { "Selected asset changed during upload" }
            }
        }
        withContext(Dispatchers.IO) {
            (if (largeFile) largeUploadClient else client)
                .newCall(Request.Builder().url(url).put(body).build()).execute().use { res ->
                if (!res.isSuccessful) throw IOException("Private asset upload failed (HTTP ${res.code})")
            }
        }
    }

    /** Stage optional binary assets as signed, private GCS PUTs. Never Base64-encode build inputs. */
    suspend fun requestBuild(
        context: Context, prompt: String, kind: CloudRequestFilter.Route,
        project: JSONObject? = null, assets: List<CloudAsset> = emptyList()
    ): CloudBuildState {
        require(kind in setOf(CloudRequestFilter.Route.APK, CloudRequestFilter.Route.VIDEO, CloudRequestFilter.Route.ZIP)) {
            "Only APK, video and ZIP generation requests use Cloud Build"
        }
        require(prompt.isNotBlank() && prompt.length <= MAX_PROMPT_CHARS) { "Describe the build in 1–4000 characters" }
        require(assets.size <= 20) { "Too many project assets (max 20)" }
        configured()
        val appUid = AuthManager.currentUser.value?.uid ?: error("Sign in to SAIF AI first")
        val (_, _, firebaseUid) = authHeaders(appUid)
        val jobId = UUID.randomUUID().toString()
        synchronized(this) {
            check(AuthManager.currentUser.value?.uid == appUid) { "SAIF AI account changed" }
            check(_current.value?.status !in listOf("queued", "uploading", "building")) { "A cloud build is already running" }
            stopWatching()
            boundAppUid = appUid
            boundFirebaseUid = firebaseUid
            _current.value = CloudBuildState(jobId, kind, "queued", "Preparing cloud build…")
        }
        try {
            val assetInfo = org.json.JSONArray()
            var totalAssetBytes = 0L
            for (asset in assets) {
                val description = describeAsset(context, asset)
                totalAssetBytes += description.getLong("size")
                require(totalAssetBytes <= 300L * 1024 * 1024) { "Project assets exceed 300 MB" }
                assetInfo.put(description)
            }
            val payload = JSONObject().put("jobId", jobId).put("kind", kind.name.lowercase())
                .put("prompt", prompt).put("assets", assetInfo)
            if (project != null) payload.put("project", project)
            val result = post(if (assets.isEmpty()) "/api/requestBuild" else "/api/createDraftJob", payload, appUid)
            check(result.optString("jobId") == jobId && result.optString("ownerUid") == firebaseUid) {
                "Unexpected cloud job acknowledgement"
            }
            if (assets.isNotEmpty()) {
                _current.value = CloudBuildState(jobId, kind, "uploading", "Securely uploading project assets…")
                val urls = result.getJSONArray("uploadUrls")
                require(urls.length() == assets.size) { "Cloud did not accept all assets" }
                for (i in assets.indices) {
                    check(AuthManager.currentUser.value?.uid == appUid) { "SAIF AI account changed" }
                    val upload = urls.getJSONObject(i)
                    require(upload.getString("assetId") == assets[i].assetId) { "Asset order changed" }
                    uploadAsset(context, assets[i], assetInfo.getJSONObject(i), upload.getString("url"))
                    _current.value = CloudBuildState(jobId, kind, "uploading", "Uploaded ${i + 1} of ${assets.size} assets…")
                }
                val started = post("/api/dispatchJob", JSONObject().put("jobId", jobId), appUid)
                check(started.optString("jobId") == jobId && started.optString("ownerUid") == firebaseUid) {
                    "Could not dispatch cloud build"
                }
            }
            check(AuthManager.currentUser.value?.uid == appUid) { "SAIF AI account changed" }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("$appUid:lastJob", "$firebaseUid|$jobId|${kind.name}").apply()
            watch(appUid, firebaseUid, jobId, kind)
            return _current.value!!
        } catch (e: Exception) {
            if (boundAppUid == appUid) _current.value = CloudBuildState(jobId, kind, "failed", e.message ?: "Cloud build could not start")
            throw e
        }
    }

    /**
     * Send one APK/ZIP/video (or a supported >=3 MB file) to the private website gateway.
     * The manifest and Firebase tokens reach only our configured backend; bytes stream directly
     * to a short-lived GCS signed PUT. Never read a full video into a Base64 JSON string.
     */
    suspend fun pushFile(context: Context, uri: Uri, selection: PrivateFileTypes.Selection): CloudBuildState {
        require(selection.delivery == PrivateFileTypes.Delivery.WEBSITE) { "Small text and photos stay in chat" }
        if (!isConfigured) {
            _current.value = CloudBuildState(UUID.randomUUID().toString(), CloudRequestFilter.Route.UPLOAD,
                "failed", "Private upload is not connected: deploy /api/push and /api/push/complete on the SAIF AI website, set the three public cloud URLs, then rebuild the app. Your file remains attached.")
        }
        configured()
        val appUid = AuthManager.currentUser.value?.uid ?: error("Sign in to SAIF AI first")
        val (_, _, firebaseUid) = authHeaders(appUid)
        val jobId = UUID.randomUUID().toString()
        val asset = CloudAsset(UUID.randomUUID().toString(), uri, selection.mimeType,
            PrivateFileTypes.safeUploadName(selection.name))
        synchronized(this) {
            check(AuthManager.currentUser.value?.uid == appUid) { "SAIF AI account changed" }
            check(_current.value?.status !in listOf("queued", "uploading", "building")) {
                "Another private upload or cloud build is running"
            }
            stopWatching()
            boundAppUid = appUid
            boundFirebaseUid = firebaseUid
            _current.value = CloudBuildState(jobId, CloudRequestFilter.Route.UPLOAD,
                "uploading", "Checking ${asset.path} before private upload…")
        }
        try {
            val details = describeAsset(context, asset, PrivateFileTypes.UPLOAD_LIMIT)
            val manifest = JSONObject().put("jobId", jobId).put("securityMode", "onetime")
                .put("burnOnDownload", true)
                .put("files", org.json.JSONArray().put(JSONObject().put("assetId", asset.assetId)
                    .put("name", asset.path).put("mimeType", asset.mimeType)
                    .put("size", details.getLong("size")).put("sha256", details.getString("sha256"))))
            val started = post("/api/push", manifest, appUid)
            check(started.optString("jobId") == jobId && started.optString("ownerUid") == firebaseUid) {
                "Website did not acknowledge this account's private file"
            }
            val targets = started.getJSONArray("uploadUrls")
            require(targets.length() == 1 && targets.getJSONObject(0).optString("assetId") == asset.assetId) {
                "Website did not return a private upload target"
            }
            _current.value = CloudBuildState(jobId, CloudRequestFilter.Route.UPLOAD,
                "uploading", "Uploading ${asset.path} privately to SAIF AI…")
            uploadAsset(context, asset, details, targets.getJSONObject(0).getString("url"), largeFile = true)
            check(AuthManager.currentUser.value?.uid == appUid) { "SAIF AI account changed during upload" }
            _current.value = CloudBuildState(jobId, CloudRequestFilter.Route.UPLOAD,
                "building", "Verifying and saving ${asset.path} privately…")
            val completed = post("/api/push/complete", JSONObject().put("jobId", jobId), appUid,
                longRequest = true)
            check(completed.optString("jobId") == jobId && completed.optString("ownerUid") == firebaseUid &&
                completed.optString("status") == "ready") { "Website did not confirm the private file" }
            val claim = completed.optString("downloadClaim").takeIf { claimPattern.matches(it) }
                ?: error("Website did not provide a private download claim")
            check(AuthManager.currentUser.value?.uid == appUid) { "SAIF AI account changed during upload" }
            val link = Uri.parse(BuildConfig.SAIF_DOWNLOAD_PORTAL_URL.trimEnd('/') + "/download/$jobId")
                .buildUpon().fragment("claim=$claim").build().toString()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("$appUid:lastJob", "$firebaseUid|$jobId|UPLOAD").apply()
            watch(appUid, firebaseUid, jobId, CloudRequestFilter.Route.UPLOAD)
            return CloudBuildState(jobId, CloudRequestFilter.Route.UPLOAD, "ready",
                "${asset.path} is on SAIF AI DOWNLOADS", link).also { _current.value = it }
        } catch (e: Exception) {
            if (boundAppUid == appUid) _current.value = CloudBuildState(jobId, CloudRequestFilter.Route.UPLOAD,
                "failed", e.message ?: "Private file upload failed")
            throw e
        }
    }

    /** Reattach to the last submitted job after an app restart, without submitting it again. */
    suspend fun restoreLastJob(context: Context) {
        if (!isConfigured || watching != null) return
        val appUid = AuthManager.currentUser.value?.uid ?: return
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("$appUid:lastJob", null)?.split('|') ?: return
        if (saved.size != 3) return
        val kind = runCatching { CloudRequestFilter.Route.valueOf(saved[2]) }.getOrNull() ?: return
        val jobId = saved[1].takeIf { runCatching { UUID.fromString(it) }.isSuccess } ?: return
        val firebaseUid = runCatching { firebaseUser(appUid).uid }.getOrNull() ?: return
        if (saved[0] != firebaseUid || kind == CloudRequestFilter.Route.IN_APP) return
        boundAppUid = appUid
        boundFirebaseUid = firebaseUid
        watch(appUid, firebaseUid, jobId, kind)
    }

    private fun watch(appUid: String, firebaseUid: String, jobId: String, kind: CloudRequestFilter.Route) {
        val ref = FirebaseDatabase.getInstance(BuildConfig.SAIF_FIREBASE_DATABASE_URL)
            .getReference("apps").child(firebaseUid).child(jobId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (boundAppUid != appUid || boundFirebaseUid != firebaseUid ||
                    AuthManager.currentUser.value?.uid != appUid) return
                val status = snapshot.child("status").getValue(String::class.java).orEmpty()
                    .lowercase().ifBlank { "queued" }
                val claim = snapshot.child("downloadClaim").getValue(String::class.java)
                    ?.takeIf { claimPattern.matches(it) }
                val expires = snapshot.child("claimExpiresAt").getValue(Long::class.java) ?: 0L
                val link = if (status == "ready" && claim != null && expires > System.currentTimeMillis()) {
                    Uri.parse(BuildConfig.SAIF_DOWNLOAD_PORTAL_URL.trimEnd('/') + "/download/$jobId")
                        .buildUpon().fragment("claim=$claim").build().toString()
                } else null
                _current.value = CloudBuildState(jobId, kind, status,
                    when (status) {
                        "ready" -> if (link != null) {
                            if (kind == CloudRequestFilter.Route.UPLOAD) "Your private file is ready" else "Your build is ready"
                        } else "Download link expired — refresh it in the app"
                        "failed" -> snapshot.child("error").getValue(String::class.java)?.take(250) ?: "Cloud operation failed"
                        "building" -> if (kind == CloudRequestFilter.Route.UPLOAD) "Saving your private file…" else "Building in Cloud…"
                        "uploading" -> "Uploading your private file…"
                        else -> "Queued in Cloud…"
                    }, link)
            }

            override fun onCancelled(error: DatabaseError) {
                if (boundAppUid == appUid && AuthManager.currentUser.value?.uid == appUid) {
                    _current.value = CloudBuildState(jobId, kind, "failed", "Could not read your private build status: ${error.message}")
                }
            }
        }
        synchronized(this) { stopWatching(); watching = ref to listener }
        ref.addValueEventListener(listener)
    }

    suspend fun refreshDownloadLink(): CloudBuildState {
        configured()
        val state = _current.value ?: error("No cloud job to refresh")
        check(state.status == "ready") { "The cloud build is not ready" }
        val uid = boundAppUid ?: error("Sign in to the same app account")
        val response = post("/api/issueDownloadLink", JSONObject().put("jobId", state.jobId), uid)
        check(response.optString("jobId") == state.jobId) { "Private build link does not match this job" }
        val claim = response.optString("downloadClaim").takeIf { claimPattern.matches(it) }
            ?: error("Invalid download claim")
        val url = Uri.parse(BuildConfig.SAIF_DOWNLOAD_PORTAL_URL.trimEnd('/') + "/download/${state.jobId}")
            .buildUpon().fragment("claim=$claim").build().toString()
        return state.copy(downloadUrl = url, message =
            if (state.kind == CloudRequestFilter.Route.UPLOAD) "Your private file is ready" else "Your build is ready")
            .also { if (AuthManager.currentUser.value?.uid == uid) _current.value = it }
    }

    @Synchronized private fun stopWatching() {
        watching?.let { (ref, listener) -> ref.removeEventListener(listener) }
        watching = null
    }

    @Synchronized fun onSignedOut() {
        stopWatching()
        boundAppUid = null
        boundFirebaseUid = null
        _current.value = null
    }
}
