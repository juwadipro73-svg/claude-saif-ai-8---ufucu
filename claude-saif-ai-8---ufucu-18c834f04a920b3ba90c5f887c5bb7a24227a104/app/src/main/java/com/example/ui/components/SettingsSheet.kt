/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.PhotoCamera
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material.icons.outlined.ChevronRight
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.data.local.ThemeManager
import com.example.data.local.ThemeMode
import com.example.gemini.ui.settings.ENGINE_SETTINGS_SECTIONS
import com.example.gemini.ui.settings.SettingsSection
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PrimaryAccent
import com.example.ui.theme.SaifTheme
import com.example.ui.theme.StatusError
import com.example.ui.theme.SubtextMuted

data class VoiceProfile(
    val id: String, 
    val name: String, 
    val description: String, 
    val emotion: String = "Warm & Natural",
    val gender: String, 
    val pitch: Float, 
    val speechRate: Float, 
    val previewHinglish: String = "Bilkul! Mai SAIF AI hoon, aapse smoothly aur natural emotion ke saath baat karne ke liye taiyaar.",
    val previewEnglish: String = "Hello, I am SAIF AI, speaking with natural clarity and emotion.",
    val targetEngineNameMatches: List<String> = emptyList()
)

val VOICE_PROFILES = listOf(
    // Round 9: the voice of SAIF AI AUTOMATION, brought into the main app as the DEFAULT voice everywhere
    // (chat replies, live baatcheet and video narration all read the same setting - see utils/SaifVoice.kt).
    VoiceProfile(
        id = "SaifAdvance",
        name = "SAIF ADVANCE VOICE",
        description = "SAIF AI ki apni advance awaaz — warm, smart aur natural (Hindi & English). Live baatcheet, chat aur video narration sab isi me.",
        emotion = "\u2728 Warm & Intelligent",
        gender = "Male",
        pitch = 1.00f,
        speechRate = 1.00f,
        previewHinglish = "Namaste! Mai SAIF Advance Voice hoon. Aapke saath naturally baat karunga, screen bhi dekh sakta hoon, aur video bhi bana sakta hoon.",
        previewEnglish = "Hello, I am the SAIF Advance Voice. Natural, warm and ready to talk, watch your screen or build your video.",
        // Best natural Indian voices first; the live engine asks a Gemini-Live provider for "Puck" instead.
        targetEngineNameMatches = listOf(
            "en-in-x-end-network", "en-in-x-ene-local", "hi-in-x-hia-network", "hi-in-x-hia-local",
            "en-in-x-cxx-network", "en_in", "hi_in"
        )
    ),
    VoiceProfile(
        id = "Kabir",
        name = "Kabir",
        description = "Deep, confident, podcast baritone (Hindi & English)",
        emotion = "Deep & Calm",
        gender = "Male",
        pitch = 0.90f,
        speechRate = 0.95f,
        previewHinglish = "Namaste! Mai Kabir hoon. Har baat ko deeply, smoothly aur bina kisi rukawat ke explain karna mera style hai.",
        previewEnglish = "Greetings. I am Kabir. Here to provide thoughtful, authoritative guidance.",
        targetEngineNameMatches = listOf("en-in-x-end-network", "en-in-x-ene-local", "hi-in-x-hia-network", "hi-in-x-hia-local", "en_in", "hi_in")
    ),
    VoiceProfile(
        id = "Priya",
        name = "Priya",
        description = "Cheerful, sweet, bubbly & joyful (Hindi & English)",
        emotion = "Cheerful & Sweet",
        gender = "Female",
        pitch = 1.12f,
        speechRate = 0.98f,
        previewHinglish = "Hi there! Mai Priya hoon! Aaiye milkar kuch naya, super interesting aur mazedaar banate hain!",
        previewEnglish = "Hi there! I am Priya, ready to bring creativity and joy to every task!",
        targetEngineNameMatches = listOf("en-in-x-cxx-network", "en-in-x-cxx-local", "hi-in-x-hic-local", "hi-in-x-hid-network", "en_in", "hi_in")
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    temperature: Float,
    customSystemPrompt: String,
    onSaveSettings: (Float, String) -> Unit,
    onClearCurrentChat: () -> Unit,
    onDismiss: () -> Unit,
    onOpenAdminDashboard: () -> Unit = {},
    /** Engine categories open as details and return to this original SAIF AI Settings panel. */
    onOpenEngineSettings: (String) -> Unit,
    /** Opens SAIF AI's terminal (used by the editing-engine settings rows). */
    onOpenTerminal: () -> Unit = {},
    autoApprove: Boolean,
    onAutoApproveChange: (Boolean) -> Unit,
    scrollState: androidx.compose.foundation.ScrollState,
    /** The engine's separate OAuth account (shown in the profile card). */
    engineAuthInfo: com.example.gemini.data.remote.AgyHubClient.AgyAuthInfo? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = androidx.compose.ui.platform.LocalContext.current
    val themeMode by ThemeManager.themeMode.collectAsState()
    val generalPrefs = remember { context.getSharedPreferences("SettingsPrefs", android.content.Context.MODE_PRIVATE) }
    var autoSpeak by remember { mutableStateOf(generalPrefs.getBoolean("autoSpeak", false)) }
    
    val prefs = remember { context.getSharedPreferences("VoiceSettings", android.content.Context.MODE_PRIVATE) }
    
    val wakeWord by com.example.util.WakeWordManager.wakeWordFlow.collectAsState()
    val isWakeWordEnabled by com.example.util.WakeWordManager.isWakeWordEnabledFlow.collectAsState()
    var customWakeWordInput by remember(wakeWord) { mutableStateOf(wakeWord) }
    var wakeWordSavedFeedback by remember { mutableStateOf(false) }
    var wakeWordPresets by remember { mutableStateOf(com.example.util.WakeWordManager.getPresets(context)) }

    var selectedVoiceId by remember {
        // One voice for the whole app: chat, live baatcheet and video narration (see utils/SaifVoice.kt).
        mutableStateOf(com.example.utils.SaifVoice.selectedId(context))
    }
    var ttsVoices by remember { mutableStateOf(VOICE_PROFILES) }
    var ttsInstance by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    
    LaunchedEffect(Unit) {
        ttsInstance = android.speech.tts.TextToSpeech(context) { status ->
            // Just initialize TTS to test availability if needed
        }
    }
    
    DisposableEffect(Unit) {
        onDispose { ttsInstance?.shutdown() }
    }
    
    val authUser by com.example.data.local.AuthManager.currentUser.collectAsState()
    val isPinProtected by com.example.util.SmartConversationMemory.isPinProtected.collectAsState()
    var showCropDialog by remember { mutableStateOf(false) }
    var selectedCropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showMemoryVaultDialog by remember { mutableStateOf(false) }
    var showDownloadPinDialog by remember { mutableStateOf(false) }
    // An editing-engine section opened from this panel. null = SAIF AI's own settings list.
    var enginePage by remember { mutableStateOf<SettingsSection?>(null) }
    var downloadPinInput by remember { mutableStateOf("") }
    var downloadPinError by remember { mutableStateOf<String?>(null) }
    var showSignOutConfirmDialog by remember { mutableStateOf(false) }
    val displayName = authUser?.name?.ifBlank { authUser?.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() } } ?: "User"

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val stream = context.contentResolver.openInputStream(uri)
                val bmp = BitmapFactory.decodeStream(stream)
                stream?.close()
                if (bmp != null) {
                    selectedCropBitmap = bmp
                    showCropDialog = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Could not load selected photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showCropDialog && selectedCropBitmap != null) {
        ImageCropEditPanel(
            bitmap = selectedCropBitmap!!,
            onCropConfirmed = { cropped ->
                showCropDialog = false
                selectedCropBitmap = null
                val userEmail = authUser?.email ?: "user"
                com.example.data.local.AuthManager.saveUserAvatar(context, userEmail, cropped)
                Toast.makeText(context, "Profile photo updated successfully!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                showCropDialog = false
                selectedCropBitmap = null
            }
        )
    }

    val glassBorderBrush = Brush.linearGradient(
        listOf(
            SaifTheme.colors.textPrimary.copy(alpha = 0.35f),
            Color.Transparent,
            SaifTheme.colors.textPrimary.copy(alpha = 0.1f)
        )
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SaifTheme.colors.primaryBackground,
        contentColor = SaifTheme.colors.textPrimary,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(SaifTheme.colors.cardBorder)
            )
        },
        modifier = Modifier.fillMaxSize() // Make it full screen
    ) {
        // An editing-engine section opens INSIDE this panel: its own Back returns here.
        if (enginePage != null) {
            val section = enginePage!!
            val engineViewModel = remember {
                com.example.gemini.ui.chat.ChatViewModelHolder.get(
                    context.applicationContext as android.app.Application
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
                com.example.gemini.ui.settings.EmbeddedEngineSettings(
                    viewModel = engineViewModel,
                    isLightMode = SaifTheme.colors.isLight,
                    initialSection = section.name,
                    onOpenTerminal = onOpenTerminal,
                    autoApprove = autoApprove,
                    onAutoApproveChange = onAutoApproveChange,
                    onDismiss = { enginePage = null }
                )
            }
            return@ModalBottomSheet
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Settings",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = SaifTheme.colors.textPrimary
                    )
                    Text(
                        text = "Manage your SAIF AI experience",
                        fontSize = 13.sp,
                        color = SubtextMuted
                    )
                }
                
                // Liquid glass close button
                LiquidButton(
                    onClick = onDismiss,
                    color = SaifTheme.colors.surfaceCard,
                    glassBorderBrush = glassBorderBrush,
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = SaifTheme.colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Profile Section
            val user = authUser
            val displayName = user?.name?.ifBlank { user.email.substringBefore("@").replaceFirstChar { it.uppercase() } } ?: "User"
            val engineEmail = engineAuthInfo?.takeIf { it.isLoggedIn && it.email.isNotBlank() }?.email
            val engineAvatarModel = remember(engineAuthInfo?.profilePictureUrl) {
                com.example.gemini.ui.drawer.parseProfileAvatarModel(engineAuthInfo?.profilePictureUrl)
            }
            val displayEmail = user?.email?.ifBlank { "guest@saif.ai" } ?: "guest@saif.ai"
            val initialLetter = user?.initialLetter ?: displayName.take(1).uppercase().ifBlank { "U" }
            val avatarPath = user?.profilePicturePath
            val avatarFile = avatarPath?.let { java.io.File(it) }
            val avatarBitmap = remember(avatarPath) {
                if (avatarFile != null && avatarFile.exists()) {
                    try {
                        BitmapFactory.decodeFile(avatarFile.absolutePath)
                    } catch (e: Exception) {
                        null
                    }
                } else null
            }

            LiquidSurface(
                color = SaifTheme.colors.surfaceCard,
                glassBorderBrush = glassBorderBrush,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clickable {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF7C5CE0), Color(0xFF5E43BD), Color(0xFF4C1D95))
                                    )
                                )
                                .border(2.dp, Color(0xFF9B82EC).copy(alpha = 0.6f), CircleShape)
                        ) {
                            if (avatarBitmap != null) {
                                Image(
                                    bitmap = avatarBitmap.asImageBitmap(),
                                    contentDescription = "Profile Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (engineEmail != null && engineAvatarModel != null) {
                                coil.compose.AsyncImage(
                                    model = engineAvatarModel,
                                    contentDescription = "Google profile photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = initialLetter,
                                    color = Color.White,
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Camera Edit Badge Icon on bottom-end
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF5E43BD),
                            border = BorderStroke(1.5.dp, Color(0xFF1F1E1D)),
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .size(26.dp)
                                .align(Alignment.BottomEnd)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "Change Profile Photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Display Name with Edit Name Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = displayName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = SaifTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            onClick = { showEditNameDialog = true },
                            shape = CircleShape,
                            color = SaifTheme.colors.primaryBackground.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, SaifTheme.colors.cardBorder)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Name",
                                tint = com.example.ui.theme.PrimaryAccent,
                                modifier = Modifier.padding(5.dp).size(14.dp)
                            )
                        }
                    }

                    Text(
                        text = displayEmail,
                        fontSize = 13.sp,
                        color = SaifTheme.colors.textSecondary
                    )
                    if (engineEmail != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(Color(0xFF22C55E), CircleShape))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Antigravity signed in: $engineEmail",
                                fontSize = 11.sp,
                                color = SaifTheme.colors.textSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (user?.isAdmin == true) {
                            Surface(
                                color = Color(0xFF2E2046),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Color(0xFF6B31CF).copy(alpha = 0.7f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Icon(imageVector = Icons.Outlined.AdminPanelSettings, contentDescription = null, tint = Color(0xFFD4C9F8), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Admin",
                                        color = Color(0xFFD4C9F8),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            Surface(
                                color = Color(0xFF4C1D95).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = "Free User",
                                    color = Color(0xFF9B82EC),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        // Sign Out button in Profile Card
                        Surface(
                            onClick = { showSignOutConfirmDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            color = StatusError.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, StatusError.copy(alpha = 0.35f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Logout,
                                    contentDescription = "Sign Out",
                                    tint = StatusError,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Sign Out",
                                    color = StatusError,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SAIF AI's original Appearance control comes first, before the editing engine.
            SectionHeader("APPEARANCE")
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().height(64.dp)
                    .background(SaifTheme.colors.surfaceCard, RoundedCornerShape(16.dp))
                    .padding(4.dp)
            ) {
                val segmentWidth = maxWidth / 3
                val segmentWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { segmentWidth.toPx() }
                val targetOffsetX = when (themeMode) {
                    ThemeMode.LIGHT -> 0
                    ThemeMode.DARK -> segmentWidthPx.roundToInt()
                    ThemeMode.SYSTEM -> (segmentWidthPx * 2).roundToInt()
                }
                val offset by animateIntOffsetAsState(
                    targetValue = IntOffset(targetOffsetX, 0),
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                    label = "AppearanceSegmentOffset"
                )
                Box(
                    modifier = Modifier.width(segmentWidth).fillMaxHeight()
                        .offset { offset }
                        .background(PrimaryAccent, RoundedCornerShape(12.dp))
                )
                Row(Modifier.fillMaxSize()) {
                    listOf(
                        Triple("Light", Icons.Default.LightMode, ThemeMode.LIGHT),
                        Triple("Dark", Icons.Default.DarkMode, ThemeMode.DARK),
                        Triple("System", Icons.Default.Computer, ThemeMode.SYSTEM)
                    ).forEach { (title, icon, mode) ->
                        val selected = themeMode == mode
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { ThemeManager.setTheme(mode) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(icon, contentDescription = title,
                                    tint = if (selected) Color.White else SaifTheme.colors.textSecondary,
                                    modifier = Modifier.size(20.dp))
                                Spacer(Modifier.height(2.dp))
                                Text(title, color = if (selected) Color.White else SaifTheme.colors.textSecondary,
                                    fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            SectionHeader("AI AGENT & SKILLS")
            Spacer(Modifier.height(8.dp))
            LiquidSurface(
                color = SaifTheme.colors.surfaceCard,
                glassBorderBrush = glassBorderBrush,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Auto approve", color = SaifTheme.colors.textPrimary,
                                fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(if (autoApprove) "Commands run without asking" else "Ask before running commands",
                                color = SaifTheme.colors.textSecondary, fontSize = 12.sp)
                        }
                        Switch(checked = autoApprove, onCheckedChange = onAutoApproveChange)
                    }

                    HorizontalDivider(color = SaifTheme.colors.cardBorder)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Auto-speak replies", color = SaifTheme.colors.textPrimary,
                                fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("Read every answer out loud",
                                color = SaifTheme.colors.textSecondary, fontSize = 12.sp)
                        }
                        Switch(
                            checked = autoSpeak,
                            onCheckedChange = {
                                autoSpeak = it
                                generalPrefs.edit().putBoolean("autoSpeak", it).apply()
                            }
                        )
                    }

                    HorizontalDivider(color = SaifTheme.colors.cardBorder)
                    SettingsActionRow(
                        title = "AI Skills (/commands)",
                        subtitle = "Import & manage GitHub AI skills and slash commands",
                        onClick = { onOpenEngineSettings("SKILLS") }
                    )

                    HorizontalDivider(color = SaifTheme.colors.cardBorder)
                    SettingsActionRow(
                        title = "Slash Commands",
                        subtitle = "Create, edit and delete the / commands you type in chat",
                        onClick = { enginePage = SettingsSection.SLASH_COMMANDS }
                    )

                    HorizontalDivider(color = SaifTheme.colors.cardBorder)
                    SettingsActionRow(
                        title = "Agents",
                        subtitle = "Run Antigravity, Claude Code or both",
                        onClick = { enginePage = SettingsSection.AGENTS }
                    )

                    HorizontalDivider(color = SaifTheme.colors.cardBorder)
                    SettingsActionRow(
                        title = "MCP Servers",
                        subtitle = "Model Context Protocol tools & integrations",
                        onClick = { enginePage = SettingsSection.MCP }
                    )

                    HorizontalDivider(color = SaifTheme.colors.cardBorder)
                    SettingsActionRow(
                        title = "Skills & Plugins",
                        subtitle = "Agent capabilities, Google plugins and extensions",
                        onClick = { enginePage = SettingsSection.SKILLS_PLUGINS }
                    )

                    HorizontalDivider(color = SaifTheme.colors.cardBorder)
                    SettingsActionRow(
                        title = "GitHub Account & Sync",
                        subtitle = "Connect GitHub token/account, clone repos & sync Linux Git",
                        onClick = { onOpenEngineSettings("GITHUB") }
                    )

                    HorizontalDivider(color = SaifTheme.colors.cardBorder)
                    SettingsActionRow(
                        title = "Antigravity Connection",
                        subtitle = "Manage Antigravity sign-in and workspace server status",
                        onClick = { onOpenEngineSettings("CONNECTION") }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // SAIF ADVANCE EDITING ENGINE - every engine setting opens right here, inside this
            // same panel. There is no separate engine settings screen to hunt for any more.
            val engineShortcuts = setOf(
                SettingsSection.SLASH_COMMANDS, SettingsSection.AGENTS,
                SettingsSection.MCP, SettingsSection.SKILLS_PLUGINS
            )
            val engineSections = ENGINE_SETTINGS_SECTIONS.filter { it !in engineShortcuts }
            SectionHeader("SAIF ADVANCE EDITING ENGINE")
            Spacer(Modifier.height(8.dp))
            LiquidSurface(
                color = SaifTheme.colors.surfaceCard,
                glassBorderBrush = glassBorderBrush,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    engineSections.forEachIndexed { index, section ->
                        if (index > 0) HorizontalDivider(color = SaifTheme.colors.cardBorder)
                        SettingsActionRow(
                            title = section.title,
                            subtitle = section.subtitle,
                            onClick = { enginePage = section }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // The rest of SAIF AI's original settings follow the engine. Wake-word controls
            // remain hidden as before; live conversation settings are shown separately below.
            if (SHOW_LEGACY_VOICE_SETTINGS) {
            // Wake Up Word (Hotword) Section
            SectionHeader("WAKE UP WORD (हे सैफ़ / HOTWORD)")
            Spacer(modifier = Modifier.height(8.dp))

            LiquidSurface(
                color = SaifTheme.colors.surfaceCard,
                glassBorderBrush = glassBorderBrush,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Toggle Active
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF9B82EC).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Outlined.AutoAwesome, contentDescription = null, tint = Color(0xFF9B82EC), modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Wake Up Word Active",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SaifTheme.colors.textPrimary
                            )
                            Text(
                                text = "Open the voice assistant chat popup by speaking",
                                fontSize = 12.5.sp,
                                color = SaifTheme.colors.textSecondary
                            )
                        }
                        Switch(
                            checked = isWakeWordEnabled,
                            onCheckedChange = {
                                com.example.util.WakeWordManager.setWakeWordEnabled(context, it)
                                if (it) {
                                    com.example.util.WakeWordAudioDetector.start(context)
                                } else {
                                    com.example.util.WakeWordAudioDetector.stop()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = com.example.ui.theme.PrimaryAccent,
                                uncheckedThumbColor = SaifTheme.colors.textSecondary,
                                uncheckedTrackColor = SaifTheme.colors.primaryBackground
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = SaifTheme.colors.cardBorder)
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Customize Wake Up Word",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SaifTheme.colors.textPrimary
                    )
                    Text(
                        text = "The default is 'Hey Saif'. Whatever name or phrase you enter here will open the chat popup from the bottom of the screen when spoken.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = SaifTheme.colors.textSecondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                    )

                    // Text input field with Save button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = customWakeWordInput,
                            onValueChange = { 
                                customWakeWordInput = it 
                                wakeWordSavedFeedback = false
                            },
                            placeholder = { Text("e.g. Hey Saif", fontSize = 13.sp, color = SaifTheme.colors.textSecondary) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF9B82EC),
                                unfocusedBorderColor = SaifTheme.colors.cardBorder,
                                focusedTextColor = SaifTheme.colors.textPrimary,
                                unfocusedTextColor = SaifTheme.colors.textPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = {
                                if (customWakeWordInput.isNotBlank()) {
                                    val cleanInput = customWakeWordInput.trim()
                                    com.example.util.WakeWordManager.setWakeWord(context, cleanInput)
                                    val updated = com.example.util.WakeWordManager.addPreset(context, cleanInput)
                                    wakeWordPresets = updated
                                    wakeWordSavedFeedback = true
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = com.example.ui.theme.PrimaryAccent),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Text("Save", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    if (wakeWordSavedFeedback) {
                        Text(
                            text = "✓ Wake up word saved: '$wakeWord'",
                            color = Color(0xFF10B981),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Preset chips
                    Text(
                        text = "Quick Presets:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = SaifTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        wakeWordPresets.forEach { preset ->
                            val isCurrent = wakeWord.equals(preset, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isCurrent) com.example.ui.theme.PrimaryAccent else SaifTheme.colors.primaryBackground,
                                border = if (isCurrent) null else androidx.compose.foundation.BorderStroke(1.dp, SaifTheme.colors.cardBorder),
                                modifier = Modifier.clickable {
                                    customWakeWordInput = preset
                                    com.example.util.WakeWordManager.setWakeWord(context, preset)
                                    wakeWordSavedFeedback = true
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
                                ) {
                                    Text(
                                        text = preset,
                                        color = if (isCurrent) Color.White else SaifTheme.colors.textPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(if (isCurrent) Color.White.copy(alpha = 0.25f) else SaifTheme.colors.cardBorder.copy(alpha = 0.6f))
                                            .clickable {
                                                val updated = com.example.util.WakeWordManager.deletePreset(context, preset)
                                                wakeWordPresets = updated
                                                if (isCurrent && updated.isNotEmpty()) {
                                                    val nextWord = updated.first()
                                                    customWakeWordInput = nextWord
                                                    com.example.util.WakeWordManager.setWakeWord(context, nextWord)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Delete preset $preset",
                                            tint = if (isCurrent) Color.White else SaifTheme.colors.textSecondary,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            } // end SHOW_LEGACY_VOICE_SETTINGS (wake-up word)

            // AI Memory
            SectionHeader("AI MEMORY")
            LiquidSurface(
                color = SaifTheme.colors.surfaceCard,
                glassBorderBrush = glassBorderBrush,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                onClick = { showMemoryVaultDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Psychology,
                        contentDescription = null,
                        tint = com.example.ui.theme.PrimaryAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "SAIF AI Memories",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SaifTheme.colors.textPrimary
                            )
                            if (isPinProtected) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "PIN Protected",
                                    tint = com.example.ui.theme.PrimaryAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Text(
                            text = if (isPinProtected) "Private & PIN Protected" else "Things AI learned about you",
                            fontSize = 13.sp,
                            color = SaifTheme.colors.textSecondary
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = SaifTheme.colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Terminal providers: SAIF AI prepares the chosen agent terminal in the background.
            SectionHeader("TERMINAL PROVIDERS")
            Spacer(modifier = Modifier.height(8.dp))
            TerminalProvidersSection(glassBorderBrush = glassBorderBrush)


            // Permission Manager Section
            PermissionManagerSection(glassBorderBrush = glassBorderBrush)
            Spacer(modifier = Modifier.height(32.dp))

            Spacer(modifier = Modifier.height(24.dp))

            // Licences & open source: GPL v3 compliance lives inside the app, not just in the repo.
            // Keep the wording here in step with LICENSE, THIRD_PARTY_NOTICES.md and
            // GPL_SHIP_CHECKLIST.md.
            SectionHeader("LICENCES & OPEN SOURCE")
            Spacer(modifier = Modifier.height(8.dp))
            SaifLicencesSection(glassBorderBrush = glassBorderBrush)

            Spacer(modifier = Modifier.height(32.dp))

            // Footer
            Text(
                text = "SAIF AI is created by SAIF. Version 1.0.0\n${com.example.gemini.BuildConfig.BUILD_STAMP}",
                fontSize = 11.sp,
                color = SubtextMuted,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }

    if (showEditNameDialog) {
        EditNameDialog(
            currentName = displayName,
            onDismiss = { showEditNameDialog = false },
            onNameUpdated = { }
        )
    }

    if (showMemoryVaultDialog) {
        SaifAiMemoryDialog(
            onDismiss = { showMemoryVaultDialog = false }
        )
    }

    if (showDownloadPinDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadPinDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = PrimaryAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enter Memory PIN", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Your Saif AI data is protected with a PIN lock. Enter your 4-digit PIN to download your personal data:",
                        fontSize = 13.sp,
                        color = SaifTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = downloadPinInput,
                        onValueChange = {
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) downloadPinInput = it
                            downloadPinError = null
                        },
                        label = { Text("4-Digit PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (downloadPinError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(downloadPinError!!, color = StatusError, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (com.example.util.SmartConversationMemory.verifyPin(downloadPinInput)) {
                            showDownloadPinDialog = false
                            val file = com.example.util.SmartConversationMemory.exportAllUserData(context)
                            if (file != null) {
                                com.example.util.SmartConversationMemory.shareExportedFile(context, file)
                                Toast.makeText(context, "Data ready to download/share!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to export data", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            downloadPinError = "Incorrect PIN"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                ) {
                    Text("Verify & Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadPinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSignOutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirmDialog = false },
            title = {
                Text("Sign Out", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Are you sure you want to sign out of SAIF AI?", fontSize = 14.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutConfirmDialog = false
                        onDismiss()
                        com.example.data.local.AuthManager.signOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError)
                ) {
                    Text("Sign Out", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = SaifTheme.colors.surfaceCard,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/** One tappable settings row: a title, a subtitle and a chevron. */
@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = SaifTheme.colors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = SaifTheme.colors.textSecondary,
                fontSize = 12.sp
            )
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = SaifTheme.colors.textSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier.padding(bottom = 8.dp)) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = SubtextMuted,
        letterSpacing = 1.sp,
        modifier = modifier
    )
}

@Composable
fun AppearanceOption(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glassBorderBrush: Brush
) {
    val backgroundColor = if (isSelected) com.example.ui.theme.PrimaryAccent else SaifTheme.colors.surfaceCard
    val contentColor = if (isSelected) Color.White else SaifTheme.colors.textPrimary
    
    LiquidSurface(
        onClick = onClick,
        color = backgroundColor,
        glassBorderBrush = if (isSelected) null else glassBorderBrush,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                color = contentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * The legacy wake-word controls stay hidden. The voice picker, the live-baatcheet section, the
 * provider/model list and the data-export card were removed from this panel in this build: the
 * voice is permanently Kabir and the editing-engine settings are listed above instead.
 */
private const val SHOW_LEGACY_VOICE_SETTINGS = false

@Composable
fun LiquidSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color,
    glassBorderBrush: Brush?,
    shape: androidx.compose.ui.graphics.Shape,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "LiquidScale"
    )

    Surface(
        shape = shape,
        color = color,
        border = if (glassBorderBrush != null) androidx.compose.foundation.BorderStroke(1.5.dp, glassBorderBrush) else null,
        modifier = modifier
            .scale(scale)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource, 
                        indication = androidx.compose.foundation.LocalIndication.current,
                        onClick = onClick
                    )
                } else Modifier
            )
    ) {
        Box(modifier = Modifier.animateContentSize()) {
            content()
        }
    }
}

@Composable
fun LiquidButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    color: Color,
    glassBorderBrush: Brush?,
    shape: androidx.compose.ui.graphics.Shape,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "ButtonScale"
    )

    Surface(
        onClick = onClick,
        shape = shape,
        color = color,
        border = if (glassBorderBrush != null) androidx.compose.foundation.BorderStroke(1.5.dp, glassBorderBrush) else null,
        interactionSource = interactionSource,
        modifier = modifier.scale(scale)
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}
