/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemini.data.remote.SystemConnectionState
import com.example.gemini.data.remote.SystemStatus
import com.example.gemini.ui.chat.ChatViewModel as EngineChatViewModel
import com.example.ui.theme.SaifTheme

private val SaifPurple = Color(0xFF7C5CE0)

/**
 * The centre of an empty SAIF AI chat.
 *  - not signed in  -> "Sign in to SAIF AI" + Sign In button
 *  - connecting     -> a small spinner with the step
 *  - offline/error  -> what is wrong + Reconnect
 *  - signed in      -> just the glowing SAIF AI mark (no greeting text)
 */
@Composable
fun SaifEmptyState(
    status: SystemStatus,
    isNetworkConnected: Boolean,
    isAuthBusy: Boolean,
    onSignIn: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = SaifTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when {
            !isNetworkConnected -> {
                SaifMarkCircle(icon = Icons.Outlined.CloudOff, tint = Color(0xFFEF4444))
                Spacer(Modifier.height(16.dp))
                Text(
                    "You seem to be offline",
                    color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Connect to the internet to sign in and chat with SAIF AI.",
                    color = c.textSecondary, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center
                )
            }

            status == SystemStatus.UNAUTHENTICATED -> {
                SaifMarkCircle(icon = Icons.Default.Person, tint = SaifPurple)
                Spacer(Modifier.height(16.dp))
                Text(
                    "Sign in to SAIF AI",
                    color = c.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Please sign in to send messages, manage chat history, and interact with your workspace.",
                    color = c.textSecondary, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = onSignIn,
                    enabled = !isAuthBusy,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SaifPurple, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)
                ) {
                    if (isAuthBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Outlined.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Sign In", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }

            status == SystemStatus.CHECKING_AUTH || status == SystemStatus.ACQUIRING_CSRF || status == SystemStatus.STARTING -> {
                CircularProgressIndicator(modifier = Modifier.size(30.dp), strokeWidth = 3.dp, color = SaifPurple)
                Spacer(Modifier.height(16.dp))
                Text(
                    when (status) {
                        SystemStatus.CHECKING_AUTH -> "Checking your sign-in…"
                        SystemStatus.ACQUIRING_CSRF -> "Almost ready…"
                        else -> "Starting SAIF AI…"
                    },
                    color = c.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center
                )
            }

            status == SystemStatus.OFFLINE || status == SystemStatus.ERROR -> {
                SaifMarkCircle(icon = Icons.Outlined.CloudOff, tint = Color(0xFF6B31CF))
                Spacer(Modifier.height(16.dp))
                Text(
                    if (status == SystemStatus.ERROR) "SAIF AI hit a connection error" else "SAIF AI is not connected",
                    color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tap Reconnect to start it again.",
                    color = c.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                OutlinedButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, SaifPurple.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Text("Reconnect", color = SaifPurple, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }

            else -> {
                SaifMarkCircle(icon = Icons.Outlined.AutoAwesome, tint = SaifPurple)
            }
        }
    }
}

@Composable
private fun SaifMarkCircle(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    val transition = rememberInfiniteTransition(label = "saif_mark")
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "saif_mark_pulse"
    )
    Box(
        modifier = Modifier
            .size(64.dp)
            .scale(pulse)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(32.dp))
    }
}

/**
 * Connection / account card, opened by the small status dot in the header and by the account chip in the drawer.
 * Everything the old engine screen offered for this (sign in, sign out, reconnect, restart) lives here.
 */
@Composable
fun SaifConnectionDialog(
    engineVm: EngineChatViewModel,
    onDismiss: () -> Unit
) {
    val c = SaifTheme.colors
    val context = LocalContext.current
    val connection by engineVm.systemConnectionState.collectAsState()
    val authInfo by engineVm.agyAuthInfo.collectAsState()
    val authBusy by engineVm.isAuthBusy.collectAsState()
    val status = connection.status
    val signedIn = status == SystemStatus.READY
    val appUser by com.example.data.local.AuthManager.currentUser.collectAsState()
    val accountIssue = if (signedIn) com.example.gemini.data.remote.GoogleAccountBinding.denialReason(appUser, authInfo) else null
    val name = authInfo.displayName.ifBlank { authInfo.fullName }.ifBlank { authInfo.email }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceCard,
        titleContentColor = c.textPrimary,
        textContentColor = c.textSecondary,
        shape = RoundedCornerShape(22.dp),
        title = { Text("SAIF AI connection", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(11.dp).background(status.dotColor, CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text(if (accountIssue != null) "Account verification needed" else status.label,
                        color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                if (accountIssue != null) {
                    Text(accountIssue, color = Color(0xFFEF4444), fontSize = 13.5.sp)
                }
                if (signedIn && name.isNotBlank()) {
                    Text("Signed in as $name", fontSize = 13.5.sp)
                    if (authInfo.email.isNotBlank() && authInfo.email != name) {
                        Text(authInfo.email, fontSize = 12.5.sp)
                    }
                } else if (status == SystemStatus.UNAUTHENTICATED) {
                    Text("You are not signed in yet.", fontSize = 13.5.sp)
                } else if (status == SystemStatus.OFFLINE || status == SystemStatus.ERROR) {
                    Text("SAIF AI is not connected. Reconnect, or restart the local server.", fontSize = 13.5.sp)
                }
            }
        },
        confirmButton = {
            when (status) {
                SystemStatus.UNAUTHENTICATED -> Button(
                    onClick = { engineVm.loginToAgyHub(force = true); onDismiss() },
                    enabled = !authBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = SaifPurple, contentColor = Color.White)
                ) { Text("Sign In") }

                SystemStatus.READY -> Row {
                    if (accountIssue != null) {
                        TextButton(onClick = { engineVm.loginToAgyHub(force = true); onDismiss() }) {
                            Text("Sign in for this account", color = SaifPurple)
                        }
                    }
                    TextButton(onClick = { engineVm.logoutFromAgyHub(); onDismiss() }) {
                        Text("Sign out Antigravity", color = Color(0xFFEF4444))
                    }
                }

                SystemStatus.OFFLINE, SystemStatus.ERROR -> Button(
                    onClick = {
                        engineVm.retryConnections()
                        com.example.gemini.data.local.LocalServerManager.restartServer(context.applicationContext)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SaifPurple, contentColor = Color.White)
                ) { Text("Reconnect") }

                else -> TextButton(onClick = { engineVm.retryConnections(); onDismiss() }) {
                    Text("Refresh", color = SaifPurple)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = c.textSecondary) }
        }
    )
}

/** The browser sign-in confirmations the engine used to show itself, now in SAIF AI's own dialog style. */
@Composable
fun SaifEngineLoginDialogs(engineVm: EngineChatViewModel) {
    val c = SaifTheme.colors
    val context = LocalContext.current
    val pendingLoginUrl by engineVm.pendingLoginUrl.collectAsState()
    val loginIssue by engineVm.agyLoginIssue.collectAsState()

    val openUrl: (String) -> Unit = { url ->
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        } catch (_: Exception) {
        }
    }

    val targetUrl = pendingLoginUrl
    if (targetUrl != null) {
        AlertDialog(
            onDismissRequest = { engineVm.clearPendingLoginUrl() },
            containerColor = c.surfaceCard,
            titleContentColor = c.textPrimary,
            textContentColor = c.textSecondary,
            shape = RoundedCornerShape(22.dp),
            title = { Text("Connect Antigravity", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = { Text("Antigravity uses a separate Google sign-in inside SAIF AI. Open the browser to authorize this connection?", fontSize = 14.sp) },
            confirmButton = {
                Button(
                    onClick = { openUrl(targetUrl); engineVm.clearPendingLoginUrl() },
                    colors = ButtonDefaults.buttonColors(containerColor = SaifPurple, contentColor = Color.White)
                ) { Text("Open Browser", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { engineVm.clearPendingLoginUrl() }) { Text("Cancel", color = c.textSecondary) }
            }
        )
    }

    val issue = loginIssue
    if (issue != null) {
        AlertDialog(
            onDismissRequest = { engineVm.clearAgyLoginIssue() },
            containerColor = c.surfaceCard,
            titleContentColor = c.textPrimary,
            textContentColor = c.textSecondary,
            shape = RoundedCornerShape(22.dp),
            title = { Text("Account verification required", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = { Text(issue.message, fontSize = 14.sp) },
            confirmButton = {
                Button(
                    onClick = { openUrl(issue.verificationUrl); engineVm.clearAgyLoginIssue() },
                    colors = ButtonDefaults.buttonColors(containerColor = SaifPurple, contentColor = Color.White)
                ) { Text("Verify Account", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { engineVm.clearAgyLoginIssue() }) { Text("Cancel", color = c.textSecondary) }
            }
        )
    }
}
