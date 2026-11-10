/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.gemini.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gemini.data.remote.SavedAiSkill
import com.example.gemini.data.remote.SavedAiSkills
import kotlinx.coroutines.launch

/** Add, inspect and remove locally saved GitHub skills from SAIF AI's settings. */
@Composable
fun SavedAiSkillsScreen() {
    val scope = rememberCoroutineScope()
    var githubLink by remember { mutableStateOf("") }
    var skillName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<SavedAiSkill?>(null) }
    var preview by remember { mutableStateOf<SavedAiSkill?>(null) }
    val saved = SavedAiSkills.visibleSkills()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Add New AI Skill", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Paste a public GitHub repository, skill folder or SKILL.md link. Choose your own /command name: all text files in that skill (including references and scripts) are saved together as ONE Markdown document for this account and used in chat.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = githubLink, onValueChange = { githubLink = it.trim() },
                    label = { Text("GitHub skill URL") },
                    placeholder = { Text("https://github.com/morluto/rea") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = skillName, onValueChange = { skillName = it },
                    label = { Text("Your skill name (/command)") },
                    placeholder = { Text("mera-research-skill") },
                    supportingText = { Text("1–48 letters/numbers, _ or -; no spaces. Type /your-name in chat.") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("One repository link makes one bundled skill, not many commands. Text references, scripts and docs are included (up to 256 KB); non-text assets are listed by path, not embedded in the chat prompt.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = {
                    busy = true
                    error = null
                    feedback = null
                    scope.launch {
                        try {
                            val imported = SavedAiSkills.importFromGitHub(githubLink, skillName)
                            feedback = "Saved /${imported.name} as one skill file — use /${imported.name} in chat"
                            githubLink = ""
                            skillName = ""
                        } catch (e: Exception) {
                            error = e.message ?: "Could not fetch these skills. Check the URL and try again."
                        } finally { busy = false }
                    }
                }, enabled = !busy && githubLink.isNotBlank() && skillName.isNotBlank()) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (busy) "Bundling skill files…" else "Fetch & Save One Skill")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                feedback?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
            }
        }
        Text("My Saved Skills", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (saved.isEmpty()) {
            Text("No saved skills yet. Import one above to use it with / in chat.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        saved.forEach { skill ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("/${skill.name}", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text(skill.description, style = MaterialTheme.typography.bodyMedium)
                    val fileCount = skill.instructions.lineSequence().count { it.startsWith("===== BEGIN FILE: ") }
                    if (fileCount > 0) Text("$fileCount source text files · 1 saved skill document",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(skill.sourceUrl, style = MaterialTheme.typography.labelSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { preview = skill }) { Text("View rules") }
                        TextButton(onClick = { deleting = skill }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
        Text("Only import skills you trust. A skill changes the agent's instructions, not its permissions; files, tools and accounts still need to be available to the agent.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    deleting?.let { skill ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete /${skill.name}?") },
            text = { Text("Its saved rules will be removed from this device.") },
            confirmButton = { TextButton(onClick = { SavedAiSkills.delete(skill.name); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
    preview?.let { skill ->
        AlertDialog(onDismissRequest = { preview = null }, title = { Text("/${skill.name} rules") },
            text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                Text(skill.instructions, style = MaterialTheme.typography.bodySmall)
            } }, confirmButton = { TextButton(onClick = { preview = null }) { Text("Close") } })
    }
}
