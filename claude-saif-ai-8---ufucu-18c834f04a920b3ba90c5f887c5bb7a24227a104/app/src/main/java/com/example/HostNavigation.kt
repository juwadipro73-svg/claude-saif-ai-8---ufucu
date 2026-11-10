/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example

import kotlinx.coroutines.flow.MutableStateFlow

/** Lets other modules (SAIF AI AUTOMATION) ask the Compose UI to open its Settings panel. */
object HostNavigation {
    val openSettingsRequest = MutableStateFlow(false)
}
