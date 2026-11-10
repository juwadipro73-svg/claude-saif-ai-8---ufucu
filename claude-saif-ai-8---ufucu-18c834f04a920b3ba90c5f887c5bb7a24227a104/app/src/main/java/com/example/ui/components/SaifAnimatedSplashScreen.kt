/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SaifAnimatedSplashOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(animationSpec = tween(420, easing = FastOutSlowInEasing)) +
            scaleOut(targetScale = 1.06f, animationSpec = tween(420, easing = FastOutSlowInEasing)),
        modifier = modifier.fillMaxSize()
    ) {
        val logoScale = remember { Animatable(0.55f) }
        val logoAlpha = remember { Animatable(0f) }
        val titleAlpha = remember { Animatable(0f) }
        val titleOffsetY = remember { Animatable(18f) }
        val barProgress = remember { Animatable(0.05f) }

        LaunchedEffect(Unit) {
            logoAlpha.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
        }
        LaunchedEffect(Unit) {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        LaunchedEffect(Unit) {
            delay(180)
            titleAlpha.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        }
        LaunchedEffect(Unit) {
            delay(180)
            titleOffsetY.animateTo(0f, tween(380, easing = FastOutSlowInEasing))
        }
        LaunchedEffect(Unit) {
            barProgress.animateTo(1f, tween(1650, easing = FastOutSlowInEasing))
        }

        val infinite = rememberInfiniteTransition(label = "saif_splash_infinite")
        val orbitAngle by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(2600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "orbit_angle"
        )
        val pulseGlow by infinite.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(1100, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_glow"
        )
        val shimmerSweep by infinite.animateFloat(
            initialValue = -1f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmer_sweep"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF090812)),
            contentAlignment = Alignment.Center
        ) {
            // Ambient Aurora Glow Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height * 0.45f)
                val radius = size.minDimension * 0.55f * pulseGlow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF7C5CE0).copy(alpha = 0.28f),
                            Color(0xFF4F46E5).copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                // Orbital Energy Rings + Emblem Badge
                Box(
                    modifier = Modifier
                        .size(144.dp)
                        .scale(logoScale.value)
                        .alpha(logoAlpha.value),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val ringStroke = 2.6.dp.toPx()
                        val outerPad = 8.dp.toPx()
                        val innerPad = 19.dp.toPx()

                        rotate(degrees = orbitAngle) {
                            drawArc(
                                brush = Brush.sweepGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color(0xFF9B82EC).copy(alpha = 0.25f),
                                        Color(0xFF8B5CF6),
                                        Color(0xFF38BDF8),
                                        Color.Transparent
                                    )
                                ),
                                startAngle = 0f,
                                sweepAngle = 250f,
                                useCenter = false,
                                topLeft = Offset(outerPad, outerPad),
                                size = Size(size.width - outerPad * 2, size.height - outerPad * 2),
                                style = Stroke(width = ringStroke, cap = StrokeCap.Round)
                            )
                        }

                        rotate(degrees = -orbitAngle * 1.35f) {
                            drawArc(
                                brush = Brush.sweepGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color(0xFFA78BFA),
                                        Color(0xFFE879F9),
                                        Color.Transparent
                                    )
                                ),
                                startAngle = 60f,
                                sweepAngle = 190f,
                                useCenter = false,
                                topLeft = Offset(innerPad, innerPad),
                                size = Size(size.width - innerPad * 2, size.height - innerPad * 2),
                                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }

                    // Center SAIF AI Crystal Badge
                    Box(
                        modifier = Modifier
                            .size(78.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF8B5CF6),
                                        Color(0xFF5B3CC4),
                                        Color(0xFF312E81)
                                    )
                                )
                            )
                            .border(
                                width = 1.5.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.55f),
                                        Color(0xFF9B82EC).copy(alpha = 0.2f)
                                    )
                                ),
                                shape = RoundedCornerShape(24.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // Specular diagonal sheen
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val startX = size.width * shimmerSweep
                            drawLine(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.24f),
                                        Color.Transparent
                                    ),
                                    start = Offset(startX - 40f, 0f),
                                    end = Offset(startX + 40f, size.height)
                                ),
                                start = Offset(startX - 30f, 0f),
                                end = Offset(startX + 30f, size.height),
                                strokeWidth = 36f
                            )
                        }
                        Text(
                            text = "S",
                            color = Color.White,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .alpha(titleAlpha.value)
                        .offset(y = titleOffsetY.value.dp)
                ) {
                    Text(
                        text = "SAIF AI",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 3.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "INTELLIGENT AUTONOMOUS WORKSPACE",
                        color = Color(0xFFB8A9F5),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.8.sp
                    )
                    Spacer(Modifier.height(28.dp))

                    // Sleek Animated Progress Track
                    Box(
                        modifier = Modifier
                            .width(156.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(barProgress.value.coerceIn(0.05f, 1f))
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color(0xFF7C5CE0),
                                            Color(0xFFA78BFA),
                                            Color(0xFF38BDF8)
                                        )
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}
