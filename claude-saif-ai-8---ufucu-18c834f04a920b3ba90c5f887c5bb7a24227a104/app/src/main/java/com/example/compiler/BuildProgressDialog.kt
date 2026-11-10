/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.compiler

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.gemini.data.remote.AndroidLocalBridgeServer
import com.example.ui.components.ProjectData
import java.io.File

/** Structured compiler error payload shared with BuildAIScreen / AutonomousAppBuilder. */
data class CompilerErrorInfo(
    val fileName: String,
    val lineNumber: Int,
    val errorMessage: String,
    val rawLog: String,
    val codeSnippet: String = "",
    val fullCode: String = ""
)

/**
 * On-Device Android APK Compiler & Signer Dialog.
 * Packages, 4-byte zipaligns, and cryptographically signs (v1 + v2 + v3) real standalone APKs
 * directly on the user's phone — with direct download link, Downloads save, Share, and Install.
 */
@Composable
fun BuildProgressDialog(
    project: ProjectData,
    isDarkTheme: Boolean = true,
    onDismiss: () -> Unit,
    onAskSaifAi: (CompilerErrorInfo) -> Unit,
    modifier: Modifier = Modifier,
    mode: String = "apk"
) {
    val context = LocalContext.current
    var currentStepTitle by remember { mutableStateOf("Initializing On-Device APK Compiler…") }
    var progress by remember { mutableFloatStateOf(0.06f) }
    var isBuilding by remember { mutableStateOf(true) }
    var buildResult by remember { mutableStateOf<BuildResult?>(null) }
    var zipArtifact by remember { mutableStateOf<File?>(null) }
    var directDownloadUrl by remember { mutableStateOf<String?>(null) }
    val logs = remember { mutableStateListOf<String>() }
    val logScroll = rememberScrollState()

    val bgColor = if (isDarkTheme) Color(0xFF141824) else Color.White
    val textColor = if (isDarkTheme) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val subColor = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
    val accent = Color(0xFF3B82F6)
    val green = Color(0xFF10B981)

    LaunchedEffect(project.appName, project.packageName, mode) {
        isBuilding = true
        logs.clear()
        if (mode == "zip") {
            currentStepTitle = "Packaging Android Studio ZIP…"
            logs.add("> Exporting ${project.appName} source tree…")
            val exported = AppCompilerEngine.exportProjectAsZip(context, project)
            if (exported != null && exported.exists()) {
                val cleanZip = "${project.appName.replace(Regex("[^a-zA-Z0-9_]"), "_").trim('_').ifBlank { "Project" }}_Source.zip"
                directDownloadUrl = runCatching {
                    AndroidLocalBridgeServer.instance.registerDownloadFile(context, exported, cleanZip)
                }.getOrNull()
                zipArtifact = exported
                progress = 1f
                currentStepTitle = "Project ZIP Ready"
                logs.add("> Saved: ${exported.name} (${exported.length() / 1024} KB)")
            } else {
                buildResult = BuildResult.Error(
                    message = "Could not create project ZIP archive.",
                    fileName = null,
                    lineNumber = null,
                    errorStep = BuildStep.APK_PACKAGING,
                    logs = emptyList()
                )
            }
            isBuilding = false
        } else {
            val result = AppCompilerEngine.compileProject(
                context = context,
                project = project,
                onStepChanged = { step, p ->
                    currentStepTitle = step.title
                    progress = p
                },
                onLogAdded = { entry ->
                    logs.add(entry.message)
                }
            )
            buildResult = result
            if (result is BuildResult.Success) {
                val saved = AppCompilerEngine.saveApkToDownloads(
                    context = context,
                    apkFile = result.apkFile,
                    appName = project.appName,
                    showToast = false
                )
                val localState = OnDeviceApkBuildManager.recordReadyApk(
                    context = context,
                    project = project,
                    apkFile = result.apkFile,
                    downloadsFile = saved
                )
                directDownloadUrl = localState.directDownloadUrl
                progress = 1f
                currentStepTitle = "APK Signed & Ready!"
                logs.add("> Saved to Downloads & Direct Link Ready")
            }
            isBuilding = false
        }
    }

    LaunchedEffect(logs.size) {
        runCatching { logScroll.animateScrollTo(logScroll.maxValue) }
    }

    Dialog(
        onDismissRequest = { if (!isBuilding) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !isBuilding, dismissOnClickOutside = !isBuilding)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = bgColor,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = when {
                            isBuilding -> Icons.Default.Android
                            buildResult is BuildResult.Error -> Icons.Default.ErrorOutline
                            else -> Icons.Default.CheckCircle
                        },
                        contentDescription = null,
                        tint = when {
                            isBuilding -> accent
                            buildResult is BuildResult.Error -> Color(0xFFEF4444)
                            else -> green
                        },
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (mode == "zip") "Project ZIP Exporter" else "On-Device APK Compiler",
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            project.appName,
                            color = subColor,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (isBuilding) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = accent)
                    } else {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = subColor)
                        }
                    }
                }

                Text(currentStepTitle, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)

                LinearProgressIndicator(
                    progress = { progress.coerceIn(0.04f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = if (buildResult is BuildResult.Error) Color(0xFFEF4444) else accent
                )

                // Live compiler log console
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0A0E17))
                        .padding(10.dp)
                ) {
                    Column(Modifier.verticalScroll(logScroll)) {
                        logs.forEach { line ->
                            Text(
                                text = line,
                                color = if (line.contains("ERROR", true)) Color(0xFFF87171) else Color(0xFFA7F3D0),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                when (val res = buildResult) {
                    is BuildResult.Success -> {
                        val apkFile = res.apkFile
                        Button(
                            onClick = { AppCompilerEngine.installApk(context, apkFile) },
                            colors = ButtonDefaults.buttonColors(containerColor = green),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            Icon(Icons.Default.Android, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Install ${project.appName}.apk", fontWeight = FontWeight.Bold)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    val link = directDownloadUrl
                                    if (!link.isNullOrBlank()) {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(link))
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }.onFailure {
                                            AppCompilerEngine.saveApkToDownloads(context, apkFile, project.appName)
                                        }
                                    } else {
                                        AppCompilerEngine.saveApkToDownloads(context, apkFile, project.appName)
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Download", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    val link = directDownloadUrl ?: "file://${apkFile.absolutePath}"
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("${project.appName} APK Link", link))
                                    Toast.makeText(context, "Direct APK download link copied!", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Copy Link", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { AppCompilerEngine.shareApk(context, apkFile, project.appName) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Share", fontSize = 12.sp)
                            }
                        }
                    }

                    is BuildResult.Error -> {
                        Text(
                            text = res.message,
                            color = Color(0xFFEF4444),
                            fontSize = 12.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    onAskSaifAi(
                                        CompilerErrorInfo(
                                            fileName = res.fileName ?: "MainActivity.java",
                                            lineNumber = res.lineNumber ?: 1,
                                            errorMessage = res.message,
                                            rawLog = logs.joinToString("\n")
                                        )
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = accent),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Fix with SAIF AI")
                            }
                            OutlinedButton(onClick = onDismiss) {
                                Text("Close")
                            }
                        }
                    }

                    null -> {
                        zipArtifact?.let { zip ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = {
                                        val link = directDownloadUrl
                                        if (!link.isNullOrBlank()) {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(link))
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = green),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Download ZIP")
                                }
                                OutlinedButton(onClick = onDismiss) {
                                    Text("Done")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
