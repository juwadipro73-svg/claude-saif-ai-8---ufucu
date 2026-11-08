package com.saif.ai.builder.ui

sealed interface UiState {

    data object Idle : UiState

    data class Running(
        val step: String,
        val log: List<String> = emptyList(),
    ) : UiState

    data class Ready(
        val downloadUrl: String,
        val appName: String,
        val runUrl: String,
        val log: List<String> = emptyList(),
    ) : UiState

    data class Failed(
        val message: String,
        val log: List<String> = emptyList(),
    ) : UiState
}
