/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import com.example.ui.theme.SaifTheme

@Composable
fun QuickPromptChips(
    currentMode: String,
    onSelectPrompt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = Color(0xFF7C5CE0)
    val isLightMode = com.example.ui.theme.SaifTheme.colors.isLight

    // Staggered entrance: hero first, then each suggestion card slides up in turn.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        val infiniteTransition = rememberInfiniteTransition(label = "hero")
        val breathe by infiniteTransition.animateFloat(
            initialValue = 0.985f,
            targetValue = 1.015f,
            animationSpec = infiniteRepeatable(
                animation = tween(3200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "hero_breathe"
        )
        val glowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.10f,
            targetValue = 0.24f,
            animationSpec = infiniteRepeatable(
                animation = tween(3200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "hero_glow"
        )

        AnimatedVisibility(
            visible = entered,
            enter = fadeIn(tween(450)) + slideInVertically(tween(450, easing = FastOutSlowInEasing)) { it / 6 }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Hero mark: soft single-colour halo, gentle breathing only
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.scale(breathe)
                ) {
                    Box(
                        modifier = Modifier
                            .size(128.dp)
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                    colors = listOf(accent.copy(alpha = glowAlpha), Color.Transparent)
                                )
                            )
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (isLightMode) Color(0xFFF5F4EE) else Color(0xFF2B2A28))
                            .border(
                                1.dp,
                                if (isLightMode) Color(0xFFE5E2D6) else Color(0xFF3A3835),
                                RoundedCornerShape(22.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = "SAIF AI",
                            tint = accent,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "How can I help you today?",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp,
                    color = SaifTheme.colors.textPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Chat, create images, analyze photos, write code\nor start a voice conversation.",
                    fontSize = 14.sp,
                    color = SaifTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // Which APK is this? (set by CI: "build #N (commit)")
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = com.example.gemini.BuildConfig.BUILD_STAMP,
                    fontSize = 11.sp,
                    color = SaifTheme.colors.textSecondary.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        val cards = listOf(
            Triple(Icons.Outlined.AutoFixHigh, "Create an image" to "A futuristic city at night", "Generate an image of a futuristic city at night"),
            Triple(Icons.Outlined.ChatBubbleOutline, "Plan something" to "A 3-day Tokyo itinerary", "Help me plan a 3-day Tokyo itinerary"),
            Triple(Icons.Default.Code, "Explain code" to "Walk me through React hooks", "Walk me through React hooks"),
            Triple(Icons.Outlined.Lightbulb, "Chat in Hinglish" to "Switch to Hindi written in English letters", "Let's chat in Hinglish")
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            cards.forEachIndexed { index, (icon, text, prompt) ->
                AnimatedVisibility(
                    visible = entered,
                    enter = fadeIn(tween(380, delayMillis = 120 + index * 70)) +
                        slideInVertically(tween(380, delayMillis = 120 + index * 70, easing = FastOutSlowInEasing)) { it / 3 }
                ) {
                    PromptCard(
                        icon = icon,
                        title = text.first,
                        subtitle = text.second,
                        iconTint = accent,
                        onClick = { onSelectPrompt(prompt) }
                    )
                }
            }
        }
    }
}

@Composable
fun PromptCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color,
    onClick: () -> Unit
) {
    val isLightMode = com.example.ui.theme.SaifTheme.colors.isLight
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "PromptCardScale"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isLightMode) Color(0xFFFFFFFF) else Color(0xFF2B2A28),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isPressed) iconTint.copy(alpha = 0.55f)
            else if (isLightMode) Color(0xFFE5E2D6) else Color(0xFF3A3835)
        ),
        shadowElevation = if (isLightMode) 1.dp else 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .scale(cardScale)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconTint.copy(alpha = 0.12f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = SaifTheme.colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = SaifTheme.colors.textSecondary,
                    fontSize = 13.sp
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = SaifTheme.colors.textSecondary.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
