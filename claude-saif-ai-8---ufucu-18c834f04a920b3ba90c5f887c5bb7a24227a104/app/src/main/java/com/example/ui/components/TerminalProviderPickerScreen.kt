/*
 * Copyright (c) SAIF AI. All rights reserved.
 *
 * Original code written for SAIF AI. Distributed as part of the SAIF AI app, which ships
 * under the GNU GPL v3 (see LICENSE and THIRD_PARTY_NOTICES.md).
 */

package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.terminal.TerminalProviderProvisioner
import com.example.terminal.TerminalProviders
import com.example.ui.theme.SaifTheme

/**
 * Shown right after sign-in (or sign-up), before the main screen.
 *
 * The user picks which agent terminal SAIF AI should prepare. Tapping a provider immediately starts
 * the background preparation and drops the user on the main screen: everything that has to be
 * downloaded carries on behind a notification while they chat or sign up.
 */
@Composable
fun TerminalProviderPickerScreen(
    onPicked: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext
    LaunchedEffect(Unit) { TerminalProviders.init(app) }

    val providers = remember { TerminalProviders.all(app) }
    val status by TerminalProviderProvisioner.status.collectAsState()
    var chosen by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SaifTheme.colors.primaryBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 28.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 6 })
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF7C5CE0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("S", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "Choose your terminal",
                        color = SaifTheme.colors.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Pick the agent SAIF AI should prepare for you. Everything downloads in the "
                            + "background — tap one and keep going, you can chat while it installs.",
                        color = SaifTheme.colors.textSecondary,
                        fontSize = 13.5.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            providers.forEachIndexed { index, provider ->
                ProviderChoiceCard(
                    name = provider.name,
                    tagline = provider.tagline,
                    description = provider.description,
                    recommended = index == 0,
                    selected = chosen == provider.id,
                    busy = chosen == provider.id &&
                        status.stage != com.example.terminal.TerminalProvisionStage.READY &&
                        status.stage != com.example.terminal.TerminalProvisionStage.IDLE,
                    onClick = {
                        if (chosen != null) return@ProviderChoiceCard
                        chosen = provider.id
                        TerminalProviders.select(app, provider.id)
                        TerminalProviderProvisioner.start(app, provider.id)
                        onPicked()
                    }
                )
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(6.dp))
            TextButton(onClick = {
                TerminalProviders.select(app, TerminalProviders.BUILT_IN.first().id)
                TerminalProviderProvisioner.startForSelected(app)
                onSkip()
            }) {
                Text("Not now — use Antigravity", color = SaifTheme.colors.textSecondary, fontSize = 13.sp)
            }
        }

        // A thin progress line while the very first preparation is running.
        if (chosen != null && status.stage != com.example.terminal.TerminalProvisionStage.IDLE &&
            status.stage != com.example.terminal.TerminalProvisionStage.READY
        ) {
            LinearProgressIndicator(
                progress = { status.percent.coerceIn(0.02f, 0.98f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .height(3.dp),
                color = Color(0xFF7C5CE0)
            )
        }
    }
}

@Composable
private fun ProviderChoiceCard(
    name: String,
    tagline: String,
    description: String,
    recommended: Boolean,
    selected: Boolean,
    busy: Boolean,
    onClick: () -> Unit
) {
    val accent = Color(0xFF7C5CE0)
    val border = if (selected) accent else SaifTheme.colors.cardBorder
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) accent.copy(alpha = 0.10f) else SaifTheme.colors.surfaceCard)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = border,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Terminal,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        color = SaifTheme.colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (recommended) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accent.copy(alpha = 0.16f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Recommended", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(tagline, color = SaifTheme.colors.textSecondary, fontSize = 12.sp)
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (busy) "Preparing in the background… you can keep going." else description,
            color = SaifTheme.colors.textSecondary,
            fontSize = 12.5.sp
        )
    }
}
