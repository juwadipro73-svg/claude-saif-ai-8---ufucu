/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.gemini.data.local.LocalEnvironmentManager
import com.example.gemini.data.local.LocalInstallerState
import com.example.gemini.data.preferences.AuthPreferences
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackgroundLinuxSetupTest {
    @Test
    fun existingVerifiedInstallMigratesWithoutDownloadingAgain() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // A pre-update install has a shell and the old DataStore flag, but no new marker.
        val shell = File(context.filesDir, "ubuntu/bin/sh")
        shell.parentFile!!.mkdirs()
        shell.writeText("#!/bin/sh\n")
        val manager = LocalEnvironmentManager
        val prefs = AuthPreferences(context)
        prefs.setLocalToolsInstalled(true)
        assertFalse(manager.hasVerifiedEnvironment(context))

        manager.ensureInstalledInBackground(context)

        assertTrue(manager.hasVerifiedEnvironment(context))
        assertTrue(manager.installerState.value is LocalInstallerState.Idle)
        // A later resume must be a no-op, not a second bootstrap download.
        manager.ensureInstalledInBackground(context)
        assertTrue(manager.installerState.value is LocalInstallerState.Idle)
    }
}
