/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.AuthManager
import com.example.gemini.data.remote.SavedAiSkills
import com.example.ui.theme.SaifTheme
import kotlinx.coroutines.launch

@Composable
fun SaifSkillsManagerDialog(
    isLightMode: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by AuthManager.currentUser.collectAsState()

    LaunchedEffect(currentUser?.uid) {
        SavedAiSkills.bind(context.applicationContext, currentUser?.uid)
    }

    var githubUrl by remember { mutableStateOf("") }
    var skillName by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var successMsg by remember { mutableStateOf<String?>(null) }

    val skills = SavedAiSkills.visibleSkills()
    val surfaceBg = if (isLightMode) Color(0xFFFBFBFD) else Color(0xFF14131A)
    val cardBg = if (isLightMode) Color(0xFFF1F0F7) else Color(0xFF1E1C28)
    val borderCol = if (isLightMode) Color(0x22000000) else Color(0x26FFFFFF)
    val accentCol = Color(0xFF8B6CEF)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 640.dp),
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = accentCol,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI Skills (/commands)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = SaifTheme.colors.textPrimary
                        )
                        Text(
                            text = "Import any GitHub repo, skill folder, or SKILL.md",
                            fontSize = 12.sp,
                            color = SaifTheme.colors.textSecondary
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SaifTheme.colors.textSecondary)
                    }
                }

                Spacer(Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, borderCol),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Add New AI Skill from GitHub",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = SaifTheme.colors.textPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = githubUrl,
                            onValueChange = {
                                githubUrl = it
                                if (skillName.isBlank()) {
                                    val candidate = it.trim().removeSuffix("/").removeSuffix(".git")
                                        .substringAfterLast('/')
                                        .replace(Regex("[^A-Za-z0-9_-]"), "")
                                        .take(32)
                                    if (candidate.isNotBlank() && !candidate.equals("github.com", true)) {
                                        skillName = candidate
                                    }
                                }
                            },
                            label = { Text("GitHub Repository / Skill URL", fontSize = 12.sp) },
                            placeholder = { Text("https://github.com/morluto/rea.git", fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = skillName,
                            onValueChange = { skillName = it },
                            label = { Text("Slash Command Name (e.g. REA)", fontSize = 12.sp) },
                            placeholder = { Text("REA", fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        errorMsg?.let { err ->
                            Spacer(Modifier.height(8.dp))
                            Text(text = err, color = Color(0xFFEF4444), fontSize = 12.sp)
                        }
                        successMsg?.let { ok ->
                            Spacer(Modifier.height(8.dp))
                            Text(text = ok, color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }

                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    isImporting = true
                                    errorMsg = null
                                    successMsg = null
                                    try {
                                        val imported = SavedAiSkills.importFromGitHub(githubUrl, skillName)
                                        successMsg = "Saved /${imported.name} — type /${imported.name} in chat!"
                                        githubUrl = ""
                                        skillName = ""
                                    } catch (e: Exception) {
                                        errorMsg = e.localizedMessage ?: "Failed to import GitHub skill"
                                    } finally {
                                        isImporting = false
                                    }
                                }
                            },
                            enabled = !isImporting && githubUrl.isNotBlank() && skillName.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = accentCol),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isImporting) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Importing from GitHub…", color = Color.White, fontWeight = FontWeight.Bold)
                            } else {
                                Text("Fetch & Save Skill", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Saved Skills (${skills.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = SaifTheme.colors.textPrimary
                )
                Spacer(Modifier.height(8.dp))

                if (skills.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No skills saved yet. Paste any GitHub skill repo above.",
                            fontSize = 12.sp,
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
                        items(skills, key = { it.name }) { skill ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = cardBg,
                                border = BorderStroke(1.dp, borderCol),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "/${skill.name}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = accentCol
                                        )
                                        Text(
                                            text = skill.description,
                                            fontSize = 12.sp,
                                            color = SaifTheme.colors.textPrimary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = skill.sourceUrl,
                                            fontSize = 10.sp,
                                            color = SaifTheme.colors.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(onClick = { SavedAiSkills.delete(skill.name) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
