/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.gemini.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.gemini.data.local.LocalEnvironmentManager
import com.example.gemini.data.preferences.AuthPreferences
import com.example.gemini.theme.GeminiTheme
import com.example.gemini.ui.chat.ChatViewModel
import kotlinx.coroutines.launch

/**
 * The agent / tools settings (Agents, Servers & Connectivity, Commands & Permissions, Terminal & Shell, MCP Servers,
 * Skills & Plugins, Slash Commands, Custom Plugins ...) shown INSIDE SAIF AI, in SAIF AI's own colours.
 * It never opens a separate workspace screen: SAIF AI's settings rows call this directly.
 *
 * [initialSection] is a [SettingsSection] name (or null for the main list).
 */
@Composable
fun EmbeddedEngineSettings(
    viewModel: ChatViewModel,
    isLightMode: Boolean,
    initialSection: String?,
    onOpenTerminal: () -> Unit,
    autoApprove: Boolean,
    onAutoApproveChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var claudeSettingsPage by remember { mutableStateOf<com.example.gemini.ui.claude.ClaudeSettingsPage?>(null) }
    val engineInitialSection = remember(initialSection) {
        try { initialSection?.let { SettingsSection.valueOf(it) } } catch (_: Exception) { null }
    }
    val claudeSettingsDeps = remember(viewModel) {
        com.example.gemini.ui.claude.ClaudeSettingsDeps(viewModel.claudeAccount, viewModel.claudeConfig, viewModel.claudeBackend, viewModel.claudePrefs)
    }
    var showNetworkInspectorDialog by remember { mutableStateOf(false) }
    var showLocalToolsInstallDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadSecurityAndProjectSettings() }

    val availableModels by viewModel.availableModels.collectAsState()
    val enabledModelIds by viewModel.enabledModelIds.collectAsState()
    val quotas by viewModel.quotas.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()
    val projectId by viewModel.projectId.collectAsState()
    val tier by viewModel.tier.collectAsState()
    val contextWindowLimit by viewModel.contextWindowLimit.collectAsState()
    val summaryModelIdPref by viewModel.summaryModelIdPref.collectAsState()
    val isDevModeEnabled by viewModel.isDevModeEnabled.collectAsState()
    val chatFontScale by viewModel.chatFontScale.collectAsState(initial = 1.0f)
    val isServerOnline by viewModel.isServerOnline.collectAsState()
    val isBridgeOnline by viewModel.isBridgeOnline.collectAsState()
    val isLocalToolsEnabled by viewModel.isLocalToolsEnabled.collectAsState(initial = false)
    val isLocalToolsInstalled by viewModel.isLocalToolsInstalled.collectAsState(initial = false)
    val useSshTerminal by viewModel.useSshTerminal.collectAsState(initial = false)
    val sshHost by viewModel.termuxSshHost.collectAsState(initial = "127.0.0.1")
    val sshPort by viewModel.termuxSshPort.collectAsState(initial = 8022)
    val sshUser by viewModel.termuxSshUser.collectAsState(initial = "root")
    val sshPass by viewModel.termuxSshPass.collectAsState(initial = "root")
    val themeMode by viewModel.themeMode.collectAsState(initial = viewModel.authPreferences.getThemeModeSync())
    val agyHubUrl by viewModel.agyHubUrl.collectAsState(initial = AuthPreferences.currentHubUrl)
    val agyBridgeHttpUrl by viewModel.agyBridgeHttpUrl.collectAsState(initial = AuthPreferences.currentBridgeHttpUrl)
    val terminalFontSize by viewModel.terminalFontSize.collectAsState(initial = 14)
    val terminalCursorStyle by viewModel.terminalCursorStyle.collectAsState(initial = "BAR")
    val terminalBufferSize by viewModel.terminalBufferSize.collectAsState(initial = 20000)
    val terminalTheme by viewModel.terminalTheme.collectAsState(initial = "DEFAULT")
    val commandAutoExecutionPolicy by viewModel.commandAutoExecutionPolicy.collectAsState(initial = "CASCADE_COMMANDS_AUTO_EXECUTION_EAGER")
    val commandSandboxEnabled by viewModel.commandSandboxEnabled.collectAsState(initial = false)
    val requireApprovalForFileEdits by viewModel.requireApprovalForFileEdits.collectAsState(initial = false)
    val defaultApprovalScope by viewModel.defaultApprovalScope.collectAsState(initial = "PERMISSION_SCOPE_ONCE")
    val globalSecuritySettings by viewModel.globalSecuritySettings.collectAsState()
    val globalSettingsError by viewModel.globalSettingsError.collectAsState()
    val isGlobalSettingsLoading by viewModel.isGlobalSettingsLoading.collectAsState()
    val projectsList by viewModel.projectsList.collectAsState()
    val isProjectsLoading by viewModel.isProjectsLoading.collectAsState()
    val groupChatsByWorkspace by viewModel.groupChatsByWorkspace.collectAsState()
    val isFloatingSwitcherEnabled by viewModel.isFloatingSwitcherEnabled.collectAsState()
    val floatingSwitcherOrientation by viewModel.floatingSwitcherOrientation.collectAsState()
    val floatingSwitcherItems by viewModel.floatingSwitcherItems.collectAsState()
    val floatingSwitcherAutoCollapseSec by viewModel.floatingSwitcherAutoCollapseSec.collectAsState()
    val isFloatingDiagnosticsEnabled by viewModel.isFloatingDiagnosticsEnabled.collectAsState()
    val isNetworkInspectorEnabled by viewModel.isNetworkInspectorEnabled.collectAsState()
    val isFloatingNetworkInspectorEnabled by viewModel.isFloatingNetworkInspectorEnabled.collectAsState()
    val enabledAgents by viewModel.enabledAgents.collectAsState()
    val isApplyingAgents by viewModel.isApplyingAgents.collectAsState()
    val isOAuthServerListening by viewModel.isOAuthServerListening.collectAsState()
    val isOAuthServerLoading by viewModel.isOAuthServerLoading.collectAsState()
    val mcpServers by viewModel.mcpServers.collectAsState()
    val isMcpLoading by viewModel.isMcpLoading.collectAsState()
    val isMcpRefreshing by viewModel.isMcpRefreshing.collectAsState()
    val refreshingMcpServer by viewModel.refreshingMcpServer.collectAsState()
    val mcpErrorMessage by viewModel.mcpErrorMessage.collectAsState()
    val mcpStatusMessage by viewModel.mcpStatusMessage.collectAsState()
    val availableCascadePlugins by viewModel.availableCascadePlugins.collectAsState()
    val isCascadePluginsLoading by viewModel.isCascadePluginsLoading.collectAsState()
    val installingCascadePluginId by viewModel.installingCascadePluginId.collectAsState()
    val allSkills by viewModel.allSkills.collectAsState()
    val isSkillsLoading by viewModel.isSkillsLoading.collectAsState()
    val skillsFilterScope by viewModel.skillsFilterScope.collectAsState()
    val installedPlugins by viewModel.installedPlugins.collectAsState()
    val isInstalledPluginsLoading by viewModel.isInstalledPluginsLoading.collectAsState()
    val googlePluginsCatalog by viewModel.googlePluginsCatalog.collectAsState()
    val isGooglePluginsLoading by viewModel.isGooglePluginsLoading.collectAsState()
    val installingGooglePluginId by viewModel.installingGooglePluginId.collectAsState()
    val deletingPluginId by viewModel.deletingPluginId.collectAsState()
    val pluginActionStatusMessage by viewModel.pluginActionStatusMessage.collectAsState()
    val pluginActionErrorMessage by viewModel.pluginActionErrorMessage.collectAsState()

    GeminiTheme(darkTheme = !isLightMode, applySystemBars = false) {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            SettingsDialog(
                userEmail = userEmail,
                projectId = projectId,
                tier = tier,
                availableModels = availableModels,
                enabledModelIds = enabledModelIds,
                quotas = quotas,
                isServerListening = isOAuthServerListening,
                isServerLoading = isOAuthServerLoading,
                contextWindowLimit = contextWindowLimit,
                summaryModelId = summaryModelIdPref,
                isDevModeEnabled = isDevModeEnabled,
                chatFontScale = chatFontScale,
                themeMode = themeMode,
                agyHubUrl = agyHubUrl,
                agyBridgeHttpUrl = agyBridgeHttpUrl,
                isServerOnline = isServerOnline == true,
                isBridgeOnline = isBridgeOnline == true,
                useSshTerminal = useSshTerminal,
                sshHost = sshHost,
                sshPort = sshPort,
                sshUser = sshUser,
                sshPass = sshPass,
                terminalFontSize = terminalFontSize,
                terminalCursorStyle = terminalCursorStyle,
                terminalBufferSize = terminalBufferSize,
                terminalTheme = terminalTheme,
                isLocalToolsEnabled = isLocalToolsEnabled,
                isLocalToolsInstalled = isLocalToolsInstalled,
                onLoginWithGoogle = {
                    val url = viewModel.getGoogleOAuthUrl()
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                },
                onToggleServer = { enable ->
                    viewModel.toggleOAuthServer(enable)
                },
                onLogout = {
                    viewModel.logout()
                },
                onManualTokenEntered = { token ->
                    viewModel.applyManualInput(token)
                },
                onToggleModelEnabled = { modelId, isEnabled ->
                    viewModel.setModelEnabled(modelId, isEnabled)
                },
                onEnableAllModels = {
                    viewModel.enableAllModels()
                },
                onRefreshQuotas = {
                    viewModel.refreshQuotas(force = true, showToastFeedback = true)
                },
                onSetContextWindowLimit = { viewModel.setContextWindowLimit(it) },
                onSetSummaryModelId = { viewModel.setSummaryModelId(it) },
                onSetChatFontScale = { viewModel.setChatFontScale(it) },
                onSetThemeMode = { mode ->
                    viewModel.setThemeMode(mode)
                    com.example.data.local.ThemeManager.setTheme(when (mode) {
                        "DARK" -> com.example.data.local.ThemeMode.DARK
                        "LIGHT" -> com.example.data.local.ThemeMode.LIGHT
                        else -> com.example.data.local.ThemeMode.SYSTEM
                    })
                },
                onSaveServerUrls = { hub, bridge -> viewModel.saveServerUrls(hub, bridge) },
                onSaveTerminalPreferences = { size, cursor, buf, thm -> viewModel.saveTerminalPreferences(size, cursor, buf, thm) },
                onToggleDevMode = { viewModel.setDevModeEnabled(it) },
                onToggleUseSshTerminal = { viewModel.setUseSshTerminal(it) },
                onSaveSshSettings = { h, p, u, pass -> viewModel.saveSshSettings(h, p, u, pass) },
                onToggleLocalTools = { viewModel.setLocalToolsEnabled(it) },
                onInstallLocalTools = { showLocalToolsInstallDialog = true },
                onOpenLocalTerminal = onOpenTerminal,
                onResetLocalTools = {
                    LocalEnvironmentManager.launchReset(context) {
                        viewModel.setLocalToolsEnabled(false)
                        scope.launch { viewModel.authPreferences.setLocalToolsInstalled(false) }
                    }
                },
                mcpServers = mcpServers,
                isMcpLoading = isMcpLoading,
                isMcpRefreshing = isMcpRefreshing,
                refreshingMcpServer = refreshingMcpServer,
                mcpErrorMessage = mcpErrorMessage,
                mcpStatusMessage = mcpStatusMessage,
                onClearMcpStatus = { viewModel.clearMcpStatus() },
                onRefreshMcpServers = { viewModel.refreshMcpServers() },
                onRefreshMcpServer = { name -> viewModel.refreshMcpServers(name) },
                onToggleMcpServer = { name, enabled -> viewModel.toggleMcpServer(name, enabled) },
                onSaveMcpServer = { spec, rawJson -> viewModel.saveMcpServer(spec, rawJson) },
                onDeleteMcpServer = { name -> viewModel.deleteMcpServer(name) },
                availableCascadePlugins = availableCascadePlugins,
                isCascadePluginsLoading = isCascadePluginsLoading,
                installingCascadePluginId = installingCascadePluginId,
                onSearchCascadePlugins = { q -> viewModel.loadAvailableCascadePlugins(q) },
                onInstallCascadePlugin = { p -> viewModel.installCascadeMcpPlugin(p) },
                allSkills = allSkills,
                isSkillsLoading = isSkillsLoading,
                skillsFilterScope = skillsFilterScope,
                onSetSkillsFilterScope = { s -> viewModel.setSkillsFilterScope(s) },
                onRefreshSkills = { viewModel.loadAllSkills() },
                installedPlugins = installedPlugins,
                isInstalledPluginsLoading = isInstalledPluginsLoading,
                onRefreshInstalledPlugins = { viewModel.loadAllInstalledPlugins() },
                googlePluginsCatalog = googlePluginsCatalog,
                isGooglePluginsLoading = isGooglePluginsLoading,
                installingGooglePluginId = installingGooglePluginId,
                deletingPluginId = deletingPluginId,
                onRefreshGooglePlugins = { viewModel.loadGooglePluginsCatalog() },
                onInstallGooglePlugin = { id, name -> viewModel.installGooglePlugin(id, name) },
                onDeletePlugin = { id, name -> viewModel.deleteInstalledPlugin(id, name) },
                pluginActionStatusMessage = pluginActionStatusMessage,
                pluginActionErrorMessage = pluginActionErrorMessage,
                onClearPluginStatus = { viewModel.clearPluginActionStatus() },
                isFloatingSwitcherEnabled = isFloatingSwitcherEnabled,
                floatingSwitcherOrientation = floatingSwitcherOrientation,
                floatingSwitcherItems = floatingSwitcherItems,
                floatingSwitcherAutoCollapseSec = floatingSwitcherAutoCollapseSec,
                onToggleFloatingSwitcher = { viewModel.setFloatingSwitcherEnabled(it) },
                onSetFloatingSwitcherOrientation = { viewModel.setFloatingSwitcherOrientation(it) },
                onSetFloatingSwitcherItems = { viewModel.setFloatingSwitcherItems(it) },
                onSetFloatingSwitcherAutoCollapseSec = { viewModel.setFloatingSwitcherAutoCollapseSec(it) },
                onResetFloatingSwitcherPosition = { viewModel.resetFloatingSwitcherPosition() },
                commandAutoExecutionPolicy = commandAutoExecutionPolicy,
                commandSandboxEnabled = commandSandboxEnabled,
                requireApprovalForFileEdits = requireApprovalForFileEdits,
                defaultApprovalScope = defaultApprovalScope,
                globalSecuritySettings = globalSecuritySettings,
                globalSettingsError = globalSettingsError,
                isGlobalSettingsLoading = isGlobalSettingsLoading,
                projectsList = projectsList,
                isProjectsLoading = isProjectsLoading,
                onSetGlobalArtifactReviewMode = { viewModel.updateGlobalArtifactReviewMode(it) },
                onSetGlobalSecurityPreset = { autoExec, fileAccess -> viewModel.updateGlobalSecurityPreset(autoExec, fileAccess) },
                onSetGlobalCustomTerminalPolicy = { viewModel.updateGlobalCustomTerminalPolicy(it) },
                onSetGlobalCustomFileAccessPolicy = { viewModel.updateGlobalCustomFileAccessPolicy(it) },
                onSetGlobalTerminalSandbox = { viewModel.updateGlobalTerminalSandbox(it) },
                onSetProjectInheritGlobal = { viewModel.setProjectInheritGlobal(it) },
                onSetProjectPreset = { proj, autoExec, fileAccess -> viewModel.updateProjectPreset(proj, autoExec, fileAccess) },
                onRefreshSecurityAndProjects = { viewModel.loadSecurityAndProjectSettings() },
                onSetCommandAutoExecutionPolicy = { viewModel.setCommandAutoExecutionPolicy(it) },
                onSetCommandSandboxEnabled = { viewModel.setCommandSandboxEnabled(it) },
                onSetRequireApprovalForFileEdits = { viewModel.setRequireApprovalForFileEdits(it) },
                onSetDefaultApprovalScope = { viewModel.setDefaultApprovalScope(it) },
                onAddPermissionRule = { action, pattern, decision -> viewModel.addGlobalPermissionGrant(action, pattern, decision) },
                onRemovePermissionRule = { rawRule -> viewModel.removeGlobalPermissionGrant(rawRule) },
                onChangePermissionRuleDecision = { rawRule, newDecision -> viewModel.changeGlobalPermissionGrantDecision(rawRule, newDecision) },
                groupChatsByWorkspace = groupChatsByWorkspace,
                onToggleGroupChatsByWorkspace = { viewModel.setGroupChatsByWorkspace(it) },
                isFloatingDiagnosticsEnabled = isFloatingDiagnosticsEnabled,
                onToggleFloatingDiagnostics = { viewModel.setFloatingDiagnosticsEnabled(it) },
                isNetworkInspectorEnabled = isNetworkInspectorEnabled,
                onToggleNetworkInspector = { viewModel.setNetworkInspectorEnabled(it) },
                isFloatingNetworkInspectorEnabled = isFloatingNetworkInspectorEnabled,
                onToggleFloatingNetworkInspector = { viewModel.setFloatingNetworkInspectorEnabled(it) },
                onOpenNetworkInspector = { showNetworkInspectorDialog = true },
                claude = claudeSettingsDeps,
                initialClaudePage = claudeSettingsPage,
                initialSection = engineInitialSection,
                enabledAgents = enabledAgents,
                isApplyingAgents = isApplyingAgents,
                onApplyAgents = { viewModel.applyAgents(it) },
                autoApprove = autoApprove,
                onAutoApproveChange = onAutoApproveChange,
                onDismiss = onDismiss
            )

            if (showLocalToolsInstallDialog) {
                LocalToolsInstallDialog(
                    authPreferences = viewModel.authPreferences,
                    onOpenTerminal = onOpenTerminal,
                    onDismiss = { showLocalToolsInstallDialog = false }
                )
            }
            if (showNetworkInspectorDialog) {
                com.example.gemini.ui.components.NetworkInspectorDialog(
                    onDismiss = { showNetworkInspectorDialog = false }
                )
            }
        }
    }
}
