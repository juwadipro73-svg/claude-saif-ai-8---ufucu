/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun SaifAiTheme(
    isLightMode: Boolean = false,
    content: @Composable () -> Unit
) {
    AnimatedSaifTheme(isLightMode = isLightMode) {
        val colorScheme = if (isLightMode) {
            lightColorScheme(
                primary = PrimaryAccent,
                onPrimary = UserBubbleText,
                background = SaifTheme.colors.primaryBackground,
                surface = SaifTheme.colors.surfaceCard,
                onSurface = SaifTheme.colors.textPrimary
            )
        } else {
            darkColorScheme(
                primary = PrimaryAccent,
                onPrimary = UserBubbleText,
                background = SaifTheme.colors.primaryBackground,
                surface = SaifTheme.colors.surfaceCard,
                onSurface = SaifTheme.colors.textPrimary
            )
        }

        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = SaifShapes,
            content = content
        )
    }
}
