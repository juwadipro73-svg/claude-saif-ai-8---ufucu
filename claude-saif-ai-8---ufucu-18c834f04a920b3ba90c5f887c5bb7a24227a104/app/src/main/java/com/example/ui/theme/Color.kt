/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Hardcoded core colors (used for gradients/accents)
val PrimaryAccent = Color(0xFF7C5CE0)     // SAIF purple
val PrimaryAccentDark = Color(0xFF5E43BD) // SAIF purple (pressed)
val SecondaryViolet = Color(0xFF7C5CE0)   // Violet
val SecondaryIndigo = Color(0xFF6F52D8)   // Indigo

// Bubble Colors
val UserBubbleStart = Color(0xFF5E43BD)
val UserBubbleEnd = Color(0xFF4C35A0)
val UserBubbleText = Color(0xFFFFFFFF)

val StatusSuccess = Color(0xFF10B981)
val StatusWarning = Color(0xFF6B31CF)
val StatusError = Color(0xFFEF4444)

// Gradient Brushes
val SaifAuraGradient = Brush.horizontalGradient(
    listOf(Color(0xFF7C5CE0), Color(0xFF9B82EC), Color(0xFF7C5CE0))
)

val SaifUserBubbleBrush = Brush.linearGradient(
    listOf(Color(0xFF5E43BD), Color(0xFF4C35A0))
)

val SaifCardGradient = Brush.verticalGradient(
    listOf(Color(0xFF2B2A28), Color(0xFF242321))
)
val SubtextMuted = Color(0xFF6B6962) // Subtext & Timestamps
