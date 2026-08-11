package com.kesepain.kemoapp.ui.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kesepain.kemoapp.AppUiState
import com.kesepain.kemoapp.MainViewModel
import com.kesepain.kemoapp.UiMessageType
import com.kesepain.kemoapp.R
import com.kesepain.kemoapp.ui.screens.chat.ChatScreen
import com.kesepain.kemoapp.ui.screens.connect.ConnectScreen
import com.kesepain.kemoapp.ui.screens.config.AgentConfigScreen
import com.kesepain.kemoapp.ui.screens.files.FilesScreen
import com.kesepain.kemoapp.ui.screens.modules.ModulesScreen
import com.kesepain.kemoapp.ui.screens.settings.AppAboutScreen
import com.kesepain.kemoapp.ui.screens.settings.AppSettingsScreen
import com.kesepain.kemoapp.ui.screens.settings.ModelsScreen
import com.kesepain.kemoapp.ui.screens.settings.NotificationsScreen
import com.kesepain.kemoapp.ui.screens.settings.ProfileScreen
import com.kesepain.kemoapp.ui.screens.settings.SettingsScreen
import com.kesepain.kemoapp.ui.screens.settings.SecurityScreen
import com.kesepain.kemoapp.ui.screens.settings.VersionScreen
import com.kesepain.kemoapp.ui.screens.status.StatusScreen
import com.kesepain.kemoapp.ui.screens.tasks.TasksScreen
import kotlinx.coroutines.flow.collectLatest

private data class Tab(val route: String, val label: Int, val icon: ImageVector)
private val tabs = listOf(
    Tab("chat", R.string.tab_chat, Icons.Default.ChatBubbleOutline),
    Tab("tasks", R.string.tab_tasks, Icons.Default.CheckCircleOutline),
    Tab("files", R.string.tab_files, Icons.Default.Folder),
    Tab("modules", R.string.tab_modules, Icons.Default.Apps),
    Tab("profile", R.string.tab_profile, Icons.Default.PersonOutline),
)

@Composable
fun KemoNav(state: AppUiState, viewModel: MainViewModel, initialTask: Boolean = false, onLanguageChanged: (String) -> Unit) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val pendingKeys by viewModel.pendingKeys.collectAsState()
    val appAbout by viewModel.appAbout.collectAsState()
    val window = LocalView.current.context.findActivity()?.window
    var editingAccountId by remember { mutableStateOf<String?>(null) }
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route.orEmpty()
    val mainRoute = tabs.any { it.route == route }
    val backgroundActive = state.preferences.themeBackgroundUri.isNotBlank()
    LaunchedEffect(route) { if (route != "connect") editingAccountId = null }
    DisposableEffect(route, window) {
        window?.setSoftInputMode(
            if (route == "chat") WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            else WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
        )
        onDispose { }
    }
    LaunchedEffect(Unit) { viewModel.loadDashboard() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collectLatest { message ->
            when (message.type) {
                UiMessageType.Success -> haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                UiMessageType.Error -> haptic.performHapticFeedback(HapticFeedbackType.Reject)
                UiMessageType.Info -> Unit
            }
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = message.text,
                duration = if (message.type == UiMessageType.Error) SnackbarDuration.Long else SnackbarDuration.Short,
            )
        }
    }
    Scaffold(
        containerColor = if (backgroundActive) Color.Transparent else MaterialTheme.colorScheme.surface,
        // Let the chat drawer render behind the status bar. ChatScreen applies the safe inset to
        // its actual content, while the drawer sheet and scrim remain truly edge-to-edge.
        contentWindowInsets = if (route == "chat") WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (mainRoute) NavigationBar(
                modifier = Modifier.fillMaxWidth(),
                containerColor = if (backgroundActive) MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f) else MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Spacer(Modifier.width(16.dp))
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = { navController.navigate(tab.route) { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
                        icon = { Icon(tab.icon, null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
                Spacer(Modifier.width(16.dp))
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(navController, startDestination = if (initialTask) "tasks" else "chat", modifier = Modifier.fillMaxSize()) {
            composable("chat") {
                ChatScreen(
                    state.chatEntries,
                    state.conversations,
                    state.status,
                    state.streaming,
                    state.chatClosed,
                    viewModel::loadConversations,
                    viewModel::switchConversation,
                    viewModel::deleteConversation,
                    viewModel::deleteAllConversations,
                    viewModel::sendChat,
                    viewModel::clearConversation,
                    viewModel::retryLastResponse,
                    viewModel::compressConversation,
                    viewModel::saveAndNewConversation,
                    viewModel::reportCopied,
                    state.pendingChatAttachments,
                    state.chatAttachmentUploading,
                    state.guidanceSubmitting,
                    state.chatStopping,
                    viewModel::addChatAttachment,
                    viewModel::removeChatAttachment,
                    viewModel::stopChat,
                    viewModel::loadChatMedia,
                    { path -> viewModel.downloadFile("download", path) },
                    viewModel::loadChatAttachment,
                    state.filePreview,
                    state.filePreview?.let { preview ->
                        "download:${preview.scope}:${preview.path}" in pendingKeys
                    } == true,
                    viewModel::previewFile,
                    viewModel::clearFilePreview,
                    { path -> viewModel.downloadFile("upload", path) },
                )
            }
            composable("tasks") { TasksScreen(state.tasks, state.cron, pendingKeys, viewModel::loadTasks) }
            composable("status") { StatusScreen(state.status, "refresh:status" in pendingKeys, viewModel::loadStatus) }
            composable("modules") { ModulesScreen(state.expands, state.senses, pendingKeys, viewModel::loadModules, viewModel::setWhitelist) }
            composable("files") {
                FilesScreen(
                    state.uploadFiles,
                    state.generatedFiles,
                    pendingKeys,
                    state.error,
                    viewModel::loadFiles,
                    viewModel::uploadFile,
                    viewModel::downloadFile,
                    viewModel::deleteFile,
                    state.filePreview,
                    viewModel::previewFile,
                    viewModel::clearFilePreview,
                )
            }
            composable("profile") {
                LaunchedEffect(Unit) { viewModel.loadProfileData() }
                ProfileScreen(
                    state.preferences.accounts, state.preferences.currentAccountId,
                    state.configured,
                    state.preferences.notifications,
                    state.preferences.tone,
                    state.preferences.themeMode,
                    state.avatarBytes,
                    state.versions,
                    state.status,
                    MainViewModel.ACCOUNT_TRANSFER_IMPORT_KEY in pendingKeys,
                    MainViewModel.ACCOUNT_TRANSFER_EXPORT_KEY in pendingKeys,
                    viewModel::switchAccount,
                    onEdit = { accountId -> editingAccountId = accountId; navController.navigate("connect") },
                    onDelete = viewModel::deleteAccount,
                    onAdd = { navController.navigate("connect") },
                    onImport = viewModel::importAccount,
                    onExport = viewModel::exportAccount,
                    onSettings = { viewModel.loadAgentConfig(); navController.navigate("settings") },
                    onConfiguration = { viewModel.loadAgentConfig(); navController.navigate("agent-config") },
                    onAppSettings = { navController.navigate("app-settings") },
                    onNotifications = { navController.navigate("notifications") },
                    onSecurity = { navController.navigate("security") },
                    onStatus = { viewModel.loadStatus(); navController.navigate("status") },
                    onFrameworkVersion = { viewModel.loadProfileData(); navController.navigate("versions") },
                    onAppVersion = { navController.navigate("app-about") },
                    onLogout = viewModel::logout,
                )
            }
            composable("settings") {
                SettingsScreen(
                    state.preferences, viewModel::setTheme, viewModel::setTone, onLanguageChanged,
                    viewModel::setDynamicColor,
                    viewModel::setThemeBackground,
                    viewModel::resetTheme,
                )
            }
            composable("app-settings") {
                AppSettingsScreen(state.preferences.downloadDirectoryUri, viewModel::setDownloadDirectoryUri)
            }
            composable("notifications") {
                NotificationsScreen(state.preferences.notifications, viewModel::setNotifications)
            }
            composable("security") {
                SecurityScreen(
                    state.preferences.biometricEnabled,
                    viewModel::setBiometricEnabled,
                    viewModel::reportBiometricRequired,
                    viewModel::reportBiometricFailed,
                    viewModel::reportPasswordFailed,
                    viewModel::changeAppPassword,
                )
            }
            composable("models") { ModelsScreen(state.models, pendingKeys, viewModel::loadModels, viewModel::selectModel) }
            composable("agent-config") {
                AgentConfigScreen(
                    value = state.agentConfig,
                    models = state.models,
                    onRefresh = viewModel::loadAgentConfig,
                    onModelsRefresh = viewModel::loadModels,
                    busy = "config" in pendingKeys,
                    modelsRefreshing = "refresh:models" in pendingKeys,
                ) { viewModel.patchAgentConfig(it.toChanges()) }
            }
            composable("versions") { VersionScreen(state.versions) }
            composable("app-about") {
                AppAboutScreen(
                    state = appAbout,
                    onBack = { navController.popBackStack() },
                    onLoad = viewModel::loadAppAbout,
                    onCheckUpdate = viewModel::checkForAppUpdate,
                    onDownloadUpdate = viewModel::downloadAppUpdate,
                    onInstallUpdate = viewModel::installDownloadedUpdate,
                )
            }
            composable("connect") {
                var submittedAtVersion by remember { mutableStateOf<Long?>(null) }
                LaunchedEffect(state.connectionSuccessVersion, state.busy, submittedAtVersion) {
                    val submittedVersion = submittedAtVersion ?: return@LaunchedEffect
                    if (!state.busy && state.connectionSuccessVersion > submittedVersion) {
                        submittedAtVersion = null
                        editingAccountId = null
                        navController.navigate("chat") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
                // A new account starts with an empty form; only the explicit edit
                // route receives an existing account's name and endpoint.
                val current = editingAccountId?.let { accountId ->
                    state.preferences.accounts.firstOrNull { it.id == accountId }
                }
                ConnectScreen(
                    current = current,
                    busy = state.busy,
                    error = state.error,
                    rememberedDeviceToken = if (editingAccountId != null && editingAccountId == state.preferences.currentAccountId) state.rememberedDeviceToken else "",
                    rememberedUserPassword = if (editingAccountId != null && editingAccountId == state.preferences.currentAccountId) state.rememberedUserPassword else "",
                    initiallyRememberCredentials = state.rememberCredentials,
                    onConnect = { displayName, baseUrl, token, username, password, appPassword, rememberCredentials ->
                        submittedAtVersion = state.connectionSuccessVersion
                        val editing = editingAccountId
                        if (editing == null) viewModel.connect(displayName, baseUrl, token, username, password, appPassword, rememberCredentials)
                        else viewModel.reconnectAccount(editing, displayName, baseUrl, token, username, password, appPassword, rememberCredentials)
                    },
                    onEnterDirectly = { navController.popBackStack() },
                    onRename = editingAccountId?.let { accountId -> { name -> viewModel.renameAccount(accountId, name) } },
                )
            }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
