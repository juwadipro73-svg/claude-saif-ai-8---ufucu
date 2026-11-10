/*
 * Copyright (c) SAIF AI. All rights reserved.
 *
 * Original code written for SAIF AI. Distributed as part of the SAIF AI app, which ships
 * under the GNU GPL v3 (see LICENSE and THIRD_PARTY_NOTICES.md).
 */

package com.example.terminal

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** What kind of terminal backend a provider is. */
enum class TerminalProviderKind {
    /** SAIF AI's own Antigravity workspace: the in-app bridge does the work, no key needed here. */
    ANTIGRAVITY,

    /** Any OpenAI-compatible chat-completions endpoint the user points us at. */
    OPENAI_COMPATIBLE,

    /** Anthropic messages API. */
    ANTHROPIC
}

/**
 * One terminal provider the user can pick.
 *
 * [cliCommand] is the command SAIF AI writes into the Linux environment so the same provider is
 * reachable from a terminal session as well.
 */
data class TerminalProvider(
    val id: String,
    val name: String,
    val tagline: String,
    val description: String,
    val kind: TerminalProviderKind,
    val cliCommand: String,
    val apiBaseUrl: String = "",
    val defaultModel: String = "",
    /** Empty when the provider needs no key (Antigravity uses the app's own sign-in). */
    val authHint: String = "",
    val isCustom: Boolean = false
) {
    fun needsApiKey(): Boolean = kind != TerminalProviderKind.ANTIGRAVITY
}

/**
 * The single source of truth for terminal providers: the built-in list, the user's own custom
 * providers, which one is active, and each provider's API key.
 *
 * Keys are encrypted with the same Android Keystore vault the rest of SAIF AI uses
 * (`com.example.llm.KeyStoreCrypto`); they are only ever decrypted in memory.
 */
object TerminalProviders {

    private const val PREFS = "saif_terminal_providers"
    private const val KEY_SELECTED = "selected_provider_id"
    private const val KEY_CUSTOM = "custom_providers"
    private const val KEY_KEYS = "provider_keys"
    private const val KEY_URLS = "provider_urls"
    private const val KEY_MODELS = "provider_models"
    private const val KEY_SHARED = "shared_keys"

    /** Built-in providers. Their URLs/models are starting points the user may edit. */
    val BUILT_IN: List<TerminalProvider> = listOf(
        TerminalProvider(
            id = "antigravity",
            name = "Antigravity",
            tagline = "SAIF AI's own agent workspace",
            description = "Uses the Antigravity account you connect inside SAIF AI. No extra API key: chat, "
                + "terminal, code editing and file tools all come from the in-app bridge.",
            kind = TerminalProviderKind.ANTIGRAVITY,
            cliCommand = "agy",
            authHint = "Sign in from Settings → Antigravity Connection"
        ),
        TerminalProvider(
            id = "opencode",
            name = "OpenCode",
            tagline = "Open-source coding agent",
            description = "Run the OpenCode agent in SAIF AI's terminal. Add your OpenCode/compatible API key "
                + "and SAIF AI prepares the CLI in the background.",
            kind = TerminalProviderKind.OPENAI_COMPATIBLE,
            cliCommand = "opencode",
            apiBaseUrl = "https://opencode.ai/api/v1",
            defaultModel = "opencode/big-pickle",
            authHint = "API key from your OpenCode provider"
        ),
        TerminalProvider(
            id = "arena",
            name = "Arena AI",
            tagline = "Arena.ai agent runtime",
            description = "Use an Arena AI endpoint as the brain behind SAIF AI's terminal. Works with any "
                + "OpenAI-compatible Arena gateway URL you have access to.",
            kind = TerminalProviderKind.OPENAI_COMPATIBLE,
            cliCommand = "arena",
            apiBaseUrl = "https://api.arena.ai/v1",
            defaultModel = "arena-auto",
            authHint = "Arena AI API key"
        ),
        TerminalProvider(
            id = "claude",
            name = "Claude Code",
            tagline = "Anthropic Claude Code",
            description = "Run Claude Code inside SAIF AI's Linux terminal. SAIF AI writes the launcher and "
                + "keeps it working across Claude's own updates.",
            kind = TerminalProviderKind.ANTHROPIC,
            cliCommand = "claude",
            apiBaseUrl = "https://api.anthropic.com/v1",
            defaultModel = "claude-sonnet-4-5",
            authHint = "Anthropic API key"
        ),
        TerminalProvider(
            id = "codex",
            name = "Codex CLI",
            tagline = "OpenAI Codex CLI",
            description = "Run the Codex CLI against your OpenAI-compatible endpoint, prepared automatically "
                + "in the background after you pick it.",
            kind = TerminalProviderKind.OPENAI_COMPATIBLE,
            cliCommand = "codex",
            apiBaseUrl = "https://api.openai.com/v1",
            defaultModel = "gpt-5-codex",
            authHint = "OpenAI API key"
        )
    )

    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private fun store(context: Context): android.content.SharedPreferences {
        if (prefs == null) init(context)
        return prefs!!
    }

    private fun readCustom(context: Context): List<TerminalProvider> {
        val raw = store(context).getString(KEY_CUSTOM, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id")
                if (id.isBlank()) return@mapNotNull null
                TerminalProvider(
                    id = id,
                    name = o.optString("name", id),
                    tagline = o.optString("tagline", "Custom provider"),
                    description = o.optString("description", "Provider you added yourself."),
                    kind = runCatching { TerminalProviderKind.valueOf(o.optString("kind", "OPENAI_COMPATIBLE")) }
                        .getOrDefault(TerminalProviderKind.OPENAI_COMPATIBLE),
                    cliCommand = o.optString("cliCommand", id),
                    apiBaseUrl = o.optString("apiBaseUrl", ""),
                    defaultModel = o.optString("defaultModel", ""),
                    authHint = o.optString("authHint", "Your API key"),
                    isCustom = true
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeCustom(context: Context, list: List<TerminalProvider>) {
        val array = JSONArray()
        list.forEach { p ->
            array.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("tagline", p.tagline)
                put("description", p.description)
                put("kind", p.kind.name)
                put("cliCommand", p.cliCommand)
                put("apiBaseUrl", p.apiBaseUrl)
                put("defaultModel", p.defaultModel)
                put("authHint", p.authHint)
            })
        }
        store(context).edit().putString(KEY_CUSTOM, array.toString()).apply()
    }

    /** Every provider: the built-ins plus the user's own, with any edited URL/model applied. */
    fun all(context: Context): List<TerminalProvider> {
        val urls = JSONObject(store(context).getString(KEY_URLS, "{}") ?: "{}")
        val models = JSONObject(store(context).getString(KEY_MODELS, "{}") ?: "{}")
        return (BUILT_IN + readCustom(context)).map { p ->
            val url = urls.optString(p.id)
            val model = models.optString(p.id)
            p.copy(
                apiBaseUrl = if (url.isNotBlank()) url else p.apiBaseUrl,
                defaultModel = if (model.isNotBlank()) model else p.defaultModel
            )
        }
    }

    fun find(context: Context, id: String): TerminalProvider? =
        all(context).firstOrNull { it.id.equals(id, ignoreCase = true) }

    /** The active provider. Falls back to Antigravity until the user picks one. */
    fun selected(context: Context): TerminalProvider {
        val id = store(context).getString(KEY_SELECTED, null)
        return all(context).firstOrNull { it.id.equals(id, ignoreCase = true) } ?: BUILT_IN.first()
    }

    /** True once the user has actively chosen a provider (the post-sign-in screen is then not shown). */
    fun hasChosenProvider(context: Context): Boolean =
        !store(context).getString(KEY_SELECTED, null).isNullOrBlank()

    fun select(context: Context, id: String) {
        store(context).edit().putString(KEY_SELECTED, id).apply()
    }

    fun apiKey(context: Context, id: String): String {
        val obj = JSONObject(store(context).getString(KEY_KEYS, "{}") ?: "{}")
        val raw = obj.optString(id)
        if (raw.isBlank()) return ""
        return runCatching { com.example.llm.KeyStoreCrypto.decrypt(raw) }.getOrElse { raw }
    }

    fun setApiKey(context: Context, id: String, key: String) {
        val obj = JSONObject(store(context).getString(KEY_KEYS, "{}") ?: "{}")
        if (key.isBlank()) obj.remove(id)
        else obj.put(id, runCatching { com.example.llm.KeyStoreCrypto.encrypt(key.trim()) }.getOrDefault(key.trim()))
        store(context).edit().putString(KEY_KEYS, obj.toString()).apply()
    }

    fun setBaseUrl(context: Context, id: String, url: String) {
        val obj = JSONObject(store(context).getString(KEY_URLS, "{}") ?: "{}")
        if (url.isBlank()) obj.remove(id) else obj.put(id, url.trim())
        store(context).edit().putString(KEY_URLS, obj.toString()).apply()
    }

    fun setModel(context: Context, id: String, model: String) {
        val obj = JSONObject(store(context).getString(KEY_MODELS, "{}") ?: "{}")
        if (model.isBlank()) obj.remove(id) else obj.put(id, model.trim())
        store(context).edit().putString(KEY_MODELS, obj.toString()).apply()
    }

    /**
     * Whether a copy of the key may be placed inside the Linux sandbox so the provider's CLI can
     * read it. Off by default: the chat/agent paths never need it, only the terminal command does.
     */
    fun isTerminalKeyShared(context: Context, id: String): Boolean {
        val set = store(context).getStringSet(KEY_SHARED, emptySet()).orEmpty()
        return id in set
    }

    fun setTerminalKeyShared(context: Context, id: String, shared: Boolean) {
        val set = store(context).getStringSet(KEY_SHARED, emptySet()).orEmpty().toMutableSet()
        if (shared) set.add(id) else set.remove(id)
        store(context).edit().putStringSet(KEY_SHARED, set).apply()
        if (!shared) com.example.terminal.TerminalProviderProvisioner.revokeTerminalKey(context, id)
    }

    /** A stable command name for a display name: lowercase, letters/digits/hyphens only. */
    fun commandFor(name: String): String =
        name.trim().lowercase().map { ch -> if (ch.isLetterOrDigit()) ch else '-' }
            .joinToString("").trim('-').ifBlank { "custom" }

    /** Adds a provider the user typed in. Throws with a message the UI can show. */
    fun addCustom(
        context: Context,
        name: String,
        command: String,
        baseUrl: String,
        model: String,
        kind: TerminalProviderKind,
        apiKey: String
    ): TerminalProvider {
        val cleanName = name.trim()
        require(cleanName.length in 2..40) { "Give the provider a name (2–40 characters)" }
        val cmd = command.trim().ifBlank { commandFor(cleanName) }
        require(Regex("[a-z0-9][a-z0-9_-]{1,31}").matches(cmd)) {
            "The command may only use lowercase letters, digits, '_' and '-' (2–32 characters)"
        }
        require(baseUrl.trim().startsWith("http")) { "Enter a base URL starting with http:// or https://" }
        val id = "custom_" + cmd
        val existing = readCustom(context)
        require(existing.none { it.id.equals(id, ignoreCase = true) }) { "A provider with the command '$cmd' already exists" }
        require(BUILT_IN.none { it.cliCommand.equals(cmd, ignoreCase = true) }) { "'$cmd' is already a built-in provider" }
        val provider = TerminalProvider(
            id = id,
            name = cleanName,
            tagline = "Your provider",
            description = "Added by you. SAIF AI prepares '$cmd' in the Linux terminal in the background.",
            kind = kind,
            cliCommand = cmd,
            apiBaseUrl = baseUrl.trim().trimEnd('/'),
            defaultModel = model.trim(),
            authHint = "Your $cleanName API key",
            isCustom = true
        )
        writeCustom(context, existing + provider)
        setBaseUrl(context, id, provider.apiBaseUrl)
        if (provider.defaultModel.isNotBlank()) setModel(context, id, provider.defaultModel)
        if (apiKey.isNotBlank()) setApiKey(context, id, apiKey)
        return provider
    }

    fun removeCustom(context: Context, id: String) {
        val remaining = readCustom(context).filterNot { it.id.equals(id, ignoreCase = true) }
        writeCustom(context, remaining)
        setApiKey(context, id, "")
        setBaseUrl(context, id, "")
        setModel(context, id, "")
        if (store(context).getString(KEY_SELECTED, null).equals(id, ignoreCase = true)) {
            store(context).edit().putString(KEY_SELECTED, BUILT_IN.first().id).apply()
        }
    }

    /** Everything the terminal needs to call this provider, resolved in one place. */
    fun resolved(context: Context, id: String): ResolvedProvider? {
        val p = find(context, id) ?: return null
        return ResolvedProvider(
            provider = p,
            apiKey = apiKey(context, p.id),
            isReady = !p.needsApiKey() || apiKey(context, p.id).isNotBlank()
        )
    }

    data class ResolvedProvider(
        val provider: TerminalProvider,
        val apiKey: String,
        val isReady: Boolean
    )
}
