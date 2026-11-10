/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AuthUser(
    val email: String,
    val name: String,
    val uid: String = "",
    val role: String = "user",
    val isActive: Boolean = true,
    val isBanned: Boolean = false,
    val banReason: String = "",
    val adminApiUsageCount: Int = 0,
    val isFirebaseUser: Boolean = false,
    val loggedInAt: Long = System.currentTimeMillis(),
    val profilePicturePath: String? = null
) {
    val isAdmin: Boolean get() = role.equals("admin", ignoreCase = true)
    val initialLetter: String get() = (name.ifBlank { email }).trim().take(1).uppercase().ifBlank { "U" }
}

object AuthManager {
    private const val TAG = "AuthManager"
    private const val PREFS_NAME = "SaifAiAuthPrefs"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_ROLE = "user_role"
    private const val KEY_USER_UID = "user_uid"
    private const val KEY_USER_IS_FIREBASE = "user_is_firebase"
    private const val KEY_FIREBASE_EMAIL_PREFIX = "firebase_email_"
    private fun firebaseEmailKey(email: String) = KEY_FIREBASE_EMAIL_PREFIX + LocalIdentity.uidForEmail(email)

    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var db: SaifDatabase
    private var userDocListener: ListenerRegistration? = null

    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    fun getUserAvatarFile(context: Context, email: String): java.io.File {
        val safeEmail = email.trim().lowercase().replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "default_user" }
        val dir = java.io.File(context.filesDir, "avatars").apply { mkdirs() }
        return java.io.File(dir, "avatar_$safeEmail.jpg")
    }

    fun saveUserAvatar(context: Context, email: String, bitmap: android.graphics.Bitmap): String {
        val file = getUserAvatarFile(context, email)
        try {
            java.io.FileOutputStream(file).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val path = file.absolutePath
        if (::prefs.isInitialized) {
            prefs.edit().putString("avatar_path_${email.trim().lowercase()}", path).apply()
        }
        val cur = _currentUser.value
        if (cur != null && cur.email.equals(email, ignoreCase = true)) {
            _currentUser.value = cur.copy(profilePicturePath = path)
        }
        return path
    }

    fun isFirebaseAvailable(): Boolean {
        return try {
            if (::appContext.isInitialized && FirebaseApp.getApps(appContext).isEmpty()) {
                FirebaseApp.initializeApp(appContext)
            }
            ::appContext.isInitialized && FirebaseApp.getApps(appContext).isNotEmpty() && FirebaseAuth.getInstance() != null
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { res ->
            if (cont.isActive) cont.resume(res)
        }
        addOnFailureListener { e ->
            if (cont.isActive) cont.resumeWithException(e)
        }
        addOnCanceledListener {
            if (cont.isActive) cont.cancel()
        }
    }

    fun init(context: Context) {
        if (_isInitialized.value) return
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        db = SaifDatabase.getDatabase(appContext)

        // Restore the original email/password/guest startup flow. A Firebase account is
        // restored only when its actual Firebase session matches the saved app identity;
        // anonymous cloud-build credentials must never become an app login.
        val loggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        val email = prefs.getString(KEY_USER_EMAIL, "").orEmpty()
        val name = prefs.getString(KEY_USER_NAME, "").orEmpty()
        val savedUid = prefs.getString(KEY_USER_UID, "").orEmpty()
        val savedFirebase = prefs.getBoolean(KEY_USER_IS_FIREBASE, false)
        val fbUser = try { if (isFirebaseAvailable()) FirebaseAuth.getInstance().currentUser else null }
            catch (_: Exception) { null }
        val verifiedFirebase = loggedIn && email.isNotBlank() && fbUser != null &&
            fbUser.email?.equals(email, true) == true && (savedUid.isBlank() || savedUid == fbUser.uid) &&
            (savedFirebase || savedUid.isBlank())
        if (verifiedFirebase && fbUser != null) {
            val avatar = getUserAvatarFile(appContext, email)
            _currentUser.value = AuthUser(
                email = email, name = fbUser.displayName ?: name.ifBlank { email.substringBefore('@') },
                uid = fbUser.uid, role = "user", isFirebaseUser = true,
                profilePicturePath = if (avatar.exists()) avatar.absolutePath else null
            )
            prefs.edit().putBoolean(firebaseEmailKey(email), true).apply()
            attachRealtimeUserListener(fbUser.uid, email)
            CoroutineScope(Dispatchers.IO).launch {
                val cloud = runCatching { fetchCloudUserData(fbUser.uid, email) }.getOrNull()
                if (cloud != null) updateUserFromSnapshot(cloud, fbUser.uid, email)
            }
        } else if (loggedIn && email.isNotBlank() && !savedFirebase) {
            val owner = if (LocalIdentity.isGuest(email)) savedUid.takeIf { it.startsWith("guest:") }
                ?: "guest:${java.util.UUID.randomUUID()}" else LocalIdentity.uidForEmail(email)
            val avatar = getUserAvatarFile(appContext, email)
            _currentUser.value = AuthUser(
                email = email, name = name.ifBlank { email.substringBefore('@') }, uid = owner,
                role = prefs.getString(KEY_USER_ROLE, "user").orEmpty(),
                profilePicturePath = if (avatar.exists()) avatar.absolutePath else null
            )
            prefs.edit().putString(KEY_USER_UID, owner).putBoolean(KEY_USER_IS_FIREBASE, false).apply()
        }
        _isInitialized.value = true
        com.example.util.SaifMemoryCore.bind(appContext, _currentUser.value?.uid)
        com.example.gemini.data.remote.SavedAiSkills.bind(appContext, _currentUser.value?.uid)
    }

    fun attachRealtimeUserListener(uid: String, email: String) {
        if (!isFirebaseAvailable()) return
        try {
            userDocListener?.remove()
            val firestore = FirebaseFirestore.getInstance()

            userDocListener = firestore.collection("users").document(uid)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) return@addSnapshotListener
                    if (snapshot != null && snapshot.exists()) {
                        updateUserFromSnapshot(snapshot.data ?: emptyMap(), uid, email)
                    } else {
                        // Check Users collection (capital U from user's console)
                        firestore.collection("Users").document(uid).get()
                            .addOnSuccessListener { capSnap ->
                                if (capSnap != null && capSnap.exists()) {
                                    updateUserFromSnapshot(capSnap.data ?: emptyMap(), uid, email)
                                }
                            }
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun extractRole(data: Map<String, Any?>): String {
        // Direct key check
        for ((key, value) in data) {
            val k = key.trim().lowercase()
            val vStr = value?.toString()?.trim()?.lowercase() ?: ""
            if (k in listOf("role", "user_role", "userrole", "type", "account_type") && 
                (vStr == "admin" || vStr == "administrator" || vStr == "superadmin")) {
                return "admin"
            }
            if (k in listOf("isadmin", "is_admin", "admin", "administrator") && 
                (value == true || vStr == "true" || vStr == "yes" || vStr == "admin" || vStr == "1")) {
                return "admin"
            }
        }
        return "user"
    }

    suspend fun fetchCloudUserData(uid: String, email: String): Map<String, Any?>? {
        if (!isFirebaseAvailable()) return null
        val firestore = FirebaseFirestore.getInstance()
        val cleanEmail = email.trim().lowercase()

        // 0. Dedicated admins/Admins collections
        try {
            val d = firestore.collection("admins").document(uid).get().awaitResult()
            if (d.exists() && d.data != null) return (d.data ?: emptyMap()) + ("role" to "admin")
        } catch (ignored: Exception) {}
        try {
            val d = firestore.collection("admins").document(cleanEmail).get().awaitResult()
            if (d.exists() && d.data != null) return (d.data ?: emptyMap()) + ("role" to "admin")
        } catch (ignored: Exception) {}
        try {
            val d = firestore.collection("Admins").document(uid).get().awaitResult()
            if (d.exists() && d.data != null) return (d.data ?: emptyMap()) + ("role" to "admin")
        } catch (ignored: Exception) {}
        try {
            val d = firestore.collection("Admins").document(cleanEmail).get().awaitResult()
            if (d.exists() && d.data != null) return (d.data ?: emptyMap()) + ("role" to "admin")
        } catch (ignored: Exception) {}

        // 1. users doc by uid
        try {
            val d = firestore.collection("users").document(uid).get().awaitResult()
            if (d.exists() && d.data != null) return d.data
        } catch (ignored: Exception) {}

        // 2. Users doc by uid
        try {
            val d = firestore.collection("Users").document(uid).get().awaitResult()
            if (d.exists() && d.data != null) return d.data
        } catch (ignored: Exception) {}

        // Email-keyed legacy documents are safe for role/ban lookup only after Firebase
        // has verified ownership of this address; a freshly entered email alone is not proof.
        val account = FirebaseAuth.getInstance().currentUser
        if (account?.uid != uid || account.email?.equals(cleanEmail, true) != true || !account.isEmailVerified) return null

        // 3. users doc by email
        try {
            val d = firestore.collection("users").document(cleanEmail).get().awaitResult()
            if (d.exists() && d.data != null) return d.data
        } catch (ignored: Exception) {}

        // 4. Users doc by email
        try {
            val d = firestore.collection("Users").document(cleanEmail).get().awaitResult()
            if (d.exists() && d.data != null) return d.data
        } catch (ignored: Exception) {}

        // 5. Query where email == cleanEmail
        try {
            val q = firestore.collection("users").whereEqualTo("email", cleanEmail).limit(1).get().awaitResult()
            if (!q.isEmpty) return q.documents[0].data
        } catch (ignored: Exception) {}

        // 6. Query Users where email == cleanEmail
        try {
            val q = firestore.collection("Users").whereEqualTo("email", cleanEmail).limit(1).get().awaitResult()
            if (!q.isEmpty) return q.documents[0].data
        } catch (ignored: Exception) {}

        // 7. Query where E-mail == cleanEmail
        try {
            val q = firestore.collection("users").whereEqualTo("E-mail", cleanEmail).limit(1).get().awaitResult()
            if (!q.isEmpty) return q.documents[0].data
        } catch (ignored: Exception) {}

        // 8. Query Users where E-mail == cleanEmail
        try {
            val q = firestore.collection("Users").whereEqualTo("E-mail", cleanEmail).limit(1).get().awaitResult()
            if (!q.isEmpty) return q.documents[0].data
        } catch (ignored: Exception) {}

        return null
    }

    private fun updateUserFromSnapshot(data: Map<String, Any?>, uid: String, email: String) {
        val role = extractRole(data)
        val isActive = when {
            data.containsKey("Is active") -> data["Is active"] as? Boolean ?: true
            data.containsKey("isActive") -> data["isActive"] as? Boolean ?: true
            data.containsKey("status") -> (data["status"] as? String)?.lowercase() != "suspended" && (data["status"] as? String)?.lowercase() != "banned"
            else -> true
        }
        val name = (data["name"] as? String) ?: (data["Name"] as? String) ?: (data["Username"] as? String) ?: ""
        val usage = (data["apiUsageCount"] as? Number)?.toInt() ?: 0

        val current = _currentUser.value
        // A late Firestore callback must never promote or ban a different local/Firebase user.
        if (current != null && current.isFirebaseUser && current.uid == uid && current.email.equals(email, true)) {
            val updated = current.copy(
                name = if (name.isNotBlank()) name else current.name,
                role = role,
                isActive = isActive,
                isBanned = !isActive,
                adminApiUsageCount = usage
            )
            _currentUser.value = updated
            prefs.edit().putString(KEY_USER_ROLE, role).apply()

            // Also keep Room DB in sync
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                try {
                    db.userAccountDao().updateUserRole(email, role)
                    db.userAccountDao().updateUserStatus(email, isActive, !isActive)
                } catch (ignored: Exception) {}
            }
        }
    }

    suspend fun signUp(name: String, email: String, password: String): Result<AuthUser> = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        val cleanEmail = email.trim().lowercase()
        val cleanPass = password.trim()

        if (cleanName.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter your name"))
        }
        if (cleanEmail.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a valid email address"))
        }
        if (cleanPass.length < 6) {
            return@withContext Result.failure(IllegalArgumentException("Password must be at least 6 characters"))
        }
        if (LocalIdentity.isGuest(cleanEmail)) {
            return@withContext Result.failure(IllegalArgumentException("This email is reserved for the Guest button"))
        }

        // Try Firebase Authentication First
        if (isFirebaseAvailable()) {
            try {
                val auth = FirebaseAuth.getInstance()
                val authResult = auth.createUserWithEmailAndPassword(cleanEmail, cleanPass).awaitResult()
                val fbUser = authResult.user

                if (fbUser != null) {
                    // Update Display Name in Firebase Auth Profile
                    try {
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(cleanName)
                            .build()
                        fbUser.updateProfile(profileUpdates).awaitResult()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // Save Profile to Cloud Firestore (both "users" and "Users")
                    try {
                        val firestore = FirebaseFirestore.getInstance()
                        val userDoc = mapOf(
                            "uid" to fbUser.uid,
                            "email" to cleanEmail,
                            "E-mail" to cleanEmail,
                            "name" to cleanName,
                            "Name" to cleanName,
                            "role" to "user",
                            "Role" to "user",
                            "isActive" to true,
                            "Is active" to true,
                            "apiUsageCount" to 0,
                            "createdAt" to System.currentTimeMillis()
                        )
                        firestore.collection("users").document(fbUser.uid).set(userDoc, SetOptions.merge()).awaitResult()
                        try {
                            firestore.collection("Users").document(fbUser.uid).set(userDoc, SetOptions.merge()).awaitResult()
                        } catch (ignored: Exception) {}
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // Do not mirror Firebase passwords into offline Room rows. A reset or ban
                    // must not silently fall back to an old device-local credential.
                    prefs.edit().putBoolean(firebaseEmailKey(cleanEmail), true).apply()

                    val user = AuthUser(
                        email = cleanEmail,
                        name = cleanName,
                        uid = fbUser.uid,
                        role = "user",
                        isActive = true,
                        isFirebaseUser = true
                    )
                    return@withContext Result.success(user)
                }
            } catch (e: Exception) {
                val msg = e.message ?: ""
                if (msg.contains("email address is already in use", ignoreCase = true) ||
                    msg.contains("email-already-in-use", ignoreCase = true)) {
                    return@withContext Result.failure(IllegalStateException("This email is already registered in Firebase. Please switch to Sign In or tap 'Forgot Password'."))
                }
                Log.w(TAG, "Firebase registration failed ($msg). Falling back to local offline account creation...")
            }
        }

        // Local Room Database Fallback (when Firebase offline or failed).
        if (prefs.getBoolean(firebaseEmailKey(cleanEmail), false)) {
            return@withContext Result.failure(IllegalStateException(
                "This email already belongs to a Firebase account on this device. Sign in online or reset its password."
            ))
        }
        try {
            val existing = db.userAccountDao().getUserByEmail(cleanEmail)
            if (existing != null) {
                return@withContext Result.failure(IllegalStateException("An account with this email already exists locally. Please switch to Sign In."))
            }

            val hash = PasswordHash.create(cleanPass)
            val entity = UserAccountEntity(
                email = cleanEmail,
                name = cleanName,
                passwordHash = hash
            )
            db.userAccountDao().insertUser(entity)

            val user = AuthUser(email = cleanEmail, name = cleanName, uid = LocalIdentity.uidForEmail(cleanEmail),
                role = "user", isFirebaseUser = false)
            Result.success(user)
        } catch (e: Exception) {
            if (e.message?.contains("UNIQUE", ignoreCase = true) == true || e.message?.contains("exists", ignoreCase = true) == true) {
                Result.failure(IllegalStateException("An account with this email already exists. Please switch to Sign In."))
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun signIn(email: String, password: String): Result<AuthUser> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        val cleanPass = password.trim()

        if (cleanEmail.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter your email address"))
        }
        if (cleanPass.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter your password"))
        }
        if (LocalIdentity.isGuest(cleanEmail)) {
            return@withContext Result.failure(IllegalArgumentException("Use the Guest button to continue without an account"))
        }

        var firebaseExceptionMsg: String? = null

        // 1. Try Firebase Authentication First
        if (isFirebaseAvailable()) {
            try {
                val auth = FirebaseAuth.getInstance()
                val authResult = auth.signInWithEmailAndPassword(cleanEmail, cleanPass).awaitResult()
                val fbUser = authResult.user

                if (fbUser != null) {
                    var resolvedName = fbUser.displayName ?: ""
                    var resolvedRole = "user"
                    var resolvedIsActive = true
                    var resolvedUsage = 0

                    try {
                        val cloudData = fetchCloudUserData(fbUser.uid, cleanEmail)
                        if (cloudData != null) {
                            resolvedRole = extractRole(cloudData)
                            resolvedIsActive = when {
                                cloudData.containsKey("Is active") -> cloudData["Is active"] as? Boolean ?: true
                                cloudData.containsKey("isActive") -> cloudData["isActive"] as? Boolean ?: true
                                cloudData.containsKey("status") -> (cloudData["status"] as? String)?.lowercase() != "suspended" && (cloudData["status"] as? String)?.lowercase() != "banned"
                                else -> true
                            }
                            val cloudName = (cloudData["name"] as? String) ?: (cloudData["Name"] as? String) ?: (cloudData["Username"] as? String) ?: ""
                            if (cloudName.isNotBlank()) resolvedName = cloudName
                            resolvedUsage = (cloudData["apiUsageCount"] as? Number)?.toInt() ?: 0
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    if (resolvedName.isBlank()) {
                        resolvedName = cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
                    }

                    prefs.edit().putBoolean(firebaseEmailKey(cleanEmail), true).apply()

                    val user = AuthUser(
                        email = cleanEmail,
                        name = resolvedName,
                        uid = fbUser.uid,
                        role = resolvedRole,
                        isActive = resolvedIsActive,
                        isBanned = !resolvedIsActive,
                        adminApiUsageCount = resolvedUsage,
                        isFirebaseUser = true
                    )
                    return@withContext Result.success(user)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Firebase signIn attempt failed: ${e.message}. Checking local fallback...")
                firebaseExceptionMsg = e.message ?: ""
            }
        }

        // 2. Device-local accounts only. Firebase-owned emails must not bypass remote
        // password changes, bans, or reset links with a stale local cached password.
        if (prefs.getBoolean(firebaseEmailKey(cleanEmail), false)) {
            return@withContext Result.failure(IllegalStateException(
                "This email uses Firebase sign-in. Connect to the internet and use your current password or Forgot Password."
            ))
        }
        try {
            val userEntity = db.userAccountDao().getUserByEmail(cleanEmail)
            if (userEntity != null) {
                if (PasswordHash.verify(cleanPass, userEntity.passwordHash)) {
                    if (PasswordHash.needsUpgrade(userEntity.passwordHash)) {
                        db.userAccountDao().updatePasswordHash(userEntity.email, PasswordHash.create(cleanPass))
                    }
                    val user = AuthUser(
                        email = userEntity.email,
                        name = userEntity.name,
                        uid = LocalIdentity.uidForEmail(userEntity.email),
                        role = userEntity.role,
                        isActive = userEntity.isActive,
                        isBanned = userEntity.isBanned,
                        adminApiUsageCount = userEntity.apiUsageCount,
                        isFirebaseUser = false
                    )
                    return@withContext Result.success(user)
                } else {
                    return@withContext Result.failure(
                        IllegalArgumentException("Incorrect password for $cleanEmail. Please verify your password or tap 'Forgot Password'.")
                    )
                }
            }
        } catch (localEx: Exception) {
            Log.w(TAG, "Local DB check error: ${localEx.message}")
        }

        // 3. User was not found in local DB or password didn't match. Provide clear, human-friendly error.
        val errMsg = firebaseExceptionMsg ?: ""
        val isUserNotFound = errMsg.contains("user-not-found", ignoreCase = true) ||
                errMsg.contains("no user record", ignoreCase = true)
        val isInvalidCreds = errMsg.contains("credential is incorrect", ignoreCase = true) ||
                errMsg.contains("invalid-credential", ignoreCase = true) ||
                errMsg.contains("wrong-password", ignoreCase = true) ||
                errMsg.contains("malformed", ignoreCase = true) ||
                errMsg.contains("expired", ignoreCase = true) ||
                errMsg.contains("RecaptchaAction", ignoreCase = true) ||
                errMsg.contains("RecaptchaCallWrapper", ignoreCase = true)
        val isNetworkErr = errMsg.contains("network", ignoreCase = true) ||
                errMsg.contains("timeout", ignoreCase = true) ||
                errMsg.contains("unreachable", ignoreCase = true)

        val finalError = when {
            isUserNotFound -> "No account found for $cleanEmail. Please tap 'Sign Up' to create your account or continue as guest."
            isInvalidCreds -> "Incorrect password for $cleanEmail. Please verify your password, tap 'Forgot Password', or continue as guest."
            isNetworkErr -> "Network connection error. Please verify your internet connection or continue as guest."
            errMsg.isNotBlank() -> "Sign in failed: ${errMsg.take(120)}. Please check your credentials or continue as guest."
            else -> "Incorrect email or password for $cleanEmail. Please try again, tap 'Forgot Password', or continue as guest."
        }

        Result.failure(IllegalArgumentException(finalError))
    }

    suspend fun sendPasswordReset(email: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a valid email address"))
        }
        if (isFirebaseAvailable()) {
            try {
                FirebaseAuth.getInstance().sendPasswordResetEmail(cleanEmail).awaitResult()
                return@withContext Result.success("If $cleanEmail has a Firebase account, a reset link will arrive in the inbox. Offline-only accounts cannot be reset by email.")
            } catch (e: Exception) {
                val msg = when {
                    e.message?.contains("user-not-found", true) == true || e.message?.contains("no user", true) == true ->
                        "No account found with $cleanEmail. Please sign up."
                    else -> e.message ?: "Failed to send reset email"
                }
                return@withContext Result.failure(IllegalStateException(msg))
            }
        }
        Result.failure(IllegalStateException("Password reset requires an active internet connection."))
    }

    fun signInAsGuest(): AuthUser {
        val guestUser = AuthUser(
            email = "guest@saif.ai",
            name = "Guest User",
            uid = "guest:${java.util.UUID.randomUUID()}",
            role = "user",
            isActive = true,
            isFirebaseUser = false
        )
        commitLogin(guestUser)
        return guestUser
    }

    suspend fun refreshCurrentUserData(): Result<AuthUser> = withContext(Dispatchers.IO) {
        val current = _currentUser.value ?: return@withContext Result.failure(IllegalStateException("No user logged in"))
        var updatedRole = current.role
        var updatedIsActive = current.isActive
        var updatedUsage = current.adminApiUsageCount
        var updatedName = current.name

        // Check Cloud Firestore
        if (current.isFirebaseUser && isFirebaseAvailable() &&
            FirebaseAuth.getInstance().currentUser?.uid == current.uid) {
            val cloudData = fetchCloudUserData(current.uid, current.email)
            if (cloudData != null) {
                updatedRole = extractRole(cloudData)
                val active = when {
                    cloudData.containsKey("Is active") -> cloudData["Is active"] as? Boolean ?: true
                    cloudData.containsKey("isActive") -> cloudData["isActive"] as? Boolean ?: true
                    cloudData.containsKey("status") -> (cloudData["status"] as? String)?.lowercase() != "suspended" && (cloudData["status"] as? String)?.lowercase() != "banned"
                    else -> true
                }
                updatedIsActive = active
                updatedUsage = (cloudData["apiUsageCount"] as? Number)?.toInt() ?: updatedUsage
                val cName = (cloudData["name"] as? String) ?: (cloudData["Name"] as? String) ?: ""
                if (cName.isNotBlank()) updatedName = cName
            }
        }

        // An offline Room cache cannot elevate a Firebase account or undo a cloud ban.
        if (!current.isFirebaseUser) {
            val local = db.userAccountDao().getUserByEmail(current.email)
            if (local != null) {
                if (updatedRole.equals("user", true) && local.role.equals("admin", true)) updatedRole = "admin"
                if (updatedIsActive && !local.isActive) updatedIsActive = false
            }
        }
        if (_currentUser.value?.uid != current.uid ||
            (current.isFirebaseUser && FirebaseAuth.getInstance().currentUser?.uid != current.uid)) {
            return@withContext Result.failure(IllegalStateException("Account changed while refreshing"))
        }
        val updated = current.copy(
            name = updatedName,
            role = updatedRole,
            isActive = updatedIsActive,
            isBanned = !updatedIsActive,
            adminApiUsageCount = updatedUsage
        )
        _currentUser.value = updated
        prefs.edit().putString(KEY_USER_ROLE, updatedRole).apply()
        Result.success(updated)
    }

    suspend fun setLocalAdminRole(email: String, makeAdmin: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val role = if (makeAdmin) "admin" else "user"
            db.userAccountDao().updateUserRole(email, role)
            val current = _currentUser.value
            if (current != null && !current.isFirebaseUser && current.email.equals(email, ignoreCase = true)) {
                _currentUser.value = current.copy(role = role)
                prefs.edit().putString(KEY_USER_ROLE, role).apply()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserName(newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Name cannot be empty"))
        }
        val current = _currentUser.value ?: return@withContext Result.failure(IllegalStateException("No user logged in"))
        
        try {
            // Update preferences
            prefs.edit().putString(KEY_USER_NAME, trimmed).apply()
            
            // Update Room database
            try {
                db.userAccountDao().updateUserName(current.email, trimmed)
            } catch (e: Exception) {
                Log.w(TAG, "Error updating user name in Room: ${e.message}")
            }
            
            // Update Firebase Auth & Firestore if Firebase user
            if (current.isFirebaseUser && isFirebaseAvailable()) {
                try {
                    val fbUser = FirebaseAuth.getInstance().currentUser
                    if (fbUser != null && fbUser.uid == current.uid) {
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(trimmed)
                            .build()
                        fbUser.updateProfile(profileUpdates).awaitResult()
                        
                        val firestore = FirebaseFirestore.getInstance()
                        val updateMap = mapOf("name" to trimmed, "Name" to trimmed)
                        firestore.collection("users").document(fbUser.uid).set(updateMap, SetOptions.merge()).awaitResult()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error updating user name in Firebase: ${e.message}")
                }
            }
            
            // Do not apply a stale write after switching app accounts.
            if (_currentUser.value?.uid == current.uid) _currentUser.value = current.copy(name = trimmed)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Reverify the current account without changing FirebaseAuth to a different user. */
    suspend fun verifyAccountPassword(password: String): Boolean = withContext(Dispatchers.IO) {
        val current = _currentUser.value ?: return@withContext false
        if (password.isBlank()) return@withContext false
        val verified = if (current.isFirebaseUser) {
            try {
                val fbUser = FirebaseAuth.getInstance().currentUser ?: return@withContext false
                if (fbUser.uid != current.uid || !fbUser.email.equals(current.email, true)) return@withContext false
                fbUser.reauthenticate(EmailAuthProvider.getCredential(current.email, password)).awaitResult()
                FirebaseAuth.getInstance().currentUser?.uid == current.uid
            } catch (_: Exception) { false }
        } else if (!LocalIdentity.isGuest(current.email)) {
            val local = db.userAccountDao().getUserByEmail(current.email)
            local != null && PasswordHash.verify(password, local.passwordHash)
        } else false
        verified && _currentUser.value?.uid == current.uid
    }

    fun commitLogin(user: AuthUser) {
        val owner = if (user.isFirebaseUser) {
            val fb = FirebaseAuth.getInstance().currentUser
            require(fb != null && fb.uid == user.uid && fb.email.equals(user.email, true)) {
                "Firebase account changed before login completed"
            }
            user.uid
        } else if (LocalIdentity.isGuest(user.email)) {
            user.uid.takeIf { it.startsWith("guest:") } ?: "guest:${java.util.UUID.randomUUID()}"
        } else LocalIdentity.uidForEmail(user.email)
        val avatarFile = if (::appContext.isInitialized) getUserAvatarFile(appContext, user.email) else null
        val resolvedUser = user.copy(uid = owner, profilePicturePath =
            user.profilePicturePath ?: avatarFile?.takeIf { it.exists() }?.absolutePath)
        userDocListener?.remove()
        userDocListener = null
        saveSession(resolvedUser)
        _currentUser.value = resolvedUser
        com.example.util.SaifMemoryCore.bind(appContext, owner)
        com.example.gemini.data.remote.SavedAiSkills.bind(appContext, owner)
        if (resolvedUser.isFirebaseUser) attachRealtimeUserListener(owner, resolvedUser.email)
    }

    fun signOut() {
        userDocListener?.remove()
        userDocListener = null
        // A local/guest session can hold an anonymous Firebase identity for cloud jobs.
        try { if (isFirebaseAvailable()) FirebaseAuth.getInstance().signOut() } catch (_: Exception) {}
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .remove(KEY_USER_EMAIL).remove(KEY_USER_NAME).remove(KEY_USER_ROLE)
            .remove(KEY_USER_UID).remove(KEY_USER_IS_FIREBASE)
            .apply()
        _currentUser.value = null
        if (::appContext.isInitialized) {
            com.example.util.SaifMemoryCore.bind(appContext, null)
            com.example.gemini.data.remote.SavedAiSkills.bind(appContext, null)
        }
        com.example.cloud.SaifAiCloudManager.onSignedOut()
        com.example.gemini.data.remote.AntigravityAppBinding.clear()
    }

    private fun saveSession(user: AuthUser) {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_USER_EMAIL, user.email)
            .putString(KEY_USER_NAME, user.name)
            .putString(KEY_USER_ROLE, user.role)
            .putString(KEY_USER_UID, user.uid)
            .putBoolean(KEY_USER_IS_FIREBASE, user.isFirebaseUser)
            .apply()
    }
}
