/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.ui.components

import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.draw.blur

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.scale
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut

import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.border
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.focus.onFocusChanged
import com.example.ui.theme.LiquidGlassDefaults
import com.example.ui.theme.liquidBounceClickable


@Composable
fun SaifInputBar(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    isGenerating: Boolean,
    onStop: () -> Unit,
    onVoiceRecorded: (java.io.File) -> Unit,
    onStartLiveVoice: () -> Unit,
    onGalleryClick: () -> Unit,
    onFileClick: () -> Unit,
    onVideoClick: () -> Unit,
    onCameraClick: () -> Unit,
    onVoiceListeningStateChange: (Boolean, Float) -> Unit = { _, _ -> },
    stopVoiceListeningTrigger: Int = 0,
    hasAttachments: Boolean = false,
    modelSelector: (@Composable () -> Unit)? = null,
    placeholderText: String = "REPLY TO SAIF AI",
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    val context = androidx.compose.ui.platform.LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var rmsValue by remember { mutableStateOf(0f) }
    var textBeforeListening by remember { mutableStateOf("") }

    val directSpeechEngine = remember { DirectSpeechInputEngine(context) }

    val stopListeningNow = {
        if (isListening) {
            directSpeechEngine.stopListening()
            isListening = false
            rmsValue = 0f
            onVoiceListeningStateChange(false, 0f)
        }
    }

    LaunchedEffect(stopVoiceListeningTrigger) {
        if (stopVoiceListeningTrigger > 0 && isListening) {
            stopListeningNow()
        }
    }

    val startListening = {
        textBeforeListening = input
        directSpeechEngine.startListening(
            onRmsChanged = { rms ->
                rmsValue = rms
                onVoiceListeningStateChange(true, rms)
            },
            onInterimResult = { partialText ->
                val newText = if (textBeforeListening.isBlank()) partialText else "$textBeforeListening $partialText"
                onInputChange(newText)
            },
            onFinalResult = { finalText ->
                val newText = if (textBeforeListening.isBlank()) finalText else "$textBeforeListening $finalText"
                onInputChange(newText)
            },
            onStateChanged = { listening ->
                isListening = listening
                if (!listening) rmsValue = 0f
                onVoiceListeningStateChange(listening, if (listening) rmsValue else 0f)
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startListening()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            directSpeechEngine.release()
        }
    }

    val hasText = input.trim().isNotEmpty()
    val canSend = hasText || hasAttachments
    var showAttachmentMenu by remember { mutableStateOf(false) }

    // The text lives in local state so fast typing / IME composition is never overwritten by a late value echoed
    // back from the ViewModel (that echo was what made the old box lose characters). Only genuinely external
    // changes (voice transcript, clear after send) replace what is shown.
    var tfv by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(input)) }
    val sentTexts = remember { mutableListOf<String>() }
    LaunchedEffect(input) {
        val external = input !in sentTexts || (input.isEmpty() && sentTexts.lastOrNull() != "")
        if (input != tfv.text && external) {
            tfv = androidx.compose.ui.text.input.TextFieldValue(input, androidx.compose.ui.text.TextRange(input.length))
            sentTexts.clear()
            sentTexts.add(input)
        }
    }

    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        // Attachment Menu using Popup to allow dismissal by clicking outside
        if (showAttachmentMenu) {
            Popup(
                alignment = Alignment.BottomStart,
                offset = androidx.compose.ui.unit.IntOffset(16, -180),
                onDismissRequest = { showAttachmentMenu = false },
                properties = PopupProperties(focusable = true)
            ) {
                var isVisible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { isVisible = true }
                val scale by animateFloatAsState(
                    targetValue = if (isVisible) 1f else 0.8f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
                    label = "popupScale"
                )
                val alpha by animateFloatAsState(
                    targetValue = if (isVisible) 1f else 0f,
                    animationSpec = tween(200),
                    label = "popupAlpha"
                )
                val isLight = com.example.ui.theme.SaifTheme.colors.isLight
                Box(
                    modifier = Modifier
                        .width(168.dp)
                        .scale(scale)
                        .alpha(alpha)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            if (isLight) Color(0xFFFFFFFF) else Color(0xFF2B2A28)
                        )
                        .border(1.dp, if (isLight) Color(0xFFE5E2D6) else Color(0xFF3A3835), RoundedCornerShape(22.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        AttachmentOption(icon = Icons.Outlined.Image, color = Color(0xFF7C5CE0), label = "Gallery", onClick = { showAttachmentMenu = false; onGalleryClick() })
                        AttachmentOption(icon = Icons.AutoMirrored.Outlined.InsertDriveFile, color = Color(0xFF9B82EC), label = "File", onClick = { showAttachmentMenu = false; onFileClick() })
                        AttachmentOption(icon = Icons.Outlined.Videocam, color = Color(0xFFEF4444), label = "Video", onClick = { showAttachmentMenu = false; onVideoClick() })
                    }
                }
            }
        }
        
        val c = com.example.ui.theme.SaifTheme.colors
        val isLightMode = c.isLight
        val purple = Color(0xFF7C5CE0)
        val circleFill = if (isLightMode) Color(0xFFFAF9F5) else Color(0xFF1F1E1D)
        val circleBorder = BorderStroke(1.dp, c.textPrimary.copy(alpha = 0.10f))

        // Keep the suggestions above the composer rather than drawing over the mic or model controls.
        val slashQuery = com.example.gemini.data.remote.SkillSlashParsing.query(tfv.text)
        val suggestions = if (slashQuery == null) emptyList() else
            (com.example.gemini.data.remote.PromptExpansion.baseSlashItems() +
                com.example.gemini.data.remote.SlashCommandsCache.getCachedSync())
                .distinctBy { it.command.lowercase() }
                .filter { slashQuery.isBlank() || it.name.contains(slashQuery, true) || it.description.contains(slashQuery, true) }
        Column(Modifier.fillMaxWidth()) {
            if (suggestions.isNotEmpty()) {
                com.example.gemini.ui.chat.SlashCommandOverlay(
                    items = suggestions, query = slashQuery.orEmpty(), runsOnTap = { false },
                    onPick = { item ->
                        val picked = item.command + if (item.command.endsWith(':')) "" else " "
                        tfv = androidx.compose.ui.text.input.TextFieldValue(picked,
                            androidx.compose.ui.text.TextRange(picked.length))
                        sentTexts.add(picked)
                        onInputChange(picked)
                    }, modifier = Modifier.padding(bottom = 6.dp)
                )
            }
        // The workspace-style chatbox: one rounded card, text on top, one row of round buttons below.
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = if (isLightMode) Color(0xFFFFFFFF) else Color(0xFF2B2A28),
            border = BorderStroke(1.dp, if (isLightMode) Color(0xFFE5E2D6) else Color(0xFF3A3835)),
            shadowElevation = if (isLightMode) 3.dp else 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 2.dp)
                        .heightIn(min = 40.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (tfv.text.isEmpty()) {
                        if (isListening) {
                            val listeningRmsNorm = (rmsValue / 10f).coerceIn(0f, 1f)
                            val pulseDotScale by animateFloatAsState(
                                targetValue = 1f + (listeningRmsNorm * 0.5f),
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                label = "pulseDotScale"
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .scale(pulseDotScale)
                                        .background(Color(0xFF8A5CD9), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Listening...",
                                    color = Color(0xFF9B82EC),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            Text(
                                text = placeholderText,
                                color = c.textPrimary.copy(alpha = 0.42f),
                                fontSize = 15.sp
                            )
                        }
                    }
                    BasicTextField(
                        value = tfv,
                        onValueChange = { newVal ->
                            if (isListening) {
                                stopListeningNow()
                            }
                            val textChanged = newVal.text != tfv.text
                            tfv = newVal
                            if (textChanged) {
                                sentTexts.add(newVal.text)
                                if (sentTexts.size > 24) sentTexts.removeAt(0)
                                onInputChange(newVal.text)
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        textStyle = TextStyle(
                            color = c.textPrimary,
                            fontSize = 15.sp,
                            lineHeight = 21.sp
                        ),
                        maxLines = 6,
                        cursorBrush = SolidColor(purple),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused && isListening) {
                                    stopListeningNow()
                                }
                            }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Clip (attachments) and camera: SAIF AI's own two buttons, in the workspace button style
                    Surface(
                        onClick = {
                            stopListeningNow()
                            showAttachmentMenu = !showAttachmentMenu
                        },
                        shape = CircleShape,
                        color = circleFill,
                        border = circleBorder,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.AttachFile,
                                contentDescription = "Attach File",
                                tint = c.textPrimary.copy(alpha = 0.75f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        onClick = {
                            stopListeningNow()
                            onCameraClick()
                        },
                        shape = CircleShape,
                        color = circleFill,
                        border = circleBorder,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.CameraAlt,
                                contentDescription = "Camera",
                                tint = c.textPrimary.copy(alpha = 0.75f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Use the free space between camera and mic, not a second full-width row.
                    // The model name ellipsizes inside this weighted slot on narrow phones.
                    Box(Modifier.weight(1f).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                        modelSelector?.invoke()
                    }

                    // Dictation remains in the row when the model picker or generation state changes.
                    Surface(
                            onClick = {
                                if (isListening) {
                                    stopListeningNow()
                                } else {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        startListening()
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            shape = CircleShape,
                            color = if (isListening) purple.copy(alpha = 0.16f) else circleFill,
                            border = if (isListening) BorderStroke(1.dp, purple.copy(alpha = 0.55f)) else circleBorder,
                            modifier = Modifier.size(34.dp),
                            enabled = !isGenerating
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Mic,
                                    contentDescription = "Microphone",
                                    tint = if (isListening) purple else c.textPrimary.copy(alpha = 0.75f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    Spacer(modifier = Modifier.width(6.dp))

                    // Stop / Send / Live voice
                    AnimatedContent(
                        targetState = if (isGenerating) 0 else if (canSend) 1 else 2,
                        transitionSpec = { (fadeIn(tween(160)) + scaleIn(initialScale = 0.78f, animationSpec = tween(180))) togetherWith fadeOut(tween(90)) },
                        label = "actionMorph"
                    ) { actionState ->
                    if (actionState == 0) {
                        Surface(
                            onClick = onStop,
                            shape = CircleShape,
                            color = purple,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else if (actionState == 1) {
                        Surface(
                            onClick = {
                                if (isListening) stopListeningNow()
                                focusManager.clearFocus()
                                onSend()
                            },
                            shape = CircleShape,
                            color = purple,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Send",
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    } else {
                        val liveBreath = rememberInfiniteTransition(label = "liveBreath")
                        val liveScale by liveBreath.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.06f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1100, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "liveBreathScale"
                        )
                        Surface(
                            onClick = {
                                if (isListening) stopListeningNow() else onStartLiveVoice()
                            },
                            shape = CircleShape,
                            color = Color.Transparent,
                            modifier = Modifier.size(36.dp).scale(liveScale)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize().background(
                                    Brush.linearGradient(listOf(Color(0xFF7C5CE0), Color(0xFF5E43BD), Color(0xFF9B82EC)))
                                )
                            ) {
                                SoundWaveIcon(isAnimated = isListening, rms = rmsValue)
                            }
                        }
                    }
                    }
                }
            }
        }
        }
    }
}

@Composable
fun AttachmentOption(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, label: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "AttachmentOptionScale"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(end = 24.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (com.example.ui.theme.SaifTheme.colors.isLight) Color(0x207C5CE0) else Color(0x337C5CE0)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = label, color = com.example.ui.theme.SaifTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SoundWaveIcon(isAnimated: Boolean = false, rms: Float = 0f) {
    val waveBrush = androidx.compose.ui.graphics.Brush.verticalGradient(
        listOf(Color.White, Color(0xFFEDE6F9))
    )

    if (isAnimated) {
        val transition = rememberInfiniteTransition(label = "soundbars")
        val h1Base by transition.animateFloat(6f, 15f, infiniteRepeatable(tween(350, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "h1")
        val h2Base by transition.animateFloat(10f, 20f, infiniteRepeatable(tween(280, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "h2")
        val h3Base by transition.animateFloat(8f, 17f, infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "h3")
        val h4Base by transition.animateFloat(5f, 13f, infiniteRepeatable(tween(320, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "h4")
        
        val rmsFactor = (rms / 10f).coerceIn(0f, 1f)
        val h1 = h1Base + (10f * rmsFactor)
        val h2 = h2Base + (14f * rmsFactor)
        val h3 = h3Base + (12f * rmsFactor)
        val h4 = h4Base + (8f * rmsFactor)

        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(3.dp).height(h1.dp).clip(CircleShape).background(waveBrush))
            Box(modifier = Modifier.width(3.dp).height(h2.dp).clip(CircleShape).background(waveBrush))
            Box(modifier = Modifier.width(3.dp).height(h3.dp).clip(CircleShape).background(waveBrush))
            Box(modifier = Modifier.width(3.dp).height(h4.dp).clip(CircleShape).background(waveBrush))
        }
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(3.dp).height(10.dp).clip(CircleShape).background(waveBrush))
            Box(modifier = Modifier.width(3.dp).height(18.dp).clip(CircleShape).background(waveBrush))
            Box(modifier = Modifier.width(3.dp).height(14.dp).clip(CircleShape).background(waveBrush))
            Box(modifier = Modifier.width(3.dp).height(8.dp).clip(CircleShape).background(waveBrush))
        }
    }
}

