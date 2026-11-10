/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.ui
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File


import com.example.ui.components.*
import com.example.data.local.ChatMessageEntity
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.blur
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.onGloballyPositioned
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SaifAiApp(
    viewModel: ChatViewModel,
    isLightMode: Boolean,
    onToggleTheme: () -> Unit,
    onOpenAdminDashboard: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val listState = rememberLazyListState()
    val allSessions by viewModel.allSessions.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val currentMode by viewModel.currentMode.collectAsStateWithLifecycle()
    val activeMessages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val bookmarkedMessages by viewModel.bookmarkedMessages.collectAsStateWithLifecycle()
    val isFilterBookmarked by viewModel.isFilterBookmarked.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val currentStreamingContent by viewModel.currentStreamingContent.collectAsStateWithLifecycle()
    val temperature by viewModel.temperature.collectAsStateWithLifecycle()
    val customSystemPrompt by viewModel.customSystemPrompt.collectAsStateWithLifecycle()
    var showModeSelector by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    // Keep the user's position in the long SAIF AI settings list while an engine detail is open.
    val settingsScrollState = rememberScrollState()
    val openSettingsRequest by com.example.HostNavigation.openSettingsRequest.collectAsState()
    var showVoiceDialog by remember { mutableStateOf(false) }
    var showUpgradeDialog by remember { mutableStateOf(false) }
    var showConfigureDialog by remember { mutableStateOf(false) }
    var activeProjectData by remember { mutableStateOf<com.example.ui.components.ProjectData?>(null) }
    var showProjectEditor by remember { mutableStateOf(false) }
    // Browser / terminal / code editor / notes shown inside SAIF AI ("IDE", "TERMINAL", "BROWSER", "NOTES" or null)
    var engineFeature by remember { mutableStateOf<String?>(null) }
    var showEngineSettings by remember { mutableStateOf(false) }
    var engineSettingsSection by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(openSettingsRequest) {
        if (openSettingsRequest) {
            showEngineSettings = false
            engineSettingsSection = null
            showSettings = true
            com.example.HostNavigation.openSettingsRequest.value = false
        }
    }
    var showConnectionDialog by remember { mutableStateOf(false) }
    var showGitHubDialog by remember { mutableStateOf(false) }
    var showSkillsManagerDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        com.example.github.GitHubIntegrationManager.init(context.applicationContext)
        // Slash commands must be ready before the user types the first "/": the user's own saved
        // commands are loaded here, and the built-in list is warmed from the hub in the background.
        runCatching { com.example.gemini.data.remote.CustomSlashCommands.init(context.applicationContext) }
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { com.example.gemini.data.remote.SlashCommandsCache.getCommands() }
        }
    }
    val gitHubState by com.example.github.GitHubIntegrationManager.state.collectAsState()
    val antigravityModels by viewModel.antigravityModels.collectAsState()
    val selectedAntigravityModel by viewModel.antigravitySelectedModel.collectAsState()
    val antigravityConnection by viewModel.antigravityConnection.collectAsState()
    val antigravityAuthInfo by viewModel.antigravityAuthInfo.collectAsState()
    val appUser by com.example.data.local.AuthManager.currentUser.collectAsState()
    val accountIssue = if (antigravityConnection.isReady) {
        com.example.gemini.data.remote.GoogleAccountBinding.denialReason(appUser, antigravityAuthInfo)
    } else null
    val antigravityQuotas by viewModel.antigravityQuotas.collectAsState()
    LaunchedEffect(accountIssue) {
        if (accountIssue != null) {
            engineFeature = null
            showEngineSettings = false
            showSettings = false
            showVoiceDialog = false
            if (com.jarvis.assistant.SaifAutomation.isLiveRunning.value)
                com.jarvis.assistant.SaifAutomation.stopLive(context.applicationContext)
        }
    }
    // The engine's own view model: SAIF's header strip / settings rows ask its workspace to open on a given view or settings section.
    val engineVm = remember { com.example.gemini.ui.chat.ChatViewModelHolder.get(context.applicationContext as android.app.Application) }
    val openEngineView: (String?) -> Unit = { mode ->
        when (mode) {
            "IDE" -> { /* Raw Editing Engine IDE page permanently disabled */ }
            "TERMINAL" -> {
                if (!com.example.gemini.data.local.LocalEnvironmentManager.hasVerifiedEnvironment(context)) {
                    android.widget.Toast.makeText(
                        context,
                        "Linux tools are installing in the background. You can keep chatting; terminal will open once setup completes.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                } else {
                    engineFeature = mode
                }
            }
            else -> engineFeature = mode
        }
    }
    val openEngineSettings: (String) -> Unit = { section ->
        when (section) {
            "SKILLS" -> showSkillsManagerDialog = true
            "GITHUB" -> showGitHubDialog = true
            "CONNECTION" -> showConnectionDialog = true
        }
    }
    // One permission path for both the header automation button and every chat microphone.
    val liveMicPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) com.example.ui.components.startNormal(context, isLightMode) {}
        else android.widget.Toast.makeText(context, "Microphone permission is required for live conversation", android.widget.Toast.LENGTH_LONG).show()
    }
    val startLiveVoice: () -> Unit = {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            liveMicPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        } else {
            com.example.ui.components.startNormal(context, isLightMode) {}
        }
    }
    val engineAuthBusy by engineVm.isAuthBusy.collectAsState()
    val engineNetworkOk by engineVm.isNetworkConnectedState.collectAsState()
    DisposableEffect(viewModel) {
        // Live voice delegates coding/file changes to the same Antigravity chat as typed messages.
        com.example.live.LiveCodingBridge.isReady = {
            viewModel.antigravityConnection.value.isReady &&
                com.example.gemini.data.remote.GoogleAccountBinding.denialReason(
                    com.example.data.local.AuthManager.currentUser.value, viewModel.antigravityAuthInfo.value
                ) == null
        }
        com.example.live.LiveCodingBridge.send = { text -> viewModel.sendLiveCodingTask(context.applicationContext, text) }
        onDispose {
            com.example.live.LiveCodingBridge.send = null
            com.example.live.LiveCodingBridge.isReady = null
        }
    }
    LaunchedEffect(Unit) {
        com.example.gemini.EngineBootstrap.ensureStarted(context.applicationContext as android.app.Application)
        engineVm.authFeedbackMessage.collect { msg ->
            android.widget.Toast.makeText(context, msg.toString(), android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    var autoApprove by remember { mutableStateOf(com.example.ui.components.SaifEngineSettings.isAutoApprove(context)) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var showModelMenu by remember { mutableStateOf(false) }
    var flowSetupBusy by remember { mutableStateOf(false) }
    val mainAgentMessages by viewModel.mainAgentMessages.collectAsState()
    val mainAgentSessionId by viewModel.mainAgentSessionId.collectAsState()
    val mainAgentApprovals by viewModel.mainAgentApprovals.collectAsState()
    var videoQuestionStep by remember { mutableIntStateOf(-1) }
    var videoAspect by remember { mutableStateOf("9:16") }
    var videoDuration by remember { mutableStateOf("") }
    var videoTopic by remember { mutableStateOf("") }
    var videoStyle by remember { mutableStateOf("") }
    // Environment installation and server startup are owned by AuthenticatedWorkspace after sign-in.

    // Video Project Timeline Studio states
    var showVideoProjectDialog by remember { mutableStateOf(false) }
    var showNewProjectSheet by remember { mutableStateOf(false) }
    // The purple mic starts a normal live conversation immediately. Screen share is optional INSIDE the session.

    /**
     * A MediaProjection can only be requested from a visible activity, so SAIF AI asks for it and then hands it to
     * the automation engine (`SaifAutomation.startScreenShare`). The engine never opens its own permission screen.
     */
    val screenCapturePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode != android.app.Activity.RESULT_OK || data == null) {
            com.example.ui.components.SaifLiveSession.pendingScreenShare = false
            android.widget.Toast.makeText(context, "Screen sharing needs your permission.", android.widget.Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        // Android 14+ requires a running media-projection foreground service BEFORE
        // getMediaProjection(). Starting the service is asynchronous, so await it.
        scope.launch {
            val automation = com.jarvis.assistant.SaifAutomation
            if (!automation.isLiveRunning.value) {
                com.example.util.WakeWordAudioDetector.stop()
                if (!automation.startLive(context, com.jarvis.assistant.SaifAutomation.LiveMode.SCREEN_SHARE, isLightMode)) {
                    if (com.example.util.WakeWordManager.isWakeWordEnabledFlow.value &&
                        com.example.util.WakeWordManager.isAppInForeground) {
                        com.example.util.WakeWordAudioDetector.start(context)
                    }
                    com.example.ui.components.SaifLiveSession.pendingScreenShare = false
                    return@launch
                }
            }
            val ready = kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                automation.isLiveRunning.first { it }
                true
            } ?: false
            if (!ready || !com.jarvis.assistant.background.JarvisConversationService.elevateToMediaProjection()) {
                com.example.ui.components.SaifLiveSession.pendingScreenShare = false
                android.widget.Toast.makeText(context, "Couldn't start the screen capture service.", android.widget.Toast.LENGTH_LONG).show()
                return@launch
            }
            val mpm = context.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
            val projection = try { mpm.getMediaProjection(result.resultCode, data) } catch (_: Exception) { null }
            if (projection == null) {
                com.jarvis.assistant.background.JarvisConversationService.demoteFromMediaProjection()
                com.example.ui.components.SaifLiveSession.pendingScreenShare = false
                android.widget.Toast.makeText(context, "Screen capture access was not granted.", android.widget.Toast.LENGTH_LONG).show()
                return@launch
            }
            val metrics = android.util.DisplayMetrics().also { dm ->
                @Suppress("DEPRECATION")
                (context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.getRealMetrics(dm)
            }
            val started = automation.startScreenShare(projection, metrics)
            if (started) automation.liveMode.value = com.jarvis.assistant.SaifAutomation.LiveMode.SCREEN_SHARE
            else {
                com.jarvis.assistant.background.JarvisConversationService.demoteFromMediaProjection()
                runCatching { projection.stop() }
            }
            com.example.ui.components.SaifLiveSession.pendingScreenShare = false
            android.widget.Toast.makeText(
                context,
                if (started) "Screen sharing started." else "Couldn't start screen sharing.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            if (started) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    val reason = automation.lastScreenShareError
                    if (reason.isNotBlank()) {
                        android.widget.Toast.makeText(context, "Screen share: $reason", android.widget.Toast.LENGTH_LONG).show()
                    }
                }, 9_000L)
            }
        }
    }
    var activeVideoProjectData by remember { mutableStateOf<com.example.ui.components.VideoProjectData?>(null) }
    var showVideoStudio by remember { mutableStateOf(false) }
    var pendingVideoProjectName by remember { mutableStateOf("Cyberpunk Tokyo Vlog") }

    val pendingOpenVideoProject by viewModel.pendingOpenVideoProject.collectAsStateWithLifecycle()
    LaunchedEffect(pendingOpenVideoProject) {
        pendingOpenVideoProject?.let { proj ->
            activeVideoProjectData = proj
            showVideoStudio = true
            viewModel.consumePendingVideoProject()
        }
    }

    // Multi-media gallery picker for 50+ video & photos
    val videoProjectMediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(100)
    ) { uris: List<Uri> ->
        val clips = if (uris.isNotEmpty()) {
            uris.mapIndexed { idx, uri ->
                val name = uri.lastPathSegment?.substringAfterLast('/')?.take(18) ?: "Clip_${idx + 1}"
                com.example.ui.components.TimelineMediaClip(
                    name = name,
                    uri = uri,
                    durationSeconds = 8.0f,
                    isAiEnhanced = false,
                    isPhoto = false
                )
            }
        } else {
            // Clean empty timeline if user canceled gallery selection
            emptyList()
        }

        val videoProj = com.example.ui.components.VideoProjectData(
            projectName = com.example.util.ProjectDataManager.uniqueVideoProjectName(
                context, pendingVideoProjectName.ifBlank { "New Video Project" }
            ),
            clips = clips
        )
        activeVideoProjectData = videoProj
        showVideoStudio = true
        viewModel.createVideoProjectSession(videoProj, context)
        android.widget.Toast.makeText(
            context,
            "Loaded ${clips.size} clips into Timeline Studio!",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
    val displayedMessages = if (isFilterBookmarked) bookmarkedMessages else activeMessages
    val timeline = remember(displayedMessages, mainAgentMessages, mainAgentSessionId, activeSessionId, isFilterBookmarked) {
        val nativeMessages = if (!isFilterBookmarked && activeSessionId != null && mainAgentSessionId == activeSessionId) mainAgentMessages else emptyList()
        homeAgentTimeline(displayedMessages, nativeMessages)
    }
    val darkBg = Color(0xFF1F1E1D)
    val lightBg = Color(0xFFFAF9F5)
    val revealProgress = remember { Animatable(1f) }
    var displayedBg by remember { mutableStateOf(if (isLightMode) lightBg else darkBg) }
    var upcomingBg by remember { mutableStateOf(if (isLightMode) lightBg else darkBg) }
    var isVoiceListening by remember { mutableStateOf(false) }
    var voiceRms by remember { mutableStateOf(0f) }
    var stopVoiceListeningTrigger by remember { mutableIntStateOf(0) }
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }

    val isAssistantOverlayActive by com.example.util.WakeWordManager.isAssistantOverlayActive.collectAsState()
    val cornerGlowTrigger by com.example.util.WakeWordManager.cornerGlowTrigger.collectAsState()
    val initialSpokenQuery by com.example.util.WakeWordManager.initialSpokenQuery.collectAsState()
    val isWakeWordEnabled by com.example.util.WakeWordManager.isWakeWordEnabledFlow.collectAsState()
    val isAutomationLive by com.jarvis.assistant.SaifAutomation.isLiveRunning.collectAsState()

    val isHomeScreen = !showProjectEditor && !showSettings && !showConfigureDialog &&
        !showUpgradeDialog && !showModeSelector && !isAutomationLive

    val appContext = LocalContext.current.applicationContext
    DisposableEffect(isHomeScreen, isWakeWordEnabled, isAssistantOverlayActive) {
        val hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (isHomeScreen && isWakeWordEnabled && !isAssistantOverlayActive && hasMicPermission) {
            com.example.util.WakeWordAudioDetector.start(appContext)
        } else {
            com.example.util.WakeWordAudioDetector.stop()
        }

        onDispose {
            com.example.util.WakeWordAudioDetector.stop()
        }
    }

    LaunchedEffect(isLightMode) {
        val newBg = if (isLightMode) lightBg else darkBg
        if (newBg != upcomingBg) {
            displayedBg = upcomingBg
            upcomingBg = newBg
            revealProgress.snapTo(0f)
            revealProgress.animateTo(1f, animationSpec = tween(500, easing = FastOutSlowInEasing))
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        // Theme Reveal Background Canvas
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = displayedBg)
            val radius = size.height * 1.5f * revealProgress.value
            if (radius > 0f) {
                drawCircle(
                    color = upcomingBg,
                    radius = radius,
                    center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height)
                )
            }
        }

        // Subtle liquid glass atmospheric background effect
        com.example.ui.components.LiquidGlassBackground(
            isLightMode = isLightMode,
            modifier = Modifier.fillMaxSize()
        )

        // Gemini-style Ambient Atmospheric Voice Glow & Shifting Aurora Wave
        com.example.ui.components.GeminiAmbientVoiceGlow(
            isActive = isVoiceListening,
            rmsValue = voiceRms
        )

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color.Transparent,
                windowInsets = WindowInsets(0, 0, 0, 0)
            ) {
                SessionDrawerContent(
                    sessions = allSessions,
                    activeSessionId = activeSessionId,
                    drawerState = drawerState,
                    onSelectSession = { id ->
                        viewModel.selectSession(id)
                        val target = allSessions.firstOrNull { it.id == id }
                        val isVideoProj = target != null && (target.mode == "video_project" || com.example.util.ProjectDataManager.hasVideoProject(context, target.title))
                        val isProj = target != null && (target.mode == "project" || com.example.util.ProjectDataManager.hasProject(context, target.title))
                        if (isVideoProj) {
                            val savedVideo = com.example.util.ProjectDataManager.getVideoProject(context, target!!.title)
                                ?: com.example.ui.components.VideoProjectData(projectName = target.title)
                            activeVideoProjectData = savedVideo
                            showVideoStudio = true
                        } else if (isProj) {
                            val savedProj = com.example.util.ProjectDataManager.getProject(context, target!!.title)
                                ?: com.example.ui.components.ProjectData(
                                    appName = target.title,
                                    packageName = "com.example.${target.title.lowercase().replace(Regex("[^a-z0-9]"), "")}",
                                    minSdk = 21,
                                    targetSdk = 34,
                                    buildStudio = "SAIF AI Studio",
                                    language = "Java",
                                    iconBitmap = com.example.util.ProjectDataManager.loadProjectIcon(context, target.title)
                                )
                            activeProjectData = savedProj
                            showProjectEditor = true
                        }
                        scope.launch { drawerState.close() }
                    },
                    onNewChat = {
                        viewModel.createNewSession()
                        scope.launch { drawerState.close() }
                    },
                    onRenameSession = { id, title -> viewModel.renameSession(id, title, context) },
                    onDeleteSession = { id -> viewModel.deleteSession(id, context) },
                    onClearAll = {
                        viewModel.clearAllSessions()
                        scope.launch { drawerState.close() }
                    },
                    onOpenAdminDashboard = onOpenAdminDashboard,
                    engineAuthInfo = antigravityAuthInfo,
                    engineStatusColor = antigravityConnection.dotColor,
                    onExitApp = { showExitConfirm = true },
                    onOpenFileManager = { scope.launch { drawerState.close() }; openEngineView("IDE") },
                    onOpenSettings = { scope.launch { drawerState.close() }; showSettings = true },
                    onOpenEngineAccount = { scope.launch { drawerState.close() }; showConnectionDialog = true }
                )
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    SaifHeader(
                        currentMode = currentMode,
                        isLightMode = isLightMode,
                        onToggleTheme = onToggleTheme,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onOpenModeSelector = { showModeSelector = true },
                        onNewChat = { viewModel.createNewSession() },
                        onOpenSettings = { showSettings = true },
                        onPlusClick = { showConfigureDialog = true },
                        onNewVideoProjectClick = { showNewProjectSheet = true },
                        onAutomationClick = startLiveVoice,
                        isFilterBookmarked = isFilterBookmarked,
                        onToggleBookmarkFilter = { viewModel.toggleBookmarkFilter() },
                        onUpgradeClick = { showUpgradeDialog = true },
                        onGitHubClick = { showGitHubDialog = true },
                        isGitHubConnected = gitHubState.isConnected,
                        onOpenAdminDashboard = onOpenAdminDashboard,
                        engineBar = {
                            com.example.ui.components.SaifEngineToolbar(
                                statusColor = antigravityConnection.dotColor,
                                onStatusClick = { showConnectionDialog = true },
                                onBrowserClick = { openEngineView("BROWSER") },
                                onTerminalClick = { openEngineView("TERMINAL") },
                                onCodeClick = {}
                            )
                        }
                    )
                },
                bottomBar = {
                    val isImeVisible = androidx.compose.foundation.layout.WindowInsets.isImeVisible
                    val extraBottomPadding by androidx.compose.animation.core.animateDpAsState(
                        targetValue = if (isImeVisible) 4.dp else 10.dp,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = 0.6f, // Medium bouncy
                            stiffness = 300f // Nice and smooth
                        ),
                        label = "ImeExtraPadding"
                    )
                    // Explicit navigationBarsPadding keeps chatbox comfortably separated from system nav bar
                    // imePadding smoothly lifts above soft keyboard when typing
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(bottom = extraBottomPadding.coerceAtLeast(0.dp))
                            .onGloballyPositioned { coordinates ->
                                bottomBarHeightPx = coordinates.size.height
                            }
                    ) {
                    AnimatedVisibility(
                        visible = !showSettings && !showModeSelector,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        val selectedAttachments by viewModel.selectedAttachments.collectAsState()
                        val galleryLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.PickMultipleVisualMedia(8)
                        ) { uris ->
                            if (uris.isNotEmpty()) {
                                viewModel.addAttachments(uris)
                            }
                        }
                        val fileLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.OpenMultipleDocuments()
                        ) { uris ->
                            if (uris.isNotEmpty()) {
                                viewModel.addAttachments(uris)
                            }
                        }

                        val videoLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.PickVisualMedia()
                        ) { uri ->
                            uri?.let { viewModel.addAttachments(listOf(it)) }
                        }
                        
                        var tempCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
                        val context = LocalContext.current
                        val cameraLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.TakePicture()
                        ) { success ->
                            if (success) {
                                tempCameraUri?.let { viewModel.addAttachments(listOf(it)) }
                            }
                        }

                        val cameraPermissionLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.RequestPermission()
                        ) { isGranted ->
                            if (isGranted) {
                                try {
                                    val photoFile = java.io.File(context.cacheDir, "camera").apply { mkdirs() }.let {
                                        java.io.File.createTempFile("JPEG_${System.currentTimeMillis()}_", ".jpg", it)
                                    }
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        photoFile
                                    )
                                    tempCameraUri = uri
                                    cameraLauncher.launch(uri)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Camera open karne me samasya: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                android.widget.Toast.makeText(context, "Camera permission is required to take photos", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Attachments Display
                            if (selectedAttachments.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                selectedAttachments.forEach { uri ->
                                    val fileInfo = remember(uri) {
                                        runCatching { com.example.cloud.PrivateFileTypes.inspect(context, uri) }.getOrNull()
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(70.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(com.example.ui.theme.SaifTheme.colors.surfaceCard)
                                    ) {
                                        if (fileInfo?.mimeType?.startsWith("image/") == true) {
                                            AsyncImage(model = uri, contentDescription = fileInfo?.name ?: "Image",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.matchParentSize())
                                        } else {
                                            Column(Modifier.align(Alignment.Center).padding(4.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(Icons.Default.InsertDriveFile, contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary)
                                                Text(fileInfo?.name?.take(12) ?: "File", maxLines = 1,
                                                    fontSize = 9.sp, color = com.example.ui.theme.SaifTheme.colors.textPrimary)
                                            }
                                        }
                                        IconButton(
                                            onClick = { viewModel.removeAttachment(uri) },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(20.dp)
                                                .padding(2.dp)
                                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (isGenerating) {
                            val approvals = com.example.gemini.ui.agents.agyApprovalItems(
                                mainAgentApprovals,
                                onApprove = { tool, id, approvalScope -> viewModel.approveMainTool(tool, id, approvalScope) },
                                onReject = { tool, id, reason -> viewModel.rejectMainTool(tool, id, reason) }
                            )
                            if (approvals.isNotEmpty()) {
                                com.example.gemini.ui.agents.AgentApprovalPanel(
                                    items = approvals,
                                    onAllowAll = { viewModel.approveAllMainTools(mainAgentApprovals) },
                                    onDenyAll = { viewModel.rejectAllMainTools(mainAgentApprovals) }
                                )
                            } else {
                                val question = com.example.gemini.ui.agents.findPendingQuestion(com.example.gemini.domain.model.AgentKind.AGY, mainAgentMessages)
                                if (question != null) {
                                    com.example.gemini.ui.agents.AgentQuestionPanel(
                                        question = question,
                                        onSubmit = { responses, summary -> viewModel.submitMainChoices(question.toolCall, question.messageId, responses, summary) },
                                        onSkip = { responses -> viewModel.skipMainChoices(question.toolCall, question.messageId, responses) },
                                        onCancel = { viewModel.cancelMainChoices(question.toolCall, question.messageId) }
                                    )
                                }
                            }
                        }
                        SaifInputBar(
                            input = input,
                            onInputChange = { viewModel.onInputChange(it) },
                            isGenerating = isGenerating,
                            hasAttachments = selectedAttachments.isNotEmpty(),
                            onSend = {
                                if (input.trim().equals("/video", ignoreCase = true)) {
                                    viewModel.onInputChange("")
                                    videoQuestionStep = 0
                                } else viewModel.sendMessage(context)
                            },
                            placeholderText = "REPLY TO SAIF AI",
                            modelSelector = {
                                Box {
                                    val chipLabel = antigravityModels.firstOrNull { it.id == selectedAntigravityModel }?.displayName
                                        ?: "Model"
                                    val chipQuota = antigravityQuotas.firstOrNull { it.modelId == selectedAntigravityModel }
                                        ?.remainingFraction?.let { (it * 100f).toInt().coerceIn(0, 100) }
                                    com.example.ui.components.SaifModelChip(
                                        label = chipLabel,
                                        quotaPercent = chipQuota,
                                        enabled = !isGenerating,
                                        onClick = { showModelMenu = true }
                                    )
                                    DropdownMenu(expanded = showModelMenu, onDismissRequest = { showModelMenu = false }) {
                                        if (!antigravityConnection.isReady) {
                                            DropdownMenuItem(text = { Text("Connect Antigravity") }, onClick = {
                                                showModelMenu = false; showConnectionDialog = true
                                            })
                                        }
                                        DropdownMenuItem(text = { Text(if (flowSetupBusy) "Connecting Flow…" else "Connect Google Flow") },
                                            enabled = !flowSetupBusy && antigravityConnection.isReady, onClick = {
                                                showModelMenu = false
                                                flowSetupBusy = true
                                                scope.launch {
                                                    try {
                                                        val message = viewModel.connectGoogleFlow(context)
                                                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                                                    } catch (e: Exception) {
                                                        android.widget.Toast.makeText(context, "Flow setup failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                                                    } finally { flowSetupBusy = false }
                                                }
                                            })
                                        antigravityModels.forEach { model ->
                                            DropdownMenuItem(text = { Text(model.displayName) }, onClick = {
                                                viewModel.selectAntigravityModel(model.id); showModelMenu = false
                                            })
                                        }
                                    }
                                }
                            },
                            onStop = { viewModel.stopGeneration() },
                            onVoiceRecorded = { file -> viewModel.processAudioToText(context, file) },
                            onVoiceListeningStateChange = { listening, rms ->
                                isVoiceListening = listening
                                voiceRms = rms
                            },
                            stopVoiceListeningTrigger = stopVoiceListeningTrigger,
                            onStartLiveVoice = startLiveVoice,
                            onGalleryClick = { 
                                galleryLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                ) 
                            },
                            onFileClick = { 
                                fileLauncher.launch(arrayOf("*/*")) 
                            },
                            onVideoClick = { 
                                videoLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.VideoOnly
                                    )
                                ) 
                            },
                            onCameraClick = {
                                val hasCamPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.CAMERA
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                                if (hasCamPerm) {
                                    try {
                                        val photoFile = java.io.File(context.cacheDir, "camera").apply { mkdirs() }.let {
                                            java.io.File.createTempFile("JPEG_${System.currentTimeMillis()}_", ".jpg", it)
                                        }
                                        val uri = androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            photoFile
                                        )
                                        tempCameraUri = uri
                                        cameraLauncher.launch(uri)
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "Camera open karne me samasya: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                }
                            },
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        }
                    }
                }
            },
            modifier = modifier.fillMaxSize()
        ) { paddingValues ->
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Inner box for the main chat content with proper padding
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    if (displayedMessages.isEmpty() && !isGenerating) {
                        // Empty Welcome State with Hero & Quick Chips
                        SaifEmptyState(
                            status = antigravityConnection.status,
                            isNetworkConnected = engineNetworkOk,
                            isAuthBusy = engineAuthBusy,
                            onSignIn = { engineVm.loginToAgyHub(force = true) },
                            onRetry = {
                                engineVm.retryConnections()
                                com.example.gemini.data.local.LocalServerManager.restartServer(context.applicationContext)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        HomeAgentFeed(
                            messages = timeline,
                            modelId = selectedAntigravityModel,
                            generating = isGenerating,
                            listState = listState,
                            viewModel = viewModel,
                            onRetry = { viewModel.regenerateLastResponse(context) }
                        )
                    }
                }
                
                
                // Content-area tap dismissal when mic is listening
                if (isVoiceListening) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = paddingValues.calculateBottomPadding())
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        stopVoiceListeningTrigger++
                                    }
                                )
                            }
                    )
                }


            }
        } // Close Scaffold

        // Global unwanted touch dismissal: When mic is actively listening,
        // tapping ANY unwanted area outside the input bar stops the mic!
        if (isVoiceListening) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val bottomPadding = if (bottomBarHeightPx > 0) {
                with(density) { bottomBarHeightPx.toDp() }
            } else {
                96.dp
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = bottomPadding)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                stopVoiceListeningTrigger++
                            }
                        )
                    }
            )
        }
    } // Close Box wrapping Scaffold
        // Mode Selector Sheet
        if (showModeSelector) {
            ModeSelectorSheet(
                currentMode = currentMode,
                onSelectMode = { newMode -> viewModel.setMode(newMode) },
                onDismiss = { showModeSelector = false }
            )
        }
        // Settings Bottom Sheet
        if (showSettings) {
            SettingsSheet(
                temperature = temperature,
                customSystemPrompt = customSystemPrompt,
                onSaveSettings = { temp, prompt -> viewModel.updateSettings(temp, prompt) },
                onClearCurrentChat = { viewModel.clearCurrentSessionMessages() },
                onDismiss = { showSettings = false },
                onOpenAdminDashboard = onOpenAdminDashboard,
                onOpenEngineSettings = openEngineSettings,
                onOpenTerminal = { openEngineView("TERMINAL") },
                autoApprove = autoApprove,
                onAutoApproveChange = {
                    autoApprove = it
                    com.example.ui.components.SaifEngineSettings.setAutoApprove(context, it)
                },
                scrollState = settingsScrollState,
                engineAuthInfo = antigravityAuthInfo
            )
        }
        // Upgrade Premium Dialog (Overlay)
        AnimatedVisibility(
            visible = showUpgradeDialog,
            enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.85f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.85f)
        ) {
            com.example.ui.components.UpgradeDialog(
                onDismiss = { showUpgradeDialog = false }
            )
        }
        
        AnimatedVisibility(
            visible = showConfigureDialog,
            enter = fadeIn(tween(300)) + scaleIn(initialScale = 0.85f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.85f)
        ) {
            com.example.ui.components.ConfigureProjectDialog(
                isDarkTheme = !isLightMode,
                onDismiss = { showConfigureDialog = false },
                onCreateProject = { appName, packageName, minSdk, targetSdk, buildStudio, language, iconBitmap ->
                    showConfigureDialog = false
                    val projectData = com.example.ui.components.ProjectData(
                        appName = appName,
                        packageName = packageName,
                        minSdk = minSdk,
                        targetSdk = targetSdk,
                        buildStudio = buildStudio,
                        language = language,
                        iconBitmap = iconBitmap
                    )
                    activeProjectData = projectData
                    showProjectEditor = true
                    android.widget.Toast.makeText(
                        context,
                        "Opening Code Studio for '$appName'...",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    viewModel.createProjectSession(
                        appName = appName,
                        packageName = packageName,
                        minSdk = minSdk,
                        targetSdk = targetSdk,
                        buildStudio = buildStudio,
                        language = language,
                        iconBitmap = iconBitmap,
                        context = context
                    )
                }
            )
        }
        } // Close ModalNavigationDrawer

        // Floating "Open Code Studio" Quick Action when active project exists
        AnimatedVisibility(
            visible = activeProjectData != null && !showProjectEditor && !showConfigureDialog,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -30 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -30 }),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 58.dp, end = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF00A86B),
                shadowElevation = 6.dp,
                modifier = Modifier.clickable { showProjectEditor = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Open Studio",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Fullscreen Animated Code Studio Editor matching user's design
        AnimatedVisibility(
            visible = showProjectEditor && activeProjectData != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(tween(300)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(250)
            ) + fadeOut(tween(200))
        ) {
            activeProjectData?.let { project ->
                com.example.ui.components.ProjectEditorScreen(
                    project = project,
                    isDarkTheme = !isLightMode,
                    onClose = { showProjectEditor = false },
                    onUpdateProject = { updated ->
                        activeProjectData = updated
                        val currId = activeSessionId
                        if (currId != null && updated.appName != project.appName) {
                            viewModel.renameSession(currId, updated.appName)
                        }
                    }
                )
            }
        }

        // The purple "+" sheet: every way to start a project, plus recent projects
        if (showNewProjectSheet) {
            com.example.ui.components.NewProjectSheet(
                onDismiss = { showNewProjectSheet = false },
                onOpenRecent = { name ->
                    showNewProjectSheet = false
                    val existing = com.example.util.ProjectDataManager.getVideoProject(context, name)
                        ?: com.example.ui.components.VideoProjectData(projectName = name)
                    com.example.ui.components.RecentProjects.touch(context, existing.projectName)
                    activeVideoProjectData = existing
                    showVideoStudio = true
                    viewModel.createVideoProjectSession(existing, context)
                },
                onChoice = { choice ->
                    showNewProjectSheet = false
                    if (choice == com.example.ui.components.NewProjectChoice.MORE) {
                        showVideoProjectDialog = true
                    } else if (claimCreateTap()) {
                        val (base, wf) = when (choice) {
                            com.example.ui.components.NewProjectChoice.AI_VIDEO -> "AI Video" to com.example.studio.director.DirectorWorkflow.ZERO_TO_VIDEO
                            com.example.ui.components.NewProjectChoice.EDIT_WITH_AI -> "AI Edit" to com.example.studio.director.DirectorWorkflow.VIDEO_TO_EDITING
                            com.example.ui.components.NewProjectChoice.AI_VOICE_ON_VIDEO -> "AI Voice Video" to com.example.studio.director.DirectorWorkflow.VIDEO_EDIT_WITH_VOICE
                            com.example.ui.components.NewProjectChoice.VOICE_OVER_TO_VIDEO -> "Voice-over Video" to com.example.studio.director.DirectorWorkflow.VOICE_OVER_TO_VIDEO
                            else -> "Untitled Video" to null
                        }
                        val proj = com.example.ui.components.VideoProjectData(
                            projectName = com.example.util.ProjectDataManager.uniqueVideoProjectName(context, base),
                            clips = emptyList()
                        )
                        com.example.studio.ui.StudioLaunch.pending = wf
                        com.example.ui.components.RecentProjects.touch(context, proj.projectName)
                        activeVideoProjectData = proj
                        showVideoStudio = true
                        viewModel.createVideoProjectSession(proj, context)
                    }
                }
            )
        }

        // Video Project Creation Choice Dialog (With Video / Without Video)
        if (showVideoProjectDialog) {
            com.example.ui.components.VideoProjectCreateDialog(
                isDarkTheme = !isLightMode,
                onDismiss = { showVideoProjectDialog = false },
                onSelectWithMedia = { projectName ->
                    if (!claimCreateTap()) return@VideoProjectCreateDialog
                    showVideoProjectDialog = false
                    pendingVideoProjectName = projectName
                    try {
                        videoProjectMediaPicker.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageAndVideo
                            )
                        )
                    } catch (e: Exception) {
                        // Fallback to standard timeline
                        val fallbackProj = com.example.ui.components.VideoProjectData(
                            projectName = com.example.util.ProjectDataManager.uniqueVideoProjectName(context, projectName)
                        )
                        activeVideoProjectData = fallbackProj
                        showVideoStudio = true
                        viewModel.createVideoProjectSession(fallbackProj, context)
                    }
                },
                onSelectWithoutMedia = { projectName ->
                    if (!claimCreateTap()) return@VideoProjectCreateDialog
                    showVideoProjectDialog = false
                    val blankProj = com.example.ui.components.VideoProjectData(
                        projectName = com.example.util.ProjectDataManager.uniqueVideoProjectName(context, projectName),
                        clips = emptyList()
                    )
                    activeVideoProjectData = blankProj
                    showVideoStudio = true
                    viewModel.createVideoProjectSession(blankProj, context)
                    android.widget.Toast.makeText(
                        context,
                        "Opened blank timeline canvas",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                },
                onOpenProject = { projectName ->
                    showVideoProjectDialog = false
                    val existing = com.example.util.ProjectDataManager.getVideoProject(context, projectName)
                        ?: com.example.ui.components.VideoProjectData(projectName = projectName)
                    activeVideoProjectData = existing
                    showVideoStudio = true
                    viewModel.createVideoProjectSession(existing, context)
                },
                onCreateFromPrompt = { userPrompt ->
                    showVideoProjectDialog = false
                    val cleanTitle = com.example.util.ProjectDataManager.uniqueVideoProjectName(
                        context, userPrompt.take(28).trim().ifBlank { "AI Video Project" }
                    )
                    val initialProj = com.example.ui.components.VideoProjectData(
                        projectName = cleanTitle,
                        textOverlay = cleanTitle.uppercase(),
                        clips = emptyList()
                    )
                    activeVideoProjectData = initialProj
                    showVideoStudio = true
                    viewModel.createVideoProjectSession(initialProj, context)

                    android.widget.Toast.makeText(
                        context,
                        "Autonomous AI Director: Researching & assembling '$cleanTitle'...",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()

                    scope.launch {
                        val result = com.example.ui.components.SaifAiEditingEngine.generateAutonomousDirectorEdl(
                            context = context,
                            prompt = userPrompt,
                            rawVideoUrl = null
                        )
                        result.onSuccess { (_, proj) ->
                            activeVideoProjectData = proj
                            com.example.util.ProjectDataManager.saveVideoProject(context, proj)
                            viewModel.createVideoProjectSession(proj, context)
                            android.widget.Toast.makeText(
                                context,
                                "✅ Video assembled from prompt with Telegram assets!",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }.onFailure { err ->
                            // Algorithmic synthesis fallback: guarantees project is never blank or failing
                            val catalog = com.example.ui.components.TelegramAssetManager.loadMasterCatalog(context)
                            val fallbackEdl = com.example.ui.components.SaifAiEditingEngine.synthesizeAlgorithmicEdl(userPrompt, catalog)
                            val fallbackProj = com.example.ui.components.SaifAiEditingEngine.convertEdlToProject(context, fallbackEdl, userPrompt)
                            activeVideoProjectData = fallbackProj
                            com.example.util.ProjectDataManager.saveVideoProject(context, fallbackProj)
                            viewModel.createVideoProjectSession(fallbackProj, context)
                            android.widget.Toast.makeText(
                                context,
                                "Video assembled via Autonomous Director engine!",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            )
        }

        // Fullscreen Video Timeline Studio Screen (with SAIF AI EDITING chat & Export studio)
        AnimatedVisibility(
            visible = showVideoStudio && activeVideoProjectData != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(tween(300)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(250)
            ) + fadeOut(tween(200))
        ) {
            activeVideoProjectData?.let { videoProject ->
                // Keyed by the permanent project id (never the name). A saved studio project, including all AI work,
                // is loaded first; legacy migration happens only when nothing was saved yet.
                val studioProject = remember(videoProject.projectId) {
                    com.example.studio.model.StudioProjectResolver.resolve(context, videoProject)
                }
                val studioViewModel = remember(studioProject.id) {
                    com.example.studio.state.StudioViewModel(context, studioProject)
                }
                com.example.studio.ui.StudioScreen(
                    viewModel = studioViewModel,
                    onBack = { showVideoStudio = false },
                    onExportClick = {
                        android.widget.Toast.makeText(context, "Full Video Export unlocks in Level 2", android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // Screen 4-Corner & Perimeter Aura Glow (Activates on Wake Word)
        com.example.ui.components.GeminiCornerGlow(
            triggerTimestamp = cornerGlowTrigger
        )

        // Gemini Floating Assistant Bottom Sheet
        if (isAssistantOverlayActive) {
            com.example.ui.components.GeminiAssistantSheet(
                initialPrompt = initialSpokenQuery,
                onDismiss = {
                    com.example.util.WakeWordManager.dismissAssistant()
                }
            )
        }

        if (videoQuestionStep >= 0) {
            AlertDialog(
                onDismissRequest = { videoQuestionStep = -1 },
                title = { Text(when (videoQuestionStep) { 0 -> "Video aspect ratio"; 1 -> "Video duration (minutes)"; 2 -> "Video topic"; else -> "Style and other details" }) },
                text = {
                    when (videoQuestionStep) {
                        0 -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("9:16", "16:9", "1:1").forEach { ratio ->
                                TextButton(onClick = { videoAspect = ratio }) { Text(if (videoAspect == ratio) "✓ $ratio" else ratio) }
                            }
                        }
                        1 -> OutlinedTextField(value = videoDuration, onValueChange = { videoDuration = it }, label = { Text("Minutes") }, singleLine = true)
                        2 -> OutlinedTextField(value = videoTopic, onValueChange = { videoTopic = it }, label = { Text("Topic") })
                        else -> OutlinedTextField(value = videoStyle, onValueChange = { videoStyle = it }, label = { Text("Style, audience, language, voice…") })
                    }
                },
                confirmButton = { TextButton(onClick = {
                    if (videoQuestionStep < 3) videoQuestionStep++ else {
                        val request = "Render an MP4 video with aspect ratio $videoAspect, duration $videoDuration minutes, topic: $videoTopic. Style: $videoStyle."
                        videoQuestionStep = -1
                        scope.launch {
                            try { com.example.cloud.SaifAiCloudManager.requestBuild(context, request, com.example.cloud.CloudRequestFilter.Route.VIDEO) }
                            catch (e: Exception) { android.widget.Toast.makeText(context, e.localizedMessage ?: "Cloud video render unavailable", android.widget.Toast.LENGTH_LONG).show() }
                        }
                    }
                }, enabled = when (videoQuestionStep) { 1 -> videoDuration.toDoubleOrNull()?.let { it > 0 && it <= 180 } == true; 2 -> videoTopic.isNotBlank(); else -> true }) { Text(if (videoQuestionStep < 3) "Next" else "Create") } },
                dismissButton = { TextButton(onClick = { if (videoQuestionStep > 0) videoQuestionStep-- else videoQuestionStep = -1 }) { Text(if (videoQuestionStep > 0) "Back" else "Cancel") } }
            )
        }

        // On-demand Permission Request Dialog for voice features (Calls, Contacts, SMS, Accessibility, Notifications)
        com.example.ui.components.OnDemandPermissionDialog()

        // Auto approve: when ON, commands the agent wants to run are allowed without asking.
        androidx.compose.runtime.LaunchedEffect(autoApprove, mainAgentApprovals) {
            if (autoApprove && mainAgentApprovals.isNotEmpty()) {
                try { viewModel.approveAllMainTools(mainAgentApprovals) } catch (_: Exception) { }
            }
        }

        // Keep the system bars transparent and the window the same colour as SAIF's own background, so no black
        // strip shows above or below (the engine workspace used to leave its own bar colours behind).
        val barView = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.SideEffect {
            val window = (context as? android.app.Activity)?.window
            if (window != null && engineFeature == null) {
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    window.isNavigationBarContrastEnforced = false
                    window.isStatusBarContrastEnforced = false
                }
                window.setBackgroundDrawable(
                    android.graphics.drawable.ColorDrawable(if (isLightMode) 0xFFFFFFFF.toInt() else 0xFF1F1E1D.toInt())
                )
                val controller = androidx.core.view.WindowCompat.getInsetsController(window, barView)
                controller.isAppearanceLightStatusBars = isLightMode
                controller.isAppearanceLightNavigationBars = isLightMode
            }
        }

        if (showExitConfirm) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showExitConfirm = false },
                title = { Text("Exit SAIF AI?") },
                text = { Text("The app will close. Anything the agent is running in the background may stop.") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        showExitConfirm = false
                        (context as? android.app.Activity)?.finishAffinity()
                    }) { Text("Exit") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showExitConfirm = false }) { Text("Cancel") }
                }
            )
        }

        // Browser / terminal inside SAIF AI (IDE & Advanced Editing Engine screens permanently disabled)
        if (engineFeature != null) {
            androidx.activity.compose.BackHandler { engineFeature = null }
        }
        com.example.gemini.EmbeddedEngineFeatures(
            activeMode = engineFeature,
            isLightMode = isLightMode,
            onModeChange = openEngineView
        )

        if (showGitHubDialog) {
            com.example.ui.components.GitHubConnectDialog(
                isLightMode = isLightMode,
                onDismiss = { showGitHubDialog = false },
                onOpenRepoInChat = { repoFullName, linuxPath ->
                    viewModel.onInputChange("Inspect my GitHub repository $repoFullName cloned at $linuxPath and tell me its structure.")
                }
            )
        }

        if (showSkillsManagerDialog) {
            com.example.ui.components.SaifSkillsManagerDialog(
                isLightMode = isLightMode,
                onDismiss = { showSkillsManagerDialog = false }
            )
        }

        // A stale local Antigravity session can outlive an app sign-out. Hide its chat, tools,
        // terminal, and settings until the current app account is active and Antigravity is connected.
        if (accountIssue != null && !showSettings && !showEngineSettings) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .background(com.example.ui.theme.SaifTheme.colors.primaryBackground)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Verify your Antigravity account", fontSize = 21.sp, fontWeight = FontWeight.Bold,
                    color = com.example.ui.theme.SaifTheme.colors.textPrimary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
                Text(accountIssue, fontSize = 15.sp,
                    color = com.example.ui.theme.SaifTheme.colors.textSecondary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Button(onClick = { showConnectionDialog = true }) { Text("Manage Antigravity sign-in") }
                TextButton(onClick = { showSettings = true }) { Text("Open SAIF AI Settings") }
            }
        }

        if (showConnectionDialog) {
            SaifConnectionDialog(engineVm = engineVm, onDismiss = { showConnectionDialog = false })
        }
        // The in-app live panel sits above chat, project editor, studio and engine views alike.
        // Full-screen LIVE conversation: the SAIF AI AUTOMATION 3D orb, inside SAIF AI's own UI.
        // (This replaced the old sparkle-star FloatingVoiceOverlay animation.)
        val liveVoiceError by com.jarvis.assistant.SaifAutomation.liveError.collectAsState()
        LaunchedEffect(liveVoiceError, isAutomationLive, isHomeScreen, isWakeWordEnabled) {
            if (liveVoiceError != null && !isAutomationLive && isHomeScreen && isWakeWordEnabled) {
                com.example.util.WakeWordAudioDetector.start(context.applicationContext)
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = isAutomationLive,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
            com.example.ui.components.SaifLiveConversationPanel(
                isLightMode = isLightMode,
                onClose = { com.jarvis.assistant.SaifAutomation.stopLive(context) },
                onRequestScreenShare = {
                    com.example.ui.components.SaifLiveSession.pendingScreenShare = true
                    screenCapturePermissionLauncher.launch(
                        (context.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE)
                            as android.media.projection.MediaProjectionManager).createScreenCaptureIntent()
                    )
                }
            )
        }
        // When the service fails during startup it drops isLiveRunning immediately, so the orb
        // disappears. Show the *actual* reason here instead of leaving the user with a dead mic.
        if (!isAutomationLive && liveVoiceError != null) {
            val error = liveVoiceError!!
            AlertDialog(
                onDismissRequest = { com.jarvis.assistant.SaifAutomation.clearLiveError() },
                title = { Text(error.title) },
                text = { Text(error.message) },
                confirmButton = {
                    when (error.errorCode) {
                        "API_KEY_MISSING" -> TextButton(onClick = {
                            com.jarvis.assistant.SaifAutomation.clearLiveError()
                            showSettings = true
                        }) { Text("Open Settings") }
                        "MIC_PERMISSION_REQUIRED" -> TextButton(onClick = {
                            com.jarvis.assistant.SaifAutomation.clearLiveError()
                            liveMicPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        }) { Text("Allow microphone") }
                        else -> TextButton(onClick = {
                            com.jarvis.assistant.SaifAutomation.clearLiveError()
                            startLiveVoice()
                        }) { Text("Try again") }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { com.jarvis.assistant.SaifAutomation.clearLiveError() }) {
                        Text("Dismiss")
                    }
                }
            )
        }

        SaifEngineLoginDialogs(engineVm = engineVm)
    } // Close Box
}


private var lastCreateTapMs = 0L
/** Ignores a second "create project" tap that arrives within ~1.2 s of the first, so repeated taps cannot make duplicate projects. */
private fun claimCreateTap(): Boolean {
    val now = System.currentTimeMillis()
    if (now - lastCreateTapMs < 1200L) return false
    lastCreateTapMs = now
    return true
}
