/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemini.data.remote.AgyHubClient
import com.example.ui.theme.SaifTheme

/**
 * Small settings that belong to the SAIF ADVANCE EDITING ENGINE side of the app.
 * Stored in the same "SettingsPrefs" file the rest of SAIF's settings use.
 */
object SaifEngineSettings {
    private const val PREFS = "SettingsPrefs"
    private const val KEY_AUTO_APPROVE = "autoApproveCommands"

    /** ON: the agent runs commands without asking. OFF (default): the user is asked to allow each command. */
    fun isAutoApprove(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_APPROVE, false)

    fun setAutoApprove(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO_APPROVE, enabled).apply()
    }
}

/**
 * Status dot, browser, terminal and code-editor buttons, placed inline in SAIF's single header row.
 */
@Composable
fun SaifEngineToolbar(
    statusColor: Color,
    onStatusClick: () -> Unit,
    onBrowserClick: () -> Unit,
    onTerminalClick: () -> Unit,
    onCodeClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: androidx.compose.ui.unit.Dp = 32.dp
) {
    val c = SaifTheme.colors
    val accent = Color(0xFF9B82EC)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(buttonSize).clip(CircleShape).clickable { onStatusClick() },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(10.dp).background(statusColor, CircleShape))
        }
        Box(
            modifier = Modifier.size(buttonSize).clip(CircleShape).clickable { onBrowserClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Language, contentDescription = "Browser", tint = c.textPrimary.copy(alpha = 0.85f), modifier = Modifier.size(20.dp))
        }
        Box(
            modifier = Modifier.size(buttonSize).clip(CircleShape).clickable { onTerminalClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Terminal, contentDescription = "Terminal", tint = accent, modifier = Modifier.size(20.dp))
        }
    }
}

/** Model chip that sits in the chatbox: sparkle, model name, remaining quota, arrow. */
@Composable
fun SaifModelChip(
    label: String,
    quotaPercent: Int?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = SaifTheme.colors
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (c.isLight) Color(0xFFFAF9F5) else Color(0xFF1F1E1D),
        border = BorderStroke(1.dp, c.textPrimary.copy(alpha = 0.10f)),
        modifier = modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(18.dp)).clickable(enabled = enabled) { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Color(0xFFA07CDE), modifier = Modifier.size(15.dp))
            Spacer(Modifier.size(6.dp))
            Text(
                text = label,
                color = c.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (quotaPercent != null) {
                Spacer(Modifier.size(6.dp))
                val quotaColor = when {
                    quotaPercent >= 50 -> Color(0xFF22C55E)
                    quotaPercent >= 20 -> Color(0xFF6B31CF)
                    else -> Color(0xFFEF4444)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(quotaColor.copy(alpha = 0.16f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text("$quotaPercent%", color = quotaColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

/** The engine's Google account chip (avatar with a status dot and the account name), used in SAIF's history drawer. */
@Composable
fun SaifEngineAccountChip(
    authInfo: AgyHubClient.AgyAuthInfo,
    statusColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = SaifTheme.colors
    val name = authInfo.displayName.ifBlank { authInfo.fullName }.ifBlank { authInfo.email }.ifBlank { "Sign in" }
    val avatarModel = remember(authInfo.profilePictureUrl) { com.example.gemini.ui.drawer.parseProfileAvatarModel(authInfo.profilePictureUrl) }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (c.isLight) Color(0xFFF5F4EE) else Color(0xFF2F2D2B),
        border = BorderStroke(1.dp, if (c.isLight) Color(0xFFE5E2D6) else Color(0xFF383632)),
        modifier = modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(start = 5.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(38.dp)) {
                Box(
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF7C5CE0).copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarModel != null) {
                        coil.compose.AsyncImage(
                            model = avatarModel,
                            contentDescription = name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(38.dp).clip(CircleShape)
                        )
                    } else {
                        Text(name.take(1).uppercase(), color = c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(12.dp)
                        .background(if (c.isLight) Color.White else Color(0xFF2F2D2B), CircleShape)
                        .padding(2.dp)
                        .background(statusColor, CircleShape)
                )
            }
            Spacer(Modifier.size(10.dp))
            Text(
                text = name,
                color = c.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp)
            )
        }
    }
}
