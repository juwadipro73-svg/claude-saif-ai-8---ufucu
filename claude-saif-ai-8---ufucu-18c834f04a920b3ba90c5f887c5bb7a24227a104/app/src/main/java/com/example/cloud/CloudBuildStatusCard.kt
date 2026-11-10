/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.cloud

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.compiler.AppCompilerEngine
import com.example.compiler.LocalApkBuildState
import com.example.compiler.OnDeviceApkBuildManager
import kotlinx.coroutines.launch

/** Unified status card for On-Device APK/ZIP compilation and optional Cloud jobs. */
@Composable
fun CloudBuildStatusCard(modifier: Modifier = Modifier) {
    val localBuild by OnDeviceApkBuildManager.current.collectAsState()
    val cloudJob by SaifAiCloudManager.current.collectAsState()

    if (localBuild != null) {
        LocalApkBuildCardContent(state = localBuild!!, modifier = modifier)
        return
    }

    val state = cloudJob ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Card(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when (state.kind) {
                        CloudRequestFilter.Route.VIDEO -> "☁ Video render"
                        CloudRequestFilter.Route.ZIP -> "☁ Project ZIP"
                        CloudRequestFilter.Route.UPLOAD -> "☁ Private file upload"
                        else -> "☁ APK build"
                    },
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                if (state.status in listOf("queued", "uploading", "building")) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                IconButton(onClick = { SaifAiCloudManager.dismiss() }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                }
            }
            Text(
                state.message,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.status == "failed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            if (state.status == "ready") {
                state.downloadUrl?.let { link ->
                    Text(
                        Uri.parse(link).buildUpon().fragment(null).build().toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Button(
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open my file on SAIF AI DOWNLOADS")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.downloadUrl?.let { link ->
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("SAIF AI private file link", link))
                                Toast.makeText(context, "One-time website link copied · expires soon", Toast.LENGTH_LONG).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Copy link") }
                    }
                    OutlinedButton(
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            busy = true
                            scope.launch {
                                try {
                                    SaifAiCloudManager.refreshDownloadLink()
                                    error = null
                                } catch (e: Exception) {
                                    error = e.localizedMessage ?: "Could not refresh private link"
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    ) { Text(if (busy) "Refreshing…" else "Refresh link") }
                }
                Text(
                    "One file per link · expires after 10 minutes · one download. Only share it with someone you trust.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LocalApkBuildCardContent(state: LocalApkBuildState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isBusy = state.status == "building" || state.status == "compiling"
    Card(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = when {
                        isBusy && state.kind == "ZIP" -> "⚡ Packaging ${state.appName} ZIP…"
                        isBusy -> "⚡ Compiling ${state.appName}.apk on Phone…"
                        state.status == "ready" && state.kind == "ZIP" -> "📦 ${state.appName} ZIP Ready"
                        state.status == "ready" -> "✅ ${state.appName}.apk Ready"
                        else -> "⚠ APK Build (${state.appName})"
                    },
                    fontWeight = FontWeight.Bold,
                    color = if (state.status == "failed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isBusy) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = { OnDeviceApkBuildManager.dismiss() }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                    }
                }
            }

            Text(
                text = state.message,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.status == "failed") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )

            if (isBusy) {
                LinearProgressIndicator(
                    progress = { state.progress.coerceIn(0.05f, 0.98f) },
                    modifier = Modifier.fillMaxWidth().height(5.dp)
                )
            }

            if (state.status == "ready") {
                val apkFile = state.artifactFile
                if (state.kind == "APK" && apkFile != null && apkFile.exists()) {
                    Button(
                        onClick = { AppCompilerEngine.installApk(context, apkFile) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Android, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Install ${state.appName}.apk")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            val link = state.directDownloadUrl
                            if (!link.isNullOrBlank()) {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }.onFailure {
                                    if (apkFile != null) AppCompilerEngine.saveApkToDownloads(context, apkFile, state.appName)
                                }
                            } else if (apkFile != null) {
                                AppCompilerEngine.saveApkToDownloads(context, apkFile, state.appName)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Download")
                    }

                    state.directDownloadUrl?.let { link ->
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("${state.appName} APK Link", link))
                                Toast.makeText(context, "Direct APK download link copied!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy Link")
                        }
                    }

                    if (apkFile != null && apkFile.exists()) {
                        OutlinedButton(
                            onClick = { AppCompilerEngine.shareApk(context, apkFile, state.appName) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Share")
                        }
                    }
                }
            }
        }
    }
}
