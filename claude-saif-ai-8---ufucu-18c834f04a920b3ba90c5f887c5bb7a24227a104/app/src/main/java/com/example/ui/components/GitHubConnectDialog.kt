/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.github.GitHubDeviceFlowChallenge
import com.example.github.GitHubIntegrationManager
import com.example.github.GitHubRepoSummary
import com.example.ui.theme.SaifTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Crisp GitHub mark vector icon for header pill and GitHub connection dialog. */
val GitHubMarkVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "GitHubMark",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.EvenOdd
        ) {
            moveTo(12f, 2f)
            curveTo(6.477f, 2f, 2f, 6.484f, 2f, 12.017f)
            curveTo(2f, 16.442f, 4.865f, 20.197f, 8.839f, 21.521f)
            curveTo(9.339f, 21.613f, 9.521f, 21.304f, 9.521f, 21.038f)
            curveTo(9.521f, 20.8f, 9.512f, 20.025f, 9.508f, 19.188f)
            curveTo(6.726f, 19.793f, 6.139f, 17.997f, 6.139f, 17.997f)
            curveTo(5.685f, 16.84f, 5.029f, 16.532f, 5.029f, 16.532f)
            curveTo(4.121f, 15.91f, 5.098f, 15.923f, 5.098f, 15.923f)
            curveTo(6.101f, 15.994f, 6.629f, 16.955f, 6.629f, 16.955f)
            curveTo(7.521f, 18.487f, 8.97f, 18.044f, 9.539f, 17.788f)
            curveTo(9.631f, 17.141f, 9.889f, 16.7f, 10.175f, 16.45f)
            curveTo(7.955f, 16.197f, 5.62f, 15.338f, 5.62f, 11.5f)
            curveTo(5.62f, 10.406f, 6.01f, 9.513f, 6.649f, 8.812f)
            curveTo(6.546f, 8.559f, 6.203f, 7.54f, 6.747f, 6.161f)
            curveTo(6.747f, 6.161f, 7.586f, 5.892f, 9.497f, 7.188f)
            curveTo(10.295f, 6.966f, 11.15f, 6.855f, 12f, 6.851f)
            curveTo(12.85f, 6.855f, 13.705f, 6.966f, 14.505f, 7.188f)
            curveTo(16.414f, 5.892f, 17.251f, 6.161f, 17.251f, 6.161f)
            curveTo(17.797f, 7.54f, 17.454f, 8.559f, 17.351f, 8.812f)
            curveTo(17.991f, 9.513f, 18.38f, 10.406f, 18.38f, 11.5f)
            curveTo(18.38f, 15.348f, 16.041f, 16.194f, 13.813f, 16.442f)
            curveTo(14.172f, 16.752f, 14.492f, 17.367f, 14.492f, 18.306f)
            curveTo(14.492f, 19.652f, 14.48f, 20.737f, 14.48f, 21.038f)
            curveTo(14.48f, 21.307f, 14.66f, 21.619f, 15.167f, 21.52f)
            curveTo(19.138f, 20.194f, 22f, 16.44f, 22f, 12.017f)
            curveTo(22f, 6.484f, 17.523f, 2f, 12f, 2f)
            close()
        }
    }.build()
}

@Composable
fun GitHubMarkIcon(
    tint: Color,
    size: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    Icon(
        imageVector = GitHubMarkVector,
        contentDescription = "GitHub",
        tint = tint,
        modifier = modifier.size(size)
    )
}

@Composable
fun GitHubConnectDialog(
    isLightMode: Boolean,
    onDismiss: () -> Unit,
    onOpenRepoInChat: ((repoFullName: String, linuxPath: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        GitHubIntegrationManager.init(context)
        GitHubIntegrationManager.clearMessages()
        if (GitHubIntegrationManager.state.value.isConnected &&
            GitHubIntegrationManager.state.value.repositories.isEmpty()
        ) {
            GitHubIntegrationManager.refreshRepositories(context)
        }
    }

    val state by GitHubIntegrationManager.state.collectAsState()

    var tokenInput by remember { mutableStateOf("") }
    var showTokenPlain by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Create Repo sub-panel
    var showCreateRepoPanel by remember { mutableStateOf(false) }
    var newRepoName by remember { mutableStateOf("") }
    var newRepoDesc by remember { mutableStateOf("") }
    var newRepoPrivate by remember { mutableStateOf(true) }

    // Optional OAuth Device Flow sub-panel
    var showDeviceFlowPanel by remember { mutableStateOf(false) }
    var oauthClientId by remember { mutableStateOf(GitHubIntegrationManager.getSavedOAuthClientId(context)) }
    var activeChallenge by remember { mutableStateOf<GitHubDeviceFlowChallenge?>(null) }

    LaunchedEffect(activeChallenge?.deviceCode) {
        val challenge = activeChallenge ?: return@LaunchedEffect
        val deadline = System.currentTimeMillis() + challenge.expiresInSec * 1000L
        while (System.currentTimeMillis() < deadline && !state.isConnected) {
            delay(challenge.intervalSec * 1000L)
            val poll = GitHubIntegrationManager.pollDeviceFlowOnce(context, oauthClientId, challenge.deviceCode)
            if (poll.isSuccess && poll.getOrNull() != null) {
                activeChallenge = null
                break
            } else if (poll.isFailure) {
                activeChallenge = null
                break
            }
        }
    }

    val surfaceBg = if (isLightMode) Color(0xFFFBFBFD) else Color(0xFF14131A)
    val cardBg = if (isLightMode) Color(0xFFF1F0F7) else Color(0xFF1E1C28)
    val borderCol = if (isLightMode) Color(0x22000000) else Color(0x26FFFFFF)
    val accentCol = Color(0xFF8B6CEF)
    val emeraldCol = Color(0xFF10B981)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 650.dp),
            shape = RoundedCornerShape(24.dp),
            color = surfaceBg,
            border = BorderStroke(1.dp, borderCol),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isLightMode) Color(0xFF18171C) else Color(0xFF282636)),
                        contentAlignment = Alignment.Center
                    ) {
                        GitHubMarkIcon(tint = Color.White, size = 22.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "GitHub Connect",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = SaifTheme.colors.textPrimary
                            )
                            if (state.isConnected) {
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = emeraldCol.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, emeraldCol.copy(alpha = 0.45f))
                                ) {
                                    Text(
                                        text = "Connected",
                                        color = emeraldCol,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (state.isConnected) {
                                "Synced with SAIF AI Chat, Skills & Linux Git"
                            } else {
                                "Connect your GitHub account for repos, git push/pull & skills"
                            },
                            fontSize = 12.sp,
                            color = SaifTheme.colors.textSecondary
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = SaifTheme.colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Status / Error Banner
                state.errorMessage?.let { err ->
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = err,
                            color = if (isLightMode) Color(0xFFB91C1C) else Color(0xFFFCA5A5),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                state.statusMessage?.let { msg ->
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = emeraldCol.copy(alpha = 0.13f),
                        border = BorderStroke(1.dp, emeraldCol.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            color = emeraldCol,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                if (!state.isConnected) {
                    // === DISCONNECTED FLOW ===
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = cardBg,
                            border = BorderStroke(1.dp, borderCol),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Step 1 — Generate Token on GitHub (1 Tap)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SaifTheme.colors.textPrimary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Opens GitHub with repo & workflow scopes pre-selected. Tap 'Generate token' at the bottom of the page and copy it.",
                                    fontSize = 12.sp,
                                    color = SaifTheme.colors.textSecondary
                                )
                                Spacer(Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(GitHubIntegrationManager.NEW_TOKEN_BROWSER_URL))
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }.onFailure {
                                            Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = accentCol),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Generate Token on GitHub", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = cardBg,
                            border = BorderStroke(1.dp, borderCol),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Step 2 — Paste Token & Connect",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SaifTheme.colors.textPrimary
                                    )
                                    TextButton(
                                        onClick = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            val clipText = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
                                            if (clipText.isNotBlank()) {
                                                tokenInput = clipText
                                            } else {
                                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp), tint = accentCol)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Paste", fontSize = 12.sp, color = accentCol, fontWeight = FontWeight.SemiBold)
                                    }
                                }

                                OutlinedTextField(
                                    value = tokenInput,
                                    onValueChange = { tokenInput = it },
                                    placeholder = { Text("ghp_xxxxxxxxxxxx or github_pat_xxxx", fontSize = 13.sp) },
                                    singleLine = true,
                                    visualTransformation = if (showTokenPlain) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showTokenPlain = !showTokenPlain }) {
                                            Icon(
                                                imageVector = if (showTokenPlain) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                                contentDescription = "Toggle token visibility"
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        scope.launch {
                                            GitHubIntegrationManager.connectWithToken(context, tokenInput)
                                        }
                                    },
                                    enabled = !state.isBusy && tokenInput.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = emeraldCol),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (state.isBusy) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Verifying…", color = Color.White, fontWeight = FontWeight.Bold)
                                    } else {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Connect GitHub Account", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Optional OAuth Device Flow
                        TextButton(
                            onClick = { showDeviceFlowPanel = !showDeviceFlowPanel },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(
                                text = if (showDeviceFlowPanel) "Hide OAuth Device Code Login" else "Or use GitHub OAuth App Device Code",
                                fontSize = 12.sp,
                                color = SaifTheme.colors.textSecondary
                            )
                        }

                        AnimatedVisibility(visible = showDeviceFlowPanel) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = cardBg,
                                border = BorderStroke(1.dp, borderCol),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "GitHub OAuth App Client ID",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SaifTheme.colors.textPrimary
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = oauthClientId,
                                        onValueChange = {
                                            oauthClientId = it
                                            GitHubIntegrationManager.saveOAuthClientId(context, it)
                                        },
                                        placeholder = { Text("Ov23liXXXXXXXXXXXXXX", fontSize = 12.sp) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    val challenge = activeChallenge
                                    if (challenge == null) {
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    val res = GitHubIntegrationManager.startDeviceFlow(oauthClientId)
                                                    res.onSuccess { ch ->
                                                        activeChallenge = ch
                                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                        cm?.setPrimaryClip(ClipData.newPlainText("GitHub Code", ch.userCode))
                                                        Toast.makeText(context, "Code ${ch.userCode} copied!", Toast.LENGTH_LONG).show()
                                                        runCatching {
                                                            context.startActivity(
                                                                Intent(Intent.ACTION_VIEW, Uri.parse(ch.verificationUri))
                                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                            )
                                                        }
                                                    }.onFailure { err ->
                                                        Toast.makeText(context, err.localizedMessage ?: "Device flow failed", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            },
                                            enabled = oauthClientId.trim().length >= 8,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Get Browser Login Code", fontSize = 12.sp)
                                        }
                                    } else {
                                        Text(
                                            text = "Enter code on GitHub: ${challenge.userCode}",
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = accentCol
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "Waiting for browser confirmation at ${challenge.verificationUri}…",
                                            fontSize = 11.sp,
                                            color = SaifTheme.colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // === CONNECTED ACCOUNT & REPOSITORIES VIEW ===
                    val profile = state.profile
                    if (profile != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = cardBg,
                            border = BorderStroke(1.dp, borderCol),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(accentCol.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = profile.login.take(2).uppercase(),
                                        color = accentCol,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${profile.name} (@${profile.login})",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SaifTheme.colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${profile.email} · ${profile.publicRepos + profile.privateRepos} repos",
                                        fontSize = 11.sp,
                                        color = SaifTheme.colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TextButton(
                                    onClick = { GitHubIntegrationManager.disconnect(context) }
                                ) {
                                    Text("Disconnect", color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Action Row: Search + Refresh + New Repo
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Filter repositories…", fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { scope.launch { GitHubIntegrationManager.refreshRepositories(context) } },
                            enabled = !state.isBusy
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = SaifTheme.colors.textPrimary)
                        }
                        FilledTonalButton(
                            onClick = { showCreateRepoPanel = !showCreateRepoPanel },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("New", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    AnimatedVisibility(visible = showCreateRepoPanel) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = cardBg,
                            border = BorderStroke(1.dp, borderCol),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Create New GitHub Repository", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SaifTheme.colors.textPrimary)
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = newRepoName,
                                    onValueChange = { newRepoName = it },
                                    placeholder = { Text("my-saif-project", fontSize = 12.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = newRepoDesc,
                                    onValueChange = { newRepoDesc = it },
                                    placeholder = { Text("Optional description", fontSize = 12.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = newRepoPrivate, onCheckedChange = { newRepoPrivate = it })
                                        Text("Private repository", fontSize = 12.sp, color = SaifTheme.colors.textPrimary)
                                    }
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                val res = GitHubIntegrationManager.createRepository(
                                                    context = context,
                                                    repoName = newRepoName,
                                                    description = newRepoDesc,
                                                    isPrivate = newRepoPrivate
                                                )
                                                if (res.isSuccess) {
                                                    newRepoName = ""
                                                    newRepoDesc = ""
                                                    showCreateRepoPanel = false
                                                }
                                            }
                                        },
                                        enabled = !state.isBusy && newRepoName.isNotBlank(),
                                        colors = ButtonDefaults.buttonColors(containerColor = accentCol),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Create", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    val filteredRepos = remember(state.repositories, searchQuery) {
                        val q = searchQuery.trim().lowercase()
                        if (q.isBlank()) state.repositories
                        else state.repositories.filter {
                            it.fullName.lowercase().contains(q) || it.description.lowercase().contains(q)
                        }
                    }

                    if (state.isBusy && filteredRepos.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = accentCol)
                        }
                    } else if (filteredRepos.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No repositories found",
                                fontSize = 13.sp,
                                color = SaifTheme.colors.textSecondary
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredRepos, key = { it.fullName }) { repo ->
                                GitHubRepoRow(
                                    repo = repo,
                                    clonedPath = state.clonedRepoPaths[repo.fullName],
                                    isBusy = state.isBusy,
                                    cardBg = cardBg,
                                    borderCol = borderCol,
                                    accentCol = accentCol,
                                    emeraldCol = emeraldCol,
                                    onClone = {
                                        scope.launch {
                                            GitHubIntegrationManager.cloneRepositoryToWorkspace(context, repo)
                                        }
                                    },
                                    onUseInChat = { linuxPath ->
                                        onOpenRepoInChat?.invoke(repo.fullName, linuxPath)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GitHubRepoRow(
    repo: GitHubRepoSummary,
    clonedPath: String?,
    isBusy: Boolean,
    cardBg: Color,
    borderCol: Color,
    accentCol: Color,
    emeraldCol: Color,
    onClone: () -> Unit,
    onUseInChat: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cardBg,
        border = BorderStroke(1.dp, borderCol),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = repo.fullName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = SaifTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = (if (repo.isPrivate) Color(0xFFF59E0B) else accentCol).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (repo.isPrivate) "Private" else "Public",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (repo.isPrivate) Color(0xFFF59E0B) else accentCol,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (repo.description.isNotBlank()) {
                        Text(
                            text = repo.description,
                            fontSize = 11.sp,
                            color = SaifTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val meta = listOfNotNull(
                        repo.language.takeIf { it.isNotBlank() },
                        "branch: ${repo.defaultBranch}",
                        clonedPath?.let { "synced: $it" }
                    ).joinToString(" · ")
                    Text(
                        text = meta,
                        fontSize = 10.sp,
                        color = if (clonedPath != null) emeraldCol else SaifTheme.colors.textSecondary
                    )
                }

                Spacer(Modifier.width(8.dp))

                if (clonedPath != null) {
                    Button(
                        onClick = { onUseInChat(clonedPath) },
                        colors = ButtonDefaults.buttonColors(containerColor = emeraldCol),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("Open in Chat", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    OutlinedButton(
                        onClick = onClone,
                        enabled = !isBusy,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("Clone", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
