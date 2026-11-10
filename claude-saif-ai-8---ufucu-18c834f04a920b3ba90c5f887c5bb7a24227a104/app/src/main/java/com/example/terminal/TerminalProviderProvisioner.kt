/*
 * Copyright (c) SAIF AI. All rights reserved.
 *
 * Original code written for SAIF AI. Distributed as part of the SAIF AI app, which ships
 * under the GNU GPL v3 (see LICENSE and THIRD_PARTY_NOTICES.md).
 */

package com.example.terminal

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** How far the background preparation of one provider has got. */
enum class TerminalProvisionStage {
    IDLE,
    /** The shared Linux environment (bash, curl, proot, Ubuntu rootfs) is being prepared. */
    ENVIRONMENT,
    /** The provider's own CLI wrapper is being written into the Linux environment. */
    CLI,
    READY,
    FAILED
}

data class TerminalProvisionStatus(
    val providerId: String = "",
    val stage: TerminalProvisionStage = TerminalProvisionStage.IDLE,
    val detail: String = "",
    val percent: Float = 0f,
    val isReady: Boolean = false
)

/**
 * Prepares the terminal for the chosen [TerminalProvider] entirely in the background.
 *
 * Picking a provider on the post-sign-in screen never blocks the user: SAIF AI downloads what it
 * needs (the same way it always prepared its Linux environment), writes a launcher for the
 * provider's CLI, and then simply reports "ready". Progress is visible in Settings → Terminal
 * Providers, and the terminal button in the header opens as soon as the environment is verified.
 */
object TerminalProviderProvisioner {

    private const val TAG = "TerminalProviderProvisioner"
    private const val ENV_TIMEOUT_MS = 20 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var jobForId: String? = null

    private val _status = MutableStateFlow(TerminalProvisionStatus())
    val status: StateFlow<TerminalProvisionStatus> = _status.asStateFlow()

    /** Starts (or resumes) background preparation for [providerId]. Safe to call repeatedly. */
    fun start(context: Context, providerId: String) {
        val app = context.applicationContext
        TerminalProviders.init(app)
        if (job?.isActive == true && jobForId == providerId) return
        job?.cancel()
        jobForId = providerId
        _status.value = TerminalProvisionStatus(
            providerId = providerId,
            stage = TerminalProvisionStage.ENVIRONMENT,
            detail = "Preparing the Linux environment…",
            percent = 0.02f
        )
        job = scope.launch { run(app, providerId) }
    }

    /** Starts preparation for the provider the user already selected. */
    fun startForSelected(context: Context) {
        val app = context.applicationContext
        TerminalProviders.init(app)
        start(app, TerminalProviders.selected(app).id)
    }

    private suspend fun run(context: Context, providerId: String) {
        val provider = TerminalProviders.find(context, providerId) ?: run {
            _status.value = TerminalProvisionStatus(providerId, TerminalProvisionStage.FAILED, "Unknown provider", 0f)
            return
        }
        try {
            // 1. The shared Linux environment. Downloads happen in the background with a
            //    notification; this only waits for it so the CLI step is not run too early.
            withContext(Dispatchers.IO) {
                com.example.gemini.data.local.LocalEnvironmentManager.ensureInstalledInBackground(context)
            }
            if (!awaitEnvironment(context)) {
                _status.value = TerminalProvisionStatus(
                    providerId, TerminalProvisionStage.FAILED,
                    "Linux tools could not be prepared. Check your connection and retry from Settings → Terminal Providers.",
                    0f
                )
                return
            }
            _status.value = TerminalProvisionStatus(
                providerId, TerminalProvisionStage.CLI,
                "Installing ${provider.name} in the terminal…", 0.75f
            )

            // 2. The provider's launcher inside the Linux environment.
            when (provider.kind) {
                TerminalProviderKind.ANTIGRAVITY -> ensureAntigravityLauncher(context, provider)
                else -> writeCliWrapper(context, provider)
            }

            _status.value = TerminalProvisionStatus(
                providerId, TerminalProvisionStage.READY,
                "${provider.name} is ready. Open the terminal from the header.",
                1f, isReady = true
            )
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "Provisioning ${provider.id} failed: ${t.message}")
            _status.value = TerminalProvisionStatus(
                providerId, TerminalProvisionStage.FAILED,
                t.message ?: "Preparation failed", 0f
            )
        }
    }

    /** Waits (without blocking the UI) until the shared environment is usable. */
    private suspend fun awaitEnvironment(context: Context): Boolean {
        val started = android.os.SystemClock.elapsedRealtime()
        while (android.os.SystemClock.elapsedRealtime() - started < ENV_TIMEOUT_MS) {
            if (com.example.gemini.data.local.LocalEnvironmentManager.hasVerifiedEnvironment(context)) return true
            if (com.example.gemini.data.local.LocalEnvironmentManager.isInstalled(context) &&
                com.example.gemini.data.local.LocalEnvironmentManager.getBinDir(context).isDirectory
            ) {
                // bash is present: good enough to install a launcher even if verification lags.
                if (File(com.example.gemini.data.local.LocalEnvironmentManager.getBinDir(context), "bash").exists()) return true
            }
            val state = com.example.gemini.data.local.LocalEnvironmentManager.installerState.value
            if (state is com.example.gemini.data.local.LocalInstallerState.Error) return false
            _status.value = _status.value.copy(
                stage = TerminalProvisionStage.ENVIRONMENT,
                detail = environmentMessage(state),
                percent = environmentPercent(state)
            )
            delay(1500)
        }
        return false
    }

    private fun environmentMessage(state: com.example.gemini.data.local.LocalInstallerState): String = when (state) {
        is com.example.gemini.data.local.LocalInstallerState.Downloading ->
            "Downloading Linux tools… ${state.bytesDownloaded / (1024 * 1024)} MB"
        is com.example.gemini.data.local.LocalInstallerState.Extracting -> "Extracting Linux tools…"
        is com.example.gemini.data.local.LocalInstallerState.Configuring -> "Configuring Linux tools…"
        is com.example.gemini.data.local.LocalInstallerState.Verifying -> "Verifying Linux tools…"
        is com.example.gemini.data.local.LocalInstallerState.Discovering -> "Looking for the Linux tools package…"
        is com.example.gemini.data.local.LocalInstallerState.AwaitingConfirmation -> "Waiting for your confirmation…"
        else -> "Preparing the Linux environment in the background…"
    }

    private fun environmentPercent(state: com.example.gemini.data.local.LocalInstallerState): Float = when (state) {
        is com.example.gemini.data.local.LocalInstallerState.Downloading -> {
            val total = state.totalBytes.takeIf { it > 0 } ?: 0L
            if (total <= 0L) 0.25f else (0.05f + 0.5f * (state.bytesDownloaded.toFloat() / total)).coerceIn(0.05f, 0.55f)
        }
        is com.example.gemini.data.local.LocalInstallerState.Extracting -> 0.6f
        is com.example.gemini.data.local.LocalInstallerState.Configuring -> 0.65f
        is com.example.gemini.data.local.LocalInstallerState.Verifying -> 0.7f
        else -> 0.2f
    }

    /** Antigravity needs no launcher of its own: SAIF AI's bridge is the CLI. */
    private fun ensureAntigravityLauncher(context: Context, provider: TerminalProvider) {
        val bin = com.example.gemini.data.local.LocalEnvironmentManager.getBinDir(context)
        if (!bin.isDirectory) return
        writeScript(
            file = File(bin, provider.cliCommand),
            body = antigravityScript(provider)
        )
    }

    /** Writes a small launcher that talks to the provider's API with the key the user stored. */
    private fun writeCliWrapper(context: Context, provider: TerminalProvider) {
        val env = com.example.gemini.data.local.LocalEnvironmentManager
        val bin = env.getBinDir(context)
        val home = env.getHomeDir(context)
        if (!bin.isDirectory) return

        // The key lives in the Android Keystore; the CLI can only read it if the user allows a copy
        // inside the Linux sandbox (Settings → Terminal Providers → "Give the terminal this key").
        val keyDir = File(home, ".saif/providers")
        keyDir.mkdirs()
        val keyFile = File(keyDir, "${provider.id}.key")
        if (TerminalProviders.isTerminalKeyShared(context, provider.id)) {
            val key = TerminalProviders.apiKey(context, provider.id)
            if (key.isNotBlank()) {
                keyFile.writeText(key)
                keyFile.setReadable(true, true)
                keyFile.setWritable(true, true)
            }
        } else {
            if (keyFile.exists()) keyFile.delete()
        }
        writeScript(file = File(bin, provider.cliCommand), body = cliScript(provider))
    }

    private fun writeScript(file: File, body: String) {
        try {
            if (file.exists() && file.readText() == body) {
                file.setExecutable(true, false)
                return
            }
            val tmp = File(file.parentFile, ".${file.name}.tmp")
            tmp.writeText(body)
            tmp.setExecutable(true, false)
            tmp.setReadable(true, false)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
            file.setExecutable(true, false)
            file.setReadable(true, false)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Could not write ${file.name}: ${e.message}")
        }
    }

    private fun antigravityScript(provider: TerminalProvider): String {
        val bin = "\$HOME/.local/bin"
        return """
            |#!/usr/bin/env bash
            |# ${provider.name} launcher written by SAIF AI. Edits are overwritten.
            |# Antigravity runs through SAIF AI's own bridge: use the app's chat for agent turns.
            |set -u
            |if [ -x "$bin/agy" ]; then exec "$bin/agy" "${'$'}@"; fi
            |echo "${provider.name} is driven from SAIF AI's chat and terminal: the bridge is started automatically." >&2
            |exit 0
            |""".trimMargin()
    }

    private fun cliScript(provider: TerminalProvider): String {
        val kind = if (provider.kind == TerminalProviderKind.ANTHROPIC) "anthropic" else "openai"
        val keyPath = "\$HOME/.saif/providers/${provider.id}.key"
        return """
            |#!/usr/bin/env bash
            |# ${provider.name} launcher written by SAIF AI. Edits are overwritten.
            |set -u
            |KEY_FILE="$keyPath"
            |BASE_URL="${provider.apiBaseUrl}"
            |MODEL="${provider.defaultModel}"
            |KIND="$kind"
            |if [ ! -f "${'$'}KEY_FILE" ]; then
            |  echo "${provider.name}: no API key inside the Linux sandbox." >&2
            |  echo "Open SAIF AI -> Settings -> Terminal Providers, re-save the key and tap Prepare again." >&2
            |  exit 1
            |fi
            |KEY="$(cat "${'$'}KEY_FILE")"
            |PROMPT="${'$'}*"
            |if [ -z "${'$'}PROMPT" ]; then
            |  echo "Usage: ${provider.cliCommand} \"your prompt\"" >&2
            |  exit 64
            |fi
            |ESC="$(printf '%s' "${'$'}PROMPT" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' | tr '\n' ' ')"
            |if [ "${'$'}KIND" = "anthropic" ]; then
            |  URL="${'$'}BASE_URL/messages"
            |  HEADERS=(-H "x-api-key: ${'$'}KEY" -H "anthropic-version: 2023-06-01" -H "content-type: application/json")
            |  BODY="$(printf '{"model":"%s","max_tokens":8192,"stream":true,"messages":[{"role":"user","content":"%s"}]}' "${'$'}MODEL" "${'$'}ESC")"
            |else
            |  URL="${'$'}BASE_URL/chat/completions"
            |  HEADERS=(-H "Authorization: Bearer ${'$'}KEY" -H "content-type: application/json")
            |  BODY="$(printf '{"model":"%s","stream":true,"messages":[{"role":"user","content":"%s"}]}' "${'$'}MODEL" "${'$'}ESC")"
            |fi
            |exec curl -sS -N -X POST "${'$'}URL" "${'$'}{HEADERS[@]}" -d "${'$'}BODY" | sed -u -e 's/^data: \[DONE\]$//' -e 's/^data: //'
            |""".trimMargin()
    }

    /** Removes the copy of the key kept inside the Linux sandbox. */
    fun revokeTerminalKey(context: Context, providerId: String) {
        try {
            val home = com.example.gemini.data.local.LocalEnvironmentManager.getHomeDir(context)
            val keyFile = File(home, ".saif/providers/$providerId.key")
            if (keyFile.exists()) keyFile.delete()
        } catch (_: Exception) {
        }
    }
}
