/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.gemini.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class EngineSettingsNavigationTest {
    @Test fun originalSettingsExposeEveryEngineDestinationExactlyOnce() {
        assertFalse(ENGINE_SETTINGS_SECTIONS.contains(SettingsSection.MAIN))
        assertEquals(ENGINE_SETTINGS_SECTIONS.size, ENGINE_SETTINGS_SECTIONS.toSet().size)
        assertEquals(SettingsSection.entries.toSet() - SettingsSection.MAIN, ENGINE_SETTINGS_SECTIONS.toSet())
    }

    @Test fun engineDetailsReturnToSaifSettingsInsteadOfAnEngineMainMenu() {
        for (section in ENGINE_SETTINGS_SECTIONS) {
            if (section.parent == SettingsSection.MAIN) {
                assertNull("${section.name} must return to SAIF AI settings", engineSettingsBackTarget(section, openedFromSaif = true))
            }
        }
        assertEquals(SettingsSection.UTILITIES,
            engineSettingsBackTarget(SettingsSection.ADB_CONNECT, openedFromSaif = true))
        assertEquals(SettingsSection.UTILITIES,
            engineSettingsBackTarget(SettingsSection.ANDROID_AUTOMATION, openedFromSaif = true))
        assertNull(engineSettingsBackTarget(SettingsSection.UTILITIES, openedFromSaif = true))
    }

    @Test fun standaloneEngineSettingsKeepTheirOriginalBackNavigation() {
        assertEquals(SettingsSection.MAIN,
            engineSettingsBackTarget(SettingsSection.SERVERS, openedFromSaif = false))
        assertEquals(SettingsSection.UTILITIES,
            engineSettingsBackTarget(SettingsSection.ADB_CONNECT, openedFromSaif = false))
        assertNull(engineSettingsBackTarget(SettingsSection.MAIN, openedFromSaif = false))
    }
}
