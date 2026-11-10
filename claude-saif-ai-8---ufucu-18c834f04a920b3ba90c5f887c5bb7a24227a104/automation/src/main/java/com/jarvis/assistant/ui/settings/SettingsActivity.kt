/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.jarvis.assistant.ui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.assistant.R

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        com.jarvis.assistant.util.ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        findViewById<android.view.View>(R.id.settingsContainer)?.let {
            com.jarvis.assistant.util.ThemeManager.applyDynamicBackground(it)
        }
    }
}
