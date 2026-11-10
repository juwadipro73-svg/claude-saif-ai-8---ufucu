/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.gemini.data.local

import android.content.Context
import com.example.data.local.AuthManager
import com.example.gemini.data.agent.AgentPreferences
import com.example.gemini.domain.model.AgentKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Start the hub only for a signed-in user after background Linux setup is verified. */
object AuthenticatedWorkspace {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var installJob: Job? = null

    private fun hasActiveUser(): Boolean = AuthManager.currentUser.value.let { it != null && it.isActive && !it.isBanned }

    @Synchronized
    fun onSignedOut() {
        installJob?.cancel()
        installJob = null
        val status = LocalServerManager.status.value
        if (status is LocalServerStatus.Running || status is LocalServerStatus.Starting) {
            LocalServerManager.stopServer()
        }
    }

    @Synchronized
    fun ensureStarted(context: Context) {
        val app = context.applicationContext
        if (!hasActiveUser()) return
        if (!LocalEnvironmentManager.hasVerifiedEnvironment(app)) {
            // Sign-in can happen before the background job even starts. Wait for a verified
            // success across retries; an Error must not permanently lose the startup signal.
            if (installJob?.isActive != true) {
                installJob = scope.launch {
                    LocalEnvironmentManager.installerState.first {
                        it is LocalInstallerState.Success && LocalEnvironmentManager.hasVerifiedEnvironment(app)
                    }
                    if (hasActiveUser()) {
                        if (!AgentPreferences.hasChosenSync(app)) AgentPreferences(app).setEnabled(setOf(AgentKind.AGY))
                        startServerIfNeeded(app)
                    }
                }
            }
            return
        }
        if (!AgentPreferences.hasChosenSync(app)) AgentPreferences(app).setEnabled(setOf(AgentKind.AGY))
        startServerIfNeeded(app)
    }

    private fun startServerIfNeeded(context: Context) {
        if (!hasActiveUser() || !LocalEnvironmentManager.hasVerifiedEnvironment(context)) return
        val status = LocalServerManager.status.value
        if (status !is LocalServerStatus.Running && status !is LocalServerStatus.Starting) {
            LocalServerManager.startServer(context)
        }
    }
}
