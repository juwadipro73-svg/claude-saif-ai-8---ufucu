/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import com.jarvis.assistant.util.PermissionHelper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU], application = Application::class)
class LiveConversationPermissionTest {
    @Test fun foregroundLiveVoiceNeedsMicButNotBatteryExemptionOrNotifications() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        shadowOf(power).setIgnoringBatteryOptimizations(app.packageName, false)
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertFalse(PermissionHelper.hasBatteryOptimizationPermission(app))
        assertFalse(PermissionHelper.hasAllRequiredPermissions(app))
        assertTrue(PermissionHelper.hasLiveConversationPermission(app))

        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        assertFalse(PermissionHelper.hasLiveConversationPermission(app))
    }
}
