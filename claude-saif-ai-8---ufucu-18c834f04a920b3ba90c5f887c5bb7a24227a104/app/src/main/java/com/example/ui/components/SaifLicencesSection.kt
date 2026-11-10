/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */

package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.SaifTheme

/**
 * Settings → LICENCES & OPEN SOURCE.
 *
 * This screen is what GPL v3 compliance looks like from inside the app: the SAIF AI copyright, the
 * attribution and licence of every third-party component actually shipped, and the address where the
 * complete corresponding source for this exact build can be downloaded.
 *
 * It is deliberately plain: a licence notice is a legal document, not a marketing page. Keep the
 * wording here in step with `LICENSE`, `THIRD_PARTY_NOTICES.md` and `GPL_SHIP_CHECKLIST.md` — an app
 * that claims GPL but hides its source is in a worse position than one that never mentioned it.
 */
@Composable
fun SaifLicencesSection(glassBorderBrush: Brush) {
    var showDialog by remember { mutableStateOf(false) }

    LiquidSurface(
        color = SaifTheme.colors.surfaceCard,
        glassBorderBrush = glassBorderBrush,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        onClick = { showDialog = true }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = null,
                tint = com.example.ui.theme.PrimaryAccent,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Licences & open source",
                    color = SaifTheme.colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "SAIF AI, AntiGem (GPL v3), Termux and the Linux runtime",
                    color = SaifTheme.colors.textSecondary,
                    fontSize = 12.sp
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = SaifTheme.colors.textSecondary
            )
        }
    }

    if (showDialog) {
        LicencesDialog(onDismiss = { showDialog = false })
    }
}

@Composable
private fun LicencesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sourceUrl = com.example.gemini.BuildConfig.SAIF_SOURCE_URL.trim()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(24.dp),
            color = SaifTheme.colors.surfaceCard,
            modifier = Modifier.fillMaxWidth(0.94f)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Licences & open source",
                    color = SaifTheme.colors.textPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    LicenceBlock(
                        title = "SAIF AI",
                        body = "Copyright (c) SAIF AI. All rights reserved on SAIF AI's own code " +
                            "(the interface, chat, video studio, cloud, automation and terminal-provider modules)."
                    )
                    LicenceBlock(
                        title = "SAIF ADVANCE EDITING ENGINE — AntiGem",
                        body = "Based on AntiGem by Santosh Kurmi " +
                            "(https://github.com/Santoshkurmi/antigem), licensed under the GNU General " +
                            "Public License v3.0. The copyright notice and licence text stay with that " +
                            "code, and because this app contains it the app is distributed under GPL v3 " +
                            "with its complete corresponding source available."
                    )
                    LicenceBlock(
                        title = "Termux",
                        body = "The terminal emulator (com.termux) is derived from Termux and keeps its " +
                            "respective upstream licence (GPL v3 / Apache-2.0 per file)."
                    )
                    LicenceBlock(
                        title = "Linux runtime",
                        body = "The bundled Ubuntu/PRoot rootfs, glibc and bootstrap archives retain the " +
                            "licences of their upstream distributions."
                    )
                    LicenceBlock(
                        title = "Other components",
                        body = "AndroidX, Jetpack Compose, OkHttp, Wire, Firebase and the other libraries " +
                            "this app links are used under their own permissive licences."
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Source for this build",
                        color = SaifTheme.colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (sourceUrl.isBlank()) {
                            "Not configured. Set SAIF_SOURCE_URL when building so the complete " +
                                "corresponding source for this build is published (GPL v3 section 13)."
                        } else sourceUrl,
                        color = if (sourceUrl.isBlank()) Color(0xFFEF4444) else SaifTheme.colors.textSecondary,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (sourceUrl.isNotBlank()) {
                        TextButton(onClick = {
                            runCatching {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("SAIF AI source", sourceUrl))
                                Toast.makeText(context, "Source link copied", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("Copy link") }
                        TextButton(onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sourceUrl)))
                            }
                        }) { Text("Open") }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun LicenceBlock(title: String, body: String) {
    Row(modifier = Modifier.padding(bottom = 12.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .background(com.example.ui.theme.PrimaryAccent, CircleShape)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = SaifTheme.colors.textPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = body,
                color = SaifTheme.colors.textSecondary,
                fontSize = 12.sp
            )
        }
    }
}
