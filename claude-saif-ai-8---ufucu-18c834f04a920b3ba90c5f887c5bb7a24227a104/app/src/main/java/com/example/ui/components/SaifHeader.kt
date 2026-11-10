/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */


package com.example.ui.components

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LiquidGlassDefaults
import com.example.ui.theme.SaifTheme
import com.example.ui.theme.liquidBounceClickable
import kotlinx.coroutines.launch

@OptIn(ExperimentalTextApi::class)
@Composable
fun SaifHeader(
    currentMode: String,
    isLightMode: Boolean,
    onToggleTheme: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenModeSelector: () -> Unit,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlusClick: () -> Unit,
    onNewVideoProjectClick: () -> Unit = {},
    /** Kept for compatibility: the red "+" is gone, this now opens SAIF AI's live-conversation mode sheet. */
    onAutomationClick: () -> Unit = {},
    isFilterBookmarked: Boolean,
    onToggleBookmarkFilter: () -> Unit,
    onUpgradeClick: () -> Unit,
    onGitHubClick: () -> Unit = {},
    isGitHubConnected: Boolean = false,
    onOpenAdminDashboard: () -> Unit = {},
    /** Inline in the same row: status dot, browser, terminal, code. */
    engineBar: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Phones are narrow: below 480 dp the header switches to a compact layout.
    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 480
    val narrow = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 380
    val iconBtn = if (narrow) 28.dp else if (compact) 32.dp else 36.dp

    // Calm, solid wordmark (no animated rainbow shimmer)
    val accent = Color(0xFF7C5CE0)

    // Soft press feedback for the menu icon
    val interactionSourceMenu = remember { MutableInteractionSource() }
    val isMenuPressed by interactionSourceMenu.collectIsPressedAsState()
    val scaleMenu by animateFloatAsState(
        targetValue = if (isMenuPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "MenuPress"
    )

    // Liquid glide animation states for Upgrade button
    val upgradeScaleX = remember { Animatable(1f) }
    val upgradeScaleY = remember { Animatable(1f) }
    val glowAlpha = remember { Animatable(0f) }
    val glowScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    // Status bar safe area padding + floating margin so header NEVER touches time/battery/cutout
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 4.dp)
    ) {
        // Floating Header Capsule Bar
        val capsuleBg = if (isLightMode) Color(0xFFFFFFFF) else Color(0xFF2B2A28)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = capsuleBg,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isLightMode) Color(0xFFE5E2D6) else Color(0xFF3A3835)
            ),
            shadowElevation = if (isLightMode) 1.dp else 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            androidx.compose.foundation.layout.Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left side: Drawer Menu & Title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Menu button
                    Box(
                        modifier = Modifier
                            .size(if (compact) 38.dp else 42.dp)
                            .scale(scaleMenu)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = interactionSourceMenu,
                                indication = androidx.compose.foundation.LocalIndication.current,
                                onClick = onOpenDrawer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = SaifTheme.colors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(if (compact) 2.dp else 6.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(if (compact) 24.dp else 26.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "S",
                                color = Color.White,
                                fontSize = if (compact) 14.sp else 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SAIF AI",
                            style = TextStyle(
                                color = SaifTheme.colors.textPrimary,
                                fontSize = if (compact) 16.sp else 18.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.3.sp
                            )
                        )
                    }
                }

                // Right side: Admin crown, Upgrade, GitHub, theme, settings and the engine strip.
                // Phones are narrow and the row can hold more controls than fit, so it scrolls
                // sideways instead of squeezing them: a smooth glide shows the far end once, then
                // glides back, and the edges fade while there is more to reveal.
                val actionsScroll = rememberScrollState()
                LaunchedEffect(Unit) {
                    if (actionsScroll.maxValue > 0) {
                        kotlinx.coroutines.delay(900)
                        actionsScroll.animateScrollTo(
                            actionsScroll.maxValue,
                            tween(750, easing = FastOutSlowInEasing)
                        )
                        kotlinx.coroutines.delay(800)
                        actionsScroll.animateScrollTo(0, tween(650, easing = FastOutSlowInEasing))
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .drawWithContent {
                            drawContent()
                            val fade = 18.dp.toPx()
                            if (actionsScroll.value > 2) {
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        listOf(capsuleBg, Color.Transparent),
                                        startX = 0f, endX = fade
                                    ),
                                    size = Size(fade, size.height)
                                )
                            }
                            if (actionsScroll.value < actionsScroll.maxValue - 2) {
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        listOf(Color.Transparent, capsuleBg),
                                        startX = size.width - fade, endX = size.width
                                    ),
                                    topLeft = Offset(size.width - fade, 0f),
                                    size = Size(fade, size.height)
                                )
                            }
                        }
                ) {
                Row(
                    modifier = Modifier.horizontalScroll(actionsScroll),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 3.dp)
                ) {
                    // Admin Crown Button (Shows only for Admin role)
                    val currentAuthUser by com.example.data.local.AuthManager.currentUser.collectAsState()
                    if (currentAuthUser?.isAdmin == true) {
                        Surface(
                            onClick = onOpenAdminDashboard,
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0x336B31CF),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6B31CF).copy(alpha = 0.8f)),
                            modifier = Modifier.padding(horizontal = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AdminPanelSettings,
                                    contentDescription = "Admin",
                                    tint = Color(0xFFD4C9F8),
                                    modifier = Modifier.size(14.dp)
                                )
                                if (!compact) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Admin",
                                        color = Color(0xFFD4C9F8),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Upgrade Pill with Liquid Glide Animation
                    Box(contentAlignment = Alignment.Center) {
                        // Explosion Glow
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .scale(glowScale.value)
                                .background(Color(0xFF9B82EC).copy(alpha = glowAlpha.value), RoundedCornerShape(18.dp))
                        )

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isLightMode) Color(0x209B82EC) else Color(0x229B82EC),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x669B82EC)),
                            modifier = Modifier
                                .scale(upgradeScaleX.value, upgradeScaleY.value)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    scope.launch {
                                        // Glow blast
                                        launch {
                                            glowAlpha.snapTo(0.25f)
                                            glowScale.snapTo(1f)
                                            launch { glowScale.animateTo(1.25f, animationSpec = tween(350, easing = FastOutSlowInEasing)) }
                                            glowAlpha.animateTo(0f, animationSpec = tween(400))
                                        }

                                        // Liquid glide (Squish horizontally, then bounce back)
                                        launch { upgradeScaleX.animateTo(1.04f, animationSpec = tween(120, easing = FastOutSlowInEasing)) }
                                        upgradeScaleY.animateTo(0.96f, animationSpec = tween(120, easing = FastOutSlowInEasing))

                                        launch { upgradeScaleX.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)) }
                                        upgradeScaleY.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium))

                                        onUpgradeClick()
                                    }
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = "Upgrade",
                                    tint = Color(0xFF9B82EC),
                                    modifier = Modifier.size(14.dp)
                                )
                                if (!compact) {
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Upgrade",
                                        color = Color(0xFF9B82EC),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // Compact GitHub Connect Icon Pill right next to SAIF AI & Upgrade
                    Surface(
                        onClick = onGitHubClick,
                        shape = RoundedCornerShape(18.dp),
                        color = if (isGitHubConnected) {
                            Color(0x2410B981)
                        } else if (isLightMode) {
                            Color(0x14000000)
                        } else {
                            Color(0x1FFFFFFF)
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isGitHubConnected) Color(0x6610B981) else Color(0x409B82EC)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                        ) {
                            GitHubMarkIcon(
                                tint = if (isGitHubConnected) Color(0xFF10B981) else SaifTheme.colors.textPrimary,
                                size = 15.dp
                            )
                            if (isGitHubConnected) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF10B981), CircleShape)
                                )
                            }
                        }
                    }

                    // Theme Icon Animation
                    val themeInteractionSource = remember { MutableInteractionSource() }
                    val isThemePressed by themeInteractionSource.collectIsPressedAsState()

                    val themeScale by animateFloatAsState(
                        targetValue = if (isThemePressed) 0.9f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "ThemeScale"
                    )

                    val themeRotation by animateFloatAsState(
                        targetValue = if (isLightMode) 180f else 0f,
                        animationSpec = tween(450, easing = FastOutSlowInEasing),
                        label = "ThemeRotation"
                    )

                    IconButton(
                        onClick = onToggleTheme,
                        interactionSource = themeInteractionSource,
                        modifier = Modifier
                            .size(iconBtn)
                            .graphicsLayer {
                                scaleX = themeScale
                                scaleY = themeScale
                                rotationZ = themeRotation
                            }
                    ) {
                        AnimatedContent(
                            targetState = isLightMode,
                            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                            label = "ThemeIcon"
                        ) { light ->
                            Icon(
                                imageVector = if (light) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                contentDescription = "Theme",
                                tint = SaifTheme.colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Settings Button
                    val settingsInteraction = remember { MutableInteractionSource() }
                    val isSettingsPressed by settingsInteraction.collectIsPressedAsState()

                    val settingsScale by animateFloatAsState(
                        targetValue = if (isSettingsPressed) 0.9f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                        label = "SettingsScale"
                    )
                    val settingsRotation by animateFloatAsState(
                        targetValue = if (isSettingsPressed) 45f else 0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                        label = "SettingsRotation"
                    )

                    IconButton(
                        onClick = onOpenSettings,
                        interactionSource = settingsInteraction,
                        modifier = Modifier
                            .size(iconBtn)
                            .scale(settingsScale)
                            .graphicsLayer {
                                rotationZ = settingsRotation
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = SaifTheme.colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // No extra project-creation buttons in the header; the status, browser,
                    // terminal and code controls sit in a single row.
                    if (engineBar != null) engineBar()

                    // The red SAIF AI AUTOMATION "+" button was REMOVED in Round 9: the whole automation engine
                    // now lives inside SAIF AI's own live-conversation feature (the purple mic next to the input
                    // bar), so there is no second front door and no separate credit. `onAutomationClick` is kept
                    // only so older callers still compile; it opens the same live mode sheet.
                }
                }
            }
            }
        }
    }
}
