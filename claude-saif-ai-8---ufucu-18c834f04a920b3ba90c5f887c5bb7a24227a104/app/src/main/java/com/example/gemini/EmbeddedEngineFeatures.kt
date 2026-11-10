/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.gemini

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.gemini.theme.GeminiTheme
import com.example.gemini.ui.browser.BrowserScreen
import com.example.gemini.ui.chat.ChatViewModelHolder
import com.example.gemini.ui.ide.IdeScreen

/**
 * Starts the background pieces the SAIF AI agent needs (local server, bridge, daemon, terminal bridge ...).
 * SAIF AI calls this once at start-up, so signing in and chatting never depend on any extra screen being opened.
 */
object EngineBootstrap {
    @Volatile
    private var started = false
    private var localServicesStarted = false

    /** Do not launch the terminal service or hub against a half-extracted rootfs. */
    private fun startLocalServicesIfReady(app: android.app.Application) {
        if (localServicesStarted ||
            !com.example.gemini.data.local.LocalEnvironmentManager.hasVerifiedEnvironment(app) ||
            com.example.data.local.AuthManager.currentUser.value?.isActive != true ||
            com.example.data.local.AuthManager.currentUser.value?.isBanned == true) return
        try {
            if (com.example.gemini.ui.components.PermissionUtils.hasNotificationPermission(app)) {
                com.example.gemini.data.service.TermuxService.start(app)
                com.example.gemini.data.local.LocalServerManager.autoStartOnAppLaunch(app)
                localServicesStarted = true
            }
        } catch (_: Throwable) { /* Retry when the app returns to the foreground. */ }
    }

    fun ensureStarted(app: android.app.Application) {
        if (started) {
            startLocalServicesIfReady(app)
            return
        }
        started = true
        val chatViewModel = ChatViewModelHolder.get(app)
        try {
            com.example.gemini.data.remote.inspector.NetworkInspectorManager.isEnabled =
                chatViewModel.authPreferences.getNetworkInspectorEnabledSync()
            com.example.gemini.data.remote.inspector.NetworkInspectorManager.isFloatingBubbleEnabled =
                chatViewModel.authPreferences.getFloatingNetworkInspectorEnabledSync()
        } catch (_: Throwable) {
        }
        try {
            android.webkit.WebView.enableSlowWholeDocumentDraw()
        } catch (_: Throwable) {
        }
        startLocalServicesIfReady(app)
        try { com.example.gemini.data.daemon.TermuxDaemonManager.init(app) } catch (_: Throwable) {}
        try { com.example.gemini.ui.browser.BrowserSessionManager.instance.init(app) } catch (_: Throwable) {}
        try { com.example.gemini.data.local.LocalTerminalBridge.instance.init(app) } catch (_: Throwable) {}
        try { com.example.gemini.data.notes.NotesRepository.init(app) } catch (_: Throwable) {}
        try { com.example.gemini.data.remote.AndroidLocalBridgeServer.instance.start(app) } catch (_: Throwable) {}
        try { com.example.llm.AntigravityLlmBridge.init(app) } catch (_: Throwable) {}
    }
}

/**
 * Browser, Linux terminal, code editor and notes, shown INSIDE SAIF AI in SAIF AI's own colours.
 * There is no separate chat screen here: SAIF AI's main page is the one and only chat.
 *
 * [activeMode] is "IDE", "TERMINAL", "BROWSER", "NOTES" or null (nothing shown; opened screens stay alive in the
 * background so their tabs / sessions are kept). [onModeChange] switches screen (null = back to SAIF AI).
 */
@Composable
fun EmbeddedEngineFeatures(
    activeMode: String?,
    isLightMode: Boolean,
    onModeChange: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var openedModes by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(activeMode) {
        if (activeMode != null) openedModes = openedModes + activeMode
    }
    if (activeMode != null || openedModes.isNotEmpty()) {
        EngineFeaturesBody(
            activeMode = activeMode,
            openedModes = if (activeMode != null) openedModes + activeMode else openedModes,
            isLightMode = isLightMode,
            onModeChange = onModeChange,
            modifier = modifier
        )
    }
}

@Composable
private fun EngineFeaturesBody(
    activeMode: String?,
    openedModes: Set<String>,
    isLightMode: Boolean,
    onModeChange: (String?) -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val app = context.applicationContext as android.app.Application
    val chatViewModel = remember(app) { ChatViewModelHolder.get(app) }
    val isActive = activeMode != null
    val currentViewMode = when (activeMode) {
        "IDE" -> AppViewMode.IDE
        "TERMINAL" -> AppViewMode.TERMINAL
        "BROWSER" -> AppViewMode.BROWSER
        "NOTES" -> AppViewMode.NOTES
        else -> AppViewMode.CHAT
    }

    // System bars follow SAIF AI's background while a screen is open (SAIF AI restores its own when it closes).
    SideEffect {
        val window = (context as? android.app.Activity)?.window
        if (window != null && isActive && !view.isInEditMode) {
            val bar = if (currentViewMode == AppViewMode.TERMINAL) android.graphics.Color.TRANSPARENT
            else if (isLightMode) 0xFFFFFFFF.toInt() else 0xFF1F1E1D.toInt()
            window.statusBarColor = bar
            window.navigationBarColor = bar
            val controller = WindowCompat.getInsetsController(window, view)
            val lightIcons = if (currentViewMode == AppViewMode.TERMINAL) false else isLightMode
            controller.isAppearanceLightStatusBars = lightIcons
            controller.isAppearanceLightNavigationBars = lightIcons
        }
    }

    var showPermissionsDialog by remember {
        mutableStateOf(!com.example.gemini.ui.components.PermissionUtils.hasNotificationPermission(context))
    }
    var isInstalledState by remember {
        mutableStateOf(com.example.gemini.data.local.LocalEnvironmentManager.isInstalled(context))
    }
    var hasSkippedInstaller by rememberSaveable { mutableStateOf(false) }
    val showFullScreenInstaller = !isInstalledState && !hasSkippedInstaller
    val gated = showPermissionsDialog || showFullScreenInstaller

    GeminiTheme(darkTheme = !isLightMode, applySystemBars = false) {
        Surface(
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (isActive) 1f else 0f
                    translationX = if (isActive) 0f else 20000f
                },
            color = MaterialTheme.colorScheme.background
        ) {
            if (gated) {
                if (isActive) {
                    if (showPermissionsDialog) {
                        com.example.gemini.ui.components.AppPermissionsDialog(
                            onDismissOrCompleted = {
                                showPermissionsDialog = false
                                com.example.gemini.data.service.TermuxService.start(context)
                                com.example.gemini.data.local.LocalServerManager.autoStartOnAppLaunch(context)
                            }
                        )
                    } else {
                        BackHandler { hasSkippedInstaller = true }
                        val installerState by com.example.gemini.data.local.LocalEnvironmentManager.installerState.collectAsState()
                        LaunchedEffect(Unit) {
                            // The authenticated startup owns this job; don't launch a second extractor
                            // from the progress screen and reset files under the first one.
                            com.example.gemini.data.local.AuthenticatedWorkspace.ensureStarted(context.applicationContext)
                        }
                        LaunchedEffect(installerState) {
                            if (installerState is com.example.gemini.data.local.LocalInstallerState.Success) {
                                isInstalledState = true
                                hasSkippedInstaller = true
                            }
                        }
                        com.example.gemini.ui.settings.FullScreenLocalToolsInstaller(
                            authPreferences = chatViewModel.authPreferences,
                            onSkip = { hasSkippedInstaller = true },
                            onComplete = {
                                isInstalledState = true
                                hasSkippedInstaller = true
                                com.example.gemini.data.local.AuthenticatedWorkspace.ensureStarted(context.applicationContext)
                            }
                        )
                    }
                }
            } else {
                if (isActive) {
                    BackHandler(enabled = currentViewMode != AppViewMode.BROWSER) {
                        onModeChange(null)
                    }
                }

                val terminalSessions by com.example.gemini.data.local.LocalTerminalManager.sessions.collectAsState()
                val focusCoordinator = remember { com.example.gemini.ui.components.AppFocusCoordinator() }
                val density = LocalDensity.current
                val imeInsets = WindowInsets.ime
                var previousModeForFocus by remember { mutableStateOf(currentViewMode) }
                LaunchedEffect(currentViewMode) {
                    if (previousModeForFocus != currentViewMode) {
                        val isKeyboardOpen = imeInsets.getBottom(density) > 0
                        focusCoordinator.onScreenLeaving(previousModeForFocus, isKeyboardOpen)
                        focusCoordinator.onScreenEntering(currentViewMode, isKeyboardOpen)
                        previousModeForFocus = currentViewMode
                    }
                }

                val isFloatingSwitcherEnabled by chatViewModel.isFloatingSwitcherEnabled.collectAsState()
                val floatingSwitcherOrientation by chatViewModel.floatingSwitcherOrientation.collectAsState()
                val floatingSwitcherItems by chatViewModel.floatingSwitcherItems.collectAsState()
                val floatingSwitcherAutoCollapseSec by chatViewModel.floatingSwitcherAutoCollapseSec.collectAsState()
                val floatingSwitcherPosX by chatViewModel.floatingSwitcherPosX.collectAsState()
                val floatingSwitcherPosY by chatViewModel.floatingSwitcherPosY.collectAsState()
                val isFloatingDiagnosticsEnabled by chatViewModel.isFloatingDiagnosticsEnabled.collectAsState()
                val isNetworkInspectorEnabled by chatViewModel.isNetworkInspectorEnabled.collectAsState()
                val isFloatingNetworkInspectorEnabled by chatViewModel.isFloatingNetworkInspectorEnabled.collectAsState()
                var showNetworkInspectorDialog by remember { mutableStateOf(false) }

                CompositionLocalProvider(
                    com.example.gemini.ui.components.LocalAppFocusCoordinator provides focusCoordinator
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Code editor (keeps its open tabs and daemon connection while hidden)
                        if ("IDE" in openedModes) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val visible = currentViewMode == AppViewMode.IDE
                                        alpha = if (visible) 1f else 0f
                                        translationX = if (visible) 0f else 20000f
                                    }
                            ) {
                                IdeScreen(
                                    viewModel = chatViewModel,
                                    isVisible = currentViewMode == AppViewMode.IDE,
                                    onNavigateToChat = { onModeChange(null) },
                                    onExecuteRunCommand = { },
                                    onNavigateToTerminal = { onModeChange("TERMINAL") },
                                    onNavigateToBrowser = { onModeChange("BROWSER") }
                                )
                            }
                        }

                        // Linux terminal (keeps its sessions while hidden)
                        if ("TERMINAL" in openedModes && (currentViewMode == AppViewMode.TERMINAL || terminalSessions.isNotEmpty())) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val visible = currentViewMode == AppViewMode.TERMINAL
                                        alpha = if (visible) 1f else 0f
                                        translationX = if (visible) 0f else 20000f
                                    }
                            ) {
                                com.example.gemini.ui.components.LocalTerminalContent(
                                    onClose = { onModeChange(null) }
                                )
                            }
                        }

                        // Browser (keeps its tabs while hidden)
                        if ("BROWSER" in openedModes) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val visible = currentViewMode == AppViewMode.BROWSER
                                        alpha = if (visible) 1f else 0f
                                        translationX = if (visible) 0f else 20000f
                                    }
                            ) {
                                BrowserScreen(
                                    isVisible = currentViewMode == AppViewMode.BROWSER,
                                    onClose = { onModeChange(null) }
                                )
                            }
                        }

                        // Notes
                        if ("NOTES" in openedModes) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val visible = currentViewMode == AppViewMode.NOTES
                                        alpha = if (visible) 1f else 0f
                                        translationX = if (visible) 0f else 20000f
                                    }
                            ) {
                                com.example.gemini.ui.notes.NotesScreen(
                                    isVisible = currentViewMode == AppViewMode.NOTES,
                                    onClose = { onModeChange(null) }
                                )
                            }
                        }

                        if (isActive) {
                            com.example.gemini.ui.components.FloatingVideoPlayerOverlay()

                            if (isFloatingDiagnosticsEnabled) {
                                com.example.gemini.ui.components.FloatingDiagnosticsOverlay(
                                    onDismiss = { chatViewModel.setFloatingDiagnosticsEnabled(false) }
                                )
                            }

                            if (isNetworkInspectorEnabled && isFloatingNetworkInspectorEnabled) {
                                com.example.gemini.ui.components.FloatingNetworkInspectorBubble(
                                    onClick = { showNetworkInspectorDialog = true }
                                )
                            }

                            if (showNetworkInspectorDialog) {
                                com.example.gemini.ui.components.NetworkInspectorDialog(
                                    onDismiss = { showNetworkInspectorDialog = false }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
