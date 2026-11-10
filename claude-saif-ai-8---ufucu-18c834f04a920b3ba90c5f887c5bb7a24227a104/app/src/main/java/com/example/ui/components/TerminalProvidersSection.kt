/*
 * Copyright (c) SAIF AI. All rights reserved.
 *
 * Original code written for SAIF AI. Distributed as part of the SAIF AI app, which ships
 * under the GNU GPL v3 (see LICENSE and THIRD_PARTY_NOTICES.md).
 */

package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.terminal.TerminalProvider
import com.example.terminal.TerminalProviderKind
import com.example.terminal.TerminalProviderProvisioner
import com.example.terminal.TerminalProviders
import com.example.ui.theme.SaifTheme

/**
 * Settings → TERMINAL PROVIDERS.
 *
 * The user picks which agent terminal SAIF AI prepares: Antigravity, OpenCode, Arena AI, Claude
 * Code, Codex, or any provider they add themselves with its own base URL, model and API key.
 * Preparation always runs in the background, so nothing here blocks the app.
 */
@Composable
fun TerminalProvidersSection(glassBorderBrush: Brush) {
    val context = LocalContext.current
    val app = context.applicationContext
    androidx.compose.runtime.LaunchedEffect(Unit) { TerminalProviders.init(app) }

    var refreshToken by remember { mutableStateOf(0) }
    val providers = remember(refreshToken) { TerminalProviders.all(app) }
    var selectedId by remember(refreshToken) { mutableStateOf(TerminalProviders.selected(app).id) }
    val status by TerminalProviderProvisioner.status.collectAsState()
    var editing by remember { mutableStateOf<TerminalProvider?>(null) }
    var adding by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LiquidSurface(
        color = SaifTheme.colors.surfaceCard,
        glassBorderBrush = glassBorderBrush,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            providers.forEachIndexed { index, provider ->
                if (index > 0) HorizontalDivider(color = SaifTheme.colors.cardBorder)
                ProviderRow(
                    provider = provider,
                    isSelected = provider.id == selectedId,
                    isBusy = status.providerId == provider.id &&
                        status.stage != com.example.terminal.TerminalProvisionStage.READY &&
                        status.stage != com.example.terminal.TerminalProvisionStage.IDLE,
                    onSelect = {
                        TerminalProviders.select(app, provider.id)
                        selectedId = provider.id
                        TerminalProviderProvisioner.start(app, provider.id)
                        message = "${provider.name} selected — preparing in the background."
                    },
                    onEdit = { editing = provider }
                )
            }

            HorizontalDivider(color = SaifTheme.colors.cardBorder)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { adding = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(Color(0xFF7C5CE0).copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = null,
                        tint = Color(0xFF7C5CE0), modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Add your own provider", color = SaifTheme.colors.textPrimary,
                        fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("Any OpenAI-compatible or Anthropic endpoint with its API key",
                        color = SaifTheme.colors.textSecondary, fontSize = 12.sp)
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null,
                    tint = SaifTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }

    Spacer(Modifier.height(10.dp))

    // Live progress of the background preparation.
    if (status.stage != com.example.terminal.TerminalProvisionStage.IDLE) {
        LiquidSurface(
            color = SaifTheme.colors.surfaceCard,
            glassBorderBrush = glassBorderBrush,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Terminal, contentDescription = null,
                        tint = Color(0xFF7C5CE0), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = providers.firstOrNull { it.id == status.providerId }?.name
                            ?: "Terminal provider",
                        color = SaifTheme.colors.textPrimary,
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(status.detail, color = SaifTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                if (status.stage == com.example.terminal.TerminalProvisionStage.READY) {
                    LinearProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF10B981)
                    )
                } else if (status.stage != com.example.terminal.TerminalProvisionStage.FAILED) {
                    LinearProgressIndicator(
                        progress = { status.percent },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF7C5CE0)
                    )
                } else {
                    OutlinedButton(
                        onClick = { TerminalProviderProvisioner.start(app, status.providerId) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Retry preparation") }
                }
            }
        }
    }

    message?.let { text ->
        Spacer(Modifier.height(8.dp))
        Text(text, color = SaifTheme.colors.textSecondary, fontSize = 12.sp)
    }

    if (adding) {
        CustomProviderDialog(
            onDismiss = { adding = false },
            onSaved = { provider ->
                adding = false
                TerminalProviders.select(app, provider.id)
                selectedId = provider.id
                TerminalProviderProvisioner.start(app, provider.id)
                refreshToken++
                message = "${provider.name} added — preparing in the background."
            }
        )
    }

    editing?.let { provider ->
        ProviderDetailsDialog(
            provider = provider,
            onDismiss = { editing = null },
            onChanged = {
                editing = null
                refreshToken++
                TerminalProviderProvisioner.start(app, provider.id)
            },
            onDeleted = {
                TerminalProviderProvisioner.revokeTerminalKey(app, provider.id)
                TerminalProviders.removeCustom(app, provider.id)
                editing = null
                refreshToken++
                selectedId = TerminalProviders.selected(app).id
            }
        )
    }
}

@Composable
private fun ProviderRow(
    provider: TerminalProvider,
    isSelected: Boolean,
    isBusy: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(
                    if (isSelected) Color(0xFF7C5CE0).copy(alpha = 0.18f)
                    else SaifTheme.colors.cardBorder.copy(alpha = 0.5f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Active",
                    tint = Color(0xFF7C5CE0), modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.Outlined.Terminal, contentDescription = null,
                    tint = SaifTheme.colors.textSecondary, modifier = Modifier.size(17.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(provider.name, color = SaifTheme.colors.textPrimary,
                fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                text = when {
                    isBusy -> "Preparing in the background…"
                    !provider.needsApiKey() -> provider.tagline
                    TerminalProviders.apiKey(LocalContext.current.applicationContext, provider.id).isBlank() ->
                        "${provider.tagline} · API key needed"
                    else -> provider.tagline
                },
                color = SaifTheme.colors.textSecondary, fontSize = 12.sp
            )
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = "Settings",
                tint = SaifTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

/** Edits one provider: base URL, model, API key, terminal access, re-run preparation, delete. */
@Composable
private fun ProviderDetailsDialog(
    provider: TerminalProvider,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext
    var url by remember { mutableStateOf(provider.apiBaseUrl) }
    var model by remember { mutableStateOf(provider.defaultModel) }
    var key by remember { mutableStateOf(TerminalProviders.apiKey(app, provider.id)) }
    var shareKey by remember { mutableStateOf(TerminalProviders.isTerminalKeyShared(app, provider.id)) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = SaifTheme.colors.surfaceCard,
        modifier = Modifier.fillMaxWidth(0.94f)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(provider.name, color = SaifTheme.colors.textPrimary,
                fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(provider.description, color = SaifTheme.colors.textSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))

            if (provider.needsApiKey()) {
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = model, onValueChange = { model = it },
                    label = { Text("Model") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = key, onValueChange = { key = it },
                    label = { Text(provider.authHint.ifBlank { "API key" }) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Give the terminal this key", color = SaifTheme.colors.textPrimary,
                            fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("Copies the key into the Linux sandbox so the '${provider.cliCommand}' "
                            + "command can use it. Turn off to remove it again.",
                            color = SaifTheme.colors.textSecondary, fontSize = 11.5.sp)
                    }
                    Switch(checked = shareKey, onCheckedChange = { shareKey = it })
                }
            } else {
                Text("This provider uses SAIF AI's own sign-in, so there is no key to store here.",
                    color = SaifTheme.colors.textSecondary, fontSize = 12.5.sp)
            }

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Color(0xFFEF4444), fontSize = 12.sp)
            }

            Spacer(Modifier.height(18.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (provider.isCustom) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = {
                    try {
                        if (provider.needsApiKey()) {
                            if (url.isNotBlank() && !url.trim().startsWith("http")) {
                                error = "The base URL must start with http:// or https://"
                                return@Button
                            }
                            TerminalProviders.setBaseUrl(app, provider.id, url.trim().trimEnd('/'))
                            TerminalProviders.setModel(app, provider.id, model.trim())
                            TerminalProviders.setApiKey(app, provider.id, key.trim())
                            TerminalProviders.setTerminalKeyShared(app, provider.id, shareKey)
                            if (!shareKey) TerminalProviderProvisioner.revokeTerminalKey(app, provider.id)
                        }
                        onChanged()
                    } catch (t: Throwable) {
                        error = t.message ?: "Could not save"
                    }
                }) { Text("Save & prepare") }
            }
        }
    }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove ${provider.name}?") },
            text = { Text("Its API key and its '${provider.cliCommand}' command are removed from this device.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDeleted() }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

/** Adds a provider the user types in: name, command, endpoint kind, base URL, model and key. */
@Composable
fun CustomProviderDialog(
    onDismiss: () -> Unit,
    onSaved: (TerminalProvider) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext
    var name by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var anthropic by remember { mutableStateOf(false) }
    var shareKey by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = SaifTheme.colors.surfaceCard,
        modifier = Modifier.fillMaxWidth(0.94f)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Add a provider", color = SaifTheme.colors.textPrimary,
                fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("SAIF AI installs it into the terminal in the background, then you can use it from chat.",
                color = SaifTheme.colors.textSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(value = name, onValueChange = { name = it },
                label = { Text("Provider name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = command, onValueChange = { command = it },
                label = { Text("Terminal command") },
                placeholder = { Text(TerminalProviders.commandFor(name)) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = url, onValueChange = { url = it },
                label = { Text("Base URL") },
                placeholder = { Text("https://api.example.com/v1") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = model, onValueChange = { model = it },
                label = { Text("Model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = key, onValueChange = { key = it },
                label = { Text("API key") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Anthropic-style API", color = SaifTheme.colors.textPrimary, fontSize = 14.sp)
                    Text("Turn on for /messages endpoints; off for OpenAI-compatible /chat/completions.",
                        color = SaifTheme.colors.textSecondary, fontSize = 11.5.sp)
                }
                Switch(checked = anthropic, onCheckedChange = { anthropic = it })
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Give the terminal this key", color = SaifTheme.colors.textPrimary, fontSize = 14.sp)
                    Text("Needed for the command line; the agent never needs it.",
                        color = SaifTheme.colors.textSecondary, fontSize = 11.5.sp)
                }
                Switch(checked = shareKey, onCheckedChange = { shareKey = it })
            }

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Color(0xFFEF4444), fontSize = 12.sp)
            }

            Spacer(Modifier.height(18.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(onClick = {
                    try {
                        val created = TerminalProviders.addCustom(
                            context = app,
                            name = name,
                            command = command,
                            baseUrl = url,
                            model = model,
                            kind = if (anthropic) TerminalProviderKind.ANTHROPIC else TerminalProviderKind.OPENAI_COMPATIBLE,
                            apiKey = key
                        )
                        TerminalProviders.setTerminalKeyShared(app, created.id, shareKey)
                        onSaved(created)
                    } catch (t: Throwable) {
                        error = t.message ?: "Could not add the provider"
                    }
                }) { Text("Add & prepare") }
            }
        }
    }
    }
}
