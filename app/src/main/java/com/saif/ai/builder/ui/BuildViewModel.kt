package com.saif.ai.builder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saif.ai.builder.data.BuildRepository
import com.saif.ai.builder.data.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BuildViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsStore(application)
    private val repository = BuildRepository(settings, application.cacheDir)

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var buildJob: Job? = null

    val hasToken: Boolean get() = settings.githubToken.isNotBlank()
    val githubToken: String get() = settings.githubToken
    val firebaseDbUrl: String get() = settings.firebaseDbUrl
    val adGateUrl: String get() = settings.adGateUrl

    fun saveSettings(token: String, firebaseDbUrl: String, adGateUrl: String) {
        settings.githubToken = token
        settings.firebaseDbUrlOverride = firebaseDbUrl
        settings.adGateUrlOverride = adGateUrl
    }

    fun startBuild(prompt: String) {
        val appName = prompt.trim()
        if (appName.isBlank()) {
            _state.value = UiState.Failed("App ka naam / prompt likho 🙂")
            return
        }
        if (buildJob?.isActive == true) return

        buildJob = viewModelScope.launch {
            val log = mutableListOf<String>()

            fun push(line: String) {
                log += line
                _state.value = UiState.Running(line, log.toList())
            }

            push("▶ Build start → $appName")
            try {
                val result = repository.buildAndPublish(appName) { step -> push(step) }

                // Build complete — ab download button visible (snippet ka core).
                push("✅ Link ready: ${result.downloadUrl}")
                _state.value = UiState.Ready(
                    downloadUrl = result.downloadUrl,
                    appName = result.appName,
                    runUrl = result.runUrl,
                    log = log.toList()
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val message = error.message ?: "Kuch galat ho gaya (${error.javaClass.simpleName})"
                push("❌ $message")
                _state.value = UiState.Failed(message, log.toList())
            }
        }
    }

    fun cancelBuild() {
        buildJob?.cancel()
        buildJob = null
        _state.value = UiState.Idle
    }

    override fun onCleared() {
        buildJob?.cancel()
        super.onCleared()
    }
}
