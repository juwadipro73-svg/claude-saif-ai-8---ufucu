/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.ui.components

import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Person
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.PrimaryAccent
import com.example.ui.theme.SaifTheme
import com.example.ui.theme.StatusError
import com.example.ui.theme.SubtextMuted
import com.example.util.MemoryFactItem
import com.example.util.SmartConversationMemory
import kotlinx.coroutines.launch

@Composable
fun SaifAiMemoryDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isPinProtected by SmartConversationMemory.isPinProtected.collectAsState()
    var isUnlocked by remember { mutableStateOf(!SmartConversationMemory.isPinLockEnabled()) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var isPinVisible by remember { mutableStateOf(false) }
    var isNewPinVisible by remember { mutableStateOf(false) }
    
    // Restore account-password recovery without switching the signed-in app account.
    var showForgotPinDialog by remember { mutableStateOf(false) }
    var accountPasswordInput by remember { mutableStateOf("") }
    var accountPasswordError by remember { mutableStateOf<String?>(null) }
    var isVerifyingAccountPassword by remember { mutableStateOf(false) }

    // PIN Setup / Change Dialog
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var newPinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var pinSetupError by remember { mutableStateOf<String?>(null) }

    // Add Custom Memory Dialog
    var showAddMemoryDialog by remember { mutableStateOf(false) }
    var newFactKey by remember { mutableStateOf("") }
    var newFactValue by remember { mutableStateOf("") }

    // Memory list state
    var memoryList by remember { mutableStateOf(SmartConversationMemory.getAllMemoriesList()) }

    fun refreshMemories() {
        memoryList = SmartConversationMemory.getAllMemoriesList()
    }

    LaunchedEffect(Unit) {
        SmartConversationMemory.init(context)
        isUnlocked = !SmartConversationMemory.isPinLockEnabled()
        refreshMemories()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val glassBorderBrush = Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.35f),
                Color.White.copy(alpha = 0.05f),
                PrimaryAccent.copy(alpha = 0.3f)
            )
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(24.dp))
                .border(BorderStroke(1.5.dp, glassBorderBrush), RoundedCornerShape(24.dp)),
            color = SaifTheme.colors.primaryBackground.copy(alpha = 0.96f),
            shadowElevation = 24.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!isUnlocked) {
                    // --- PIN LOCK AUTHENTICATION SCREEN ---
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Brush.radialGradient(listOf(Color(0xFF7C5CE0), Color(0xFF5E43BD))))
                                .border(2.dp, Color(0xFF9B82EC).copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = "Locked",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Saif AI Memory Vault",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = SaifTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Enter your memory PIN or passphrase to open this screen",
                            fontSize = 13.sp,
                            color = SaifTheme.colors.textSecondary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Local memory PIN/passphrase input
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = {
                                pinInput = it
                                pinError = null
                            },
                            singleLine = true,
                            visualTransformation = if (isPinVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (pinInput.isBlank()) {
                                        pinError = "Please enter your memory PIN or passphrase"
                                    } else if (SmartConversationMemory.verifyPin(pinInput)) {
                                        isUnlocked = true
                                        refreshMemories()
                                    } else {
                                        pinError = "Incorrect memory PIN or passphrase. Try again."
                                    }
                                }
                            ),
                            trailingIcon = {
                                IconButton(onClick = { isPinVisible = !isPinVisible }) {
                                    Icon(
                                        imageVector = if (isPinVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (isPinVisible) "Hide PIN" else "Show PIN",
                                        tint = SaifTheme.colors.textSecondary
                                    )
                                }
                            },
                            textStyle = LocalTextStyle.current.copy(
                                textAlign = TextAlign.Center,
                                fontSize = 18.sp,
                                letterSpacing = if (isPinVisible) 1.sp else 6.sp,
                                color = SaifTheme.colors.textPrimary
                            ),
                            placeholder = {
                                Text(
                                    "••••••••",
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                    fontSize = 18.sp,
                                    letterSpacing = 6.sp,
                                    color = SaifTheme.colors.textSecondary.copy(alpha = 0.4f)
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryAccent,
                                unfocusedBorderColor = SaifTheme.colors.cardBorder,
                                focusedContainerColor = SaifTheme.colors.surfaceCard,
                                unfocusedContainerColor = SaifTheme.colors.surfaceCard
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(0.85f)
                        )

                        if (pinError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = pinError!!,
                                color = StatusError,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Unlock Button
                        Button(
                            onClick = {
                                if (pinInput.isBlank()) {
                                    pinError = "Please enter your memory PIN or passphrase"
                                } else if (SmartConversationMemory.verifyPin(pinInput)) {
                                    isUnlocked = true
                                    refreshMemories()
                                } else {
                                    pinError = "Incorrect memory PIN or passphrase. Try again."
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                            modifier = Modifier.fillMaxWidth(0.7f).height(46.dp)
                        ) {
                            Text("Unlock Vault", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Guest accounts cannot recover a forgotten PIN with an account password.
                        if (com.example.data.local.AuthManager.currentUser.value?.email != "guest@saif.ai") TextButton(onClick = {
                            accountPasswordInput = ""
                            accountPasswordError = null
                            showForgotPinDialog = true
                        }) {
                            Text(
                                text = "Forgot PIN? Use Account Password",
                                color = Color(0xFF9B82EC),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = SaifTheme.colors.textSecondary, fontSize = 13.sp)
                        }
                    }
                } else {
                    // --- UNLOCKED MEMORY VAULT VIEW ---
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryAccent.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Psychology,
                                        contentDescription = "Memory",
                                        tint = PrimaryAccent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "SAIF AI Memory",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SaifTheme.colors.textPrimary
                                    )
                                    Text(
                                        text = "${memoryList.size} learned facts stored",
                                        fontSize = 11.5.sp,
                                        color = SaifTheme.colors.textSecondary
                                    )
                                }
                            }

                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = SaifTheme.colors.textSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Clear English Privacy Statement Notice
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x1A10B981),
                            border = BorderStroke(1.dp, Color(0x6610B981)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Security,
                                    contentDescription = "Privacy",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Memories are stored locally. When you use AI chat, relevant memories can be included in requests to your selected AI provider. The optional PIN locks this screen; it does not encrypt local storage or prevent AI recall.",
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp,
                                    color = Color(0xFFD1FAE5)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Controls Row (PIN Lock setup / Add Fact / Lock Now)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // PIN lock toggle / change
                            val pinActive = isPinProtected
                            Surface(
                                onClick = {
                                    newPinInput = ""
                                    confirmPinInput = ""
                                    pinSetupError = null
                                    showPinSetupDialog = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (pinActive) Color(0x267C5CE0) else SaifTheme.colors.surfaceCard,
                                border = BorderStroke(1.dp, if (pinActive) PrimaryAccent else SaifTheme.colors.cardBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (pinActive) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = null,
                                        tint = if (pinActive) PrimaryAccent else SaifTheme.colors.textSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (pinActive) "PIN: Active" else "Set memory PIN",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (pinActive) PrimaryAccent else SaifTheme.colors.textPrimary
                                    )
                                }
                            }

                            if (pinActive) {
                                // Immediate Lock Vault Button
                                Surface(
                                    onClick = {
                                        isUnlocked = false
                                        pinInput = ""
                                        pinError = null
                                        Toast.makeText(context, "Memory Vault Locked", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = SaifTheme.colors.surfaceCard,
                                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                                    modifier = Modifier.weight(0.75f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Lock Now",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Lock",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFEF4444)
                                        )
                                    }
                                }
                            }

                            // Add Memory Button
                            Surface(
                                onClick = {
                                    newFactKey = ""
                                    newFactValue = ""
                                    showAddMemoryDialog = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = PrimaryAccent,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Add Memory",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Memory List
                        if (memoryList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Outlined.Psychology,
                                        contentDescription = null,
                                        tint = SaifTheme.colors.textSecondary.copy(alpha = 0.4f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "No memories saved yet",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SaifTheme.colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Tell Saif AI about your favorite car, game, color, or name in chat, and it will be remembered here automatically!",
                                        fontSize = 12.sp,
                                        color = SaifTheme.colors.textSecondary,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(memoryList, key = { it.key }) { item ->
                                    MemoryCard(
                                        item = item,
                                        onDelete = {
                                            SmartConversationMemory.deleteFact(item.key)
                                            refreshMemories()
                                            Toast.makeText(context, "Memory deleted", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Clear All Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                TextButton(
                                    onClick = {
                                        SmartConversationMemory.clearAll()
                                        refreshMemories()
                                        Toast.makeText(context, "All memories cleared", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = StatusError, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear All Memories", color = StatusError, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- SAME-ACCOUNT PASSWORD CHECK FOR OPTIONAL MEMORY PIN RECOVERY ---
    if (showForgotPinDialog) {
        AlertDialog(
            onDismissRequest = { if (!isVerifyingAccountPassword) showForgotPinDialog = false },
            title = { Text("Reset Memory PIN", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter your SAIF AI account password to reset your optional memory PIN.",
                        fontSize = 13.sp, color = SaifTheme.colors.textSecondary, lineHeight = 18.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = accountPasswordInput,
                        onValueChange = { accountPasswordInput = it; accountPasswordError = null },
                        label = { Text("Account Password") },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    accountPasswordError?.let { Text(it, color = StatusError, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (accountPasswordInput.isBlank()) {
                        accountPasswordError = "Enter your account password"
                    } else {
                        isVerifyingAccountPassword = true
                        coroutineScope.launch {
                            val success = runCatching {
                                SmartConversationMemory.resetPinUsingAccountPassword(accountPasswordInput)
                            }.getOrDefault(false)
                            accountPasswordInput = ""
                            isVerifyingAccountPassword = false
                            if (success) {
                                showForgotPinDialog = false
                                isUnlocked = true
                                refreshMemories()
                                Toast.makeText(context, "Account verified. Memory PIN reset.", Toast.LENGTH_LONG).show()
                            } else accountPasswordError = "Could not verify this account password."
                        }
                    }
                }, enabled = !isVerifyingAccountPassword,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)) {
                    if (isVerifyingAccountPassword) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Verify & Unlock")
                }
            },
            dismissButton = { TextButton(onClick = { showForgotPinDialog = false }) { Text("Cancel") } }
        )
    }

    // --- OPTIONAL LOCAL MEMORY PIN SETUP / CHANGE DIALOG ---
    if (showPinSetupDialog) {
        val pinActive = SmartConversationMemory.isPinLockEnabled()
        AlertDialog(
            onDismissRequest = { showPinSetupDialog = false },
            title = {
                Text(if (pinActive) "Manage Memory PIN" else "Set Memory PIN", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = if (com.example.data.local.AuthManager.currentUser.value?.email == "guest@saif.ai")
                            "Protect this guest memory with a separate PIN. Guest PINs cannot be recovered; guest memory is inaccessible after sign-out."
                        else "Protect this memory screen with a separate 4+ character PIN or passphrase. This is separate from your SAIF AI account password.",
                        fontSize = 13.sp,
                        color = SaifTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPinInput,
                        onValueChange = {
                            if (it.length <= 32) newPinInput = it
                            pinSetupError = null
                        },
                        label = { Text("Memory PIN or passphrase (min 4 chars)") },
                        visualTransformation = if (isNewPinVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { isNewPinVisible = !isNewPinVisible }) {
                                Icon(
                                    imageVector = if (isNewPinVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (isNewPinVisible) "Hide PIN" else "Show PIN",
                                    tint = SaifTheme.colors.textSecondary
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPinInput,
                        onValueChange = {
                            if (it.length <= 32) confirmPinInput = it
                            pinSetupError = null
                        },
                        label = { Text("Confirm memory PIN or passphrase") },
                        visualTransformation = if (isNewPinVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinSetupError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(pinSetupError!!, color = StatusError, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newPinInput.trim()
                        if (trimmed.length < 4) {
                            pinSetupError = "Memory PIN must be at least 4 characters"
                            return@Button
                        }
                        if (trimmed != confirmPinInput.trim()) {
                            pinSetupError = "Entries do not match"
                            return@Button
                        }
                        val pinActive = isPinProtected
                        val success = SmartConversationMemory.setPinLock(trimmed, context)
                        if (success) {
                            showPinSetupDialog = false
                            Toast.makeText(context, "Memory PIN saved. This screen now requires it to open.", Toast.LENGTH_LONG).show()
                        } else {
                            pinSetupError = "Failed to save memory PIN. Please try again."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                ) {
                    Text("Save PIN")
                }
            },
            dismissButton = {
                Row {
                    if (isPinProtected) {
                        TextButton(
                            onClick = {
                                SmartConversationMemory.disablePinLock(context)
                                showPinSetupDialog = false
                                Toast.makeText(context, "Memory PIN turned off", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Turn Off Lock", color = StatusError)
                        }
                    }
                    TextButton(onClick = { showPinSetupDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // --- ADD CUSTOM MEMORY DIALOG ---
    if (showAddMemoryDialog) {
        var addError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showAddMemoryDialog = false },
            title = { Text("Add Personal Fact", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Teach Saif AI something about you so it remembers it in future chats:",
                        fontSize = 13.sp,
                        color = SaifTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newFactKey,
                        onValueChange = { newFactKey = it },
                        label = { Text("Fact Title (e.g. Favorite Sport, Pet Name)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newFactValue,
                        onValueChange = { newFactValue = it },
                        label = { Text("Details (e.g. Cricket, Golden Retriever Max)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (addError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(addError!!, color = StatusError, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFactKey.isBlank() || newFactValue.isBlank()) {
                            addError = "Please fill out both fields"
                            return@Button
                        }
                        val safeKey = newFactKey.trim().replace(Regex("[^a-zA-Z0-9_]"), "")
                        SmartConversationMemory.saveFact(safeKey.ifBlank { "custom_" + System.currentTimeMillis() % 1000 }, newFactValue.trim())
                        refreshMemories()
                        showAddMemoryDialog = false
                        Toast.makeText(context, "Memory added!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                ) {
                    Text("Save Fact")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMemoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun MemoryCard(
    item: MemoryFactItem,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SaifTheme.colors.surfaceCard,
        border = BorderStroke(1.dp, SaifTheme.colors.cardBorder.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF7C5CE0).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (item.category) {
                        "Identity" -> Icons.Outlined.Person
                        "Favorites" -> Icons.Outlined.StarOutline
                        "Hardware" -> Icons.Outlined.PhoneAndroid
                        "Personal" -> Icons.Outlined.Place
                        "Career" -> Icons.Outlined.WorkOutline
                        else -> Icons.Outlined.Psychology
                    },
                    contentDescription = null,
                    tint = Color(0xFF9B82EC),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = SaifTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0x267C5CE0)
                    ) {
                        Text(
                            text = item.category,
                            color = Color(0xFF9B82EC),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.value,
                    fontSize = 13.sp,
                    color = Color(0xFFE5E2D6),
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete memory",
                    tint = StatusError.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
