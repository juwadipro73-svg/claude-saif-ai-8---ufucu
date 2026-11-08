package com.saif.ai.builder

/**
 * Build-time config (gradle.properties / local.properties se aata hai).
 */
object AppConfig {

    val githubOwner: String = BuildConfig.GITHUB_OWNER
    val githubRepo: String = BuildConfig.GITHUB_REPO
    val githubWorkflow: String = BuildConfig.GITHUB_WORKFLOW
    val githubBranch: String = BuildConfig.GITHUB_BRANCH

    /** Fallback if runtime override (Settings dialog) nahi diya gaya. */
    val firebaseDbUrlDefault: String = BuildConfig.FIREBASE_DB_URL
    val adGateUrlDefault: String = BuildConfig.AD_GATE_URL

    val saifUploadUrl: String = BuildConfig.SAIF_UPLOAD_URL
    val saifApiKey: String = BuildConfig.SAIF_API_KEY

    /** Kitne gap par build status check karein. */
    const val POLL_INTERVAL_MS = 8_000L

    /** Build ka total timeout (30 min). */
    const val BUILD_TIMEOUT_MS = 30 * 60 * 1000L

    /** Link publish hone ke baad Firebase retry (6 x 5s = 30s). */
    const val FIREBASE_RETRIES = 6
    const val FIREBASE_RETRY_DELAY_MS = 5_000L

    val repoSlug: String get() = "$githubOwner/$githubRepo"
}
