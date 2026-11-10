/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.ui.components

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class FileUpdateEvent(val projectName: String, val filePath: String? = null)

object ProjectEditorCoordinator {
    private val _globalFileUpdates = MutableSharedFlow<FileUpdateEvent>(extraBufferCapacity = 64)
    val globalFileUpdates = _globalFileUpdates.asSharedFlow()

    fun notifyFileUpdated(projectName: String, filePath: String? = null) {
        _globalFileUpdates.tryEmit(FileUpdateEvent(projectName, filePath))
    }
}
