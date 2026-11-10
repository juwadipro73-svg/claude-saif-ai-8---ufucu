/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.gemini.ui.settings

/**
 * Every Advanced Editing Engine destination, in the order shown in SAIF AI's main Settings panel.
 * Keep this list exhaustive when adding a new [SettingsSection]: otherwise the setting becomes unreachable
 * from the main panel. The nested device destinations are listed as well as their Utilities parent.
 */
val ENGINE_SETTINGS_SECTIONS: List<SettingsSection> = listOf(
    SettingsSection.AGENTS,
    SettingsSection.SERVERS,
    SettingsSection.COMMANDS,
    SettingsSection.MCP,
    SettingsSection.SKILLS_PLUGINS,
    SettingsSection.AI_SKILLS,
    SettingsSection.SLASH_COMMANDS,
    SettingsSection.WEBVIEW_EXTENSIONS,
    SettingsSection.CLAUDE,
    SettingsSection.APPEARANCE,
    SettingsSection.TERMINAL,
    SettingsSection.BACKUPS,
    SettingsSection.DATA_PROTECTION,
    SettingsSection.DIAGNOSTICS,
    SettingsSection.UTILITIES,
    SettingsSection.ADB_CONNECT,
    SettingsSection.ANDROID_AUTOMATION,
    SettingsSection.ABOUT
)

/** null means close the detail screen and reveal SAIF AI's original Settings panel. */
fun engineSettingsBackTarget(section: SettingsSection, openedFromSaif: Boolean): SettingsSection? =
    when {
        section == SettingsSection.MAIN -> null
        openedFromSaif && section.parent == SettingsSection.MAIN -> null
        else -> section.parent
    }
