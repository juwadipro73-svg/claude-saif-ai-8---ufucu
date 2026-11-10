/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

data class SaifColors(
    val primaryBackground: Color,
    val surfaceCard: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val aiBubbleBackground: Color,
    val aiBubbleBorder: Color,
    val aiBubbleText: Color,
    val codeBackground: Color,
    val codeBorder: Color,
    val codeText: Color,
    val isLight: Boolean
)

val DarkColors = SaifColors(
    primaryBackground = Color(0xFF1F1E1D),
    surfaceCard = Color(0xFF2B2A28),
    cardBorder = Color(0xFF3A3835),
    textPrimary = Color(0xFFF5F4EE),
    textSecondary = Color(0xFFA8A59B),
    aiBubbleBackground = Color(0xFF2B2A28),
    aiBubbleBorder = Color(0xFF3A3835),
    aiBubbleText = Color(0xFFF5F4EE),
    codeBackground = Color(0xFF262523),
    codeBorder = Color(0xFF3A3835),
    codeText = Color(0xFFE5E2D6),
    isLight = false
)

val LightColors = SaifColors(
    primaryBackground = Color(0xFFFAF9F5),
    surfaceCard = Color(0xFFFAF9F5),
    cardBorder = Color(0xFFE5E2D6),
    textPrimary = Color(0xFF1F1E1D),
    textSecondary = Color(0xFF6B6962),
    aiBubbleBackground = Color(0xFFF5F4EE),
    aiBubbleBorder = Color(0xFFE5E2D6),
    aiBubbleText = Color(0xFF1F1E1D),
    codeBackground = Color(0xFFFAF9F5),
    codeBorder = Color(0xFFE5E2D6),
    codeText = Color(0xFF4A4843),
    isLight = true
)

val LocalSaifColors = compositionLocalOf { DarkColors }

object SaifTheme {
    val colors: SaifColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSaifColors.current
}

@Composable
fun AnimatedSaifTheme(
    isLightMode: Boolean,
    content: @Composable () -> Unit
) {
    val targetColors = if (isLightMode) LightColors else DarkColors
    val animSpec = tween<Color>(300)
    
    val primaryBackground by animateColorAsState(targetColors.primaryBackground, animSpec, label = "bg")
    val surfaceCard by animateColorAsState(targetColors.surfaceCard, animSpec, label = "card")
    val cardBorder by animateColorAsState(targetColors.cardBorder, animSpec, label = "border")
    val textPrimary by animateColorAsState(targetColors.textPrimary, animSpec, label = "textP")
    val textSecondary by animateColorAsState(targetColors.textSecondary, animSpec, label = "textS")
    val aiBubbleBackground by animateColorAsState(targetColors.aiBubbleBackground, animSpec, label = "aiBg")
    val aiBubbleBorder by animateColorAsState(targetColors.aiBubbleBorder, animSpec, label = "aiBorder")
    val aiBubbleText by animateColorAsState(targetColors.aiBubbleText, animSpec, label = "aiText")
    val codeBackground by animateColorAsState(targetColors.codeBackground, animSpec, label = "codeBg")
    val codeBorder by animateColorAsState(targetColors.codeBorder, animSpec, label = "codeBorder")
    val codeText by animateColorAsState(targetColors.codeText, animSpec, label = "codeText")

    val animatedColors = SaifColors(
        primaryBackground = primaryBackground,
        surfaceCard = surfaceCard,
        cardBorder = cardBorder,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        aiBubbleBackground = aiBubbleBackground,
        aiBubbleBorder = aiBubbleBorder,
        aiBubbleText = aiBubbleText,
        codeBackground = codeBackground,
        codeBorder = codeBorder,
        codeText = codeText,
        isLight = isLightMode
    )

    CompositionLocalProvider(LocalSaifColors provides animatedColors) {
        content()
    }
}
