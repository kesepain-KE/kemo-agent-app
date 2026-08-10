package com.kesepain.kemoapp.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.kesepain.kemoapp.ui.screens.config.UserConfigDraft
import com.kesepain.kemoapp.ui.screens.files.FilesScreen
import com.kesepain.kemoapp.ui.screens.modules.ModulesScreen
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
    val pendingKeys by viewModel.pendingKeys.collectAsState()
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route.orEmpty()
    val mainRoute = tabs.any { it.route == route }
    val providerType = UserConfigDraft.from(state.agentConfig).providerType
    LaunchedEffect(Unit) { viewModel.loadDashboard() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collectLatest { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = message.text,
                duration = if (message.type == UiMessageType.Error) SnackbarDuration.Long else SnackbarDuration.Short,
            )
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (mainRoute) NavigationBar(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
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
                    state.streaming,
                    state.chatClosed,
                    viewModel::loadConversations,
                    viewModel::switchConversation,
                    viewModel::deleteConversation,
                    viewModel::deleteAllConversations,
                    viewModel::sendChat,
                    viewModel::clearConversation,
                    viewModel::saveConversation,
                    viewModel::compressConversation,
                    viewModel::saveAndNewConversation,
                    state.pendingChatAttachments,
                    state.chatAttachmentUploading,
                    viewModel::addChatAttachment,
                    viewModel::removeChatAttachment,
                )
            }
            composable("tasks") { TasksScreen(state.tasks, state.cron, pendingKeys, viewModel::loadTasks, viewModel::taskAction, viewModel::updateCron) }
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
                    viewModel::switchAccount,
                    onAdd = { navController.navigate("connect") },
                    onSettings = { viewModel.loadAgentConfig(); navController.navigate("settings") },
                    onConfiguration = { viewModel.loadAgentConfig(); navController.navigate("agent-config") },
                    onAppSettings = { navController.navigate("app-settings") },
                    onNotifications = { navController.navigate("notifications") },
                    onSecurity = { navController.navigate("security") },
                    onStatus = { viewModel.loadStatus(); navController.navigate("status") },
                    onVersion = { viewModel.loadProfileData(); navController.navigate("versions") },
                    onLogout = viewModel::logout,
                )
            }
            composable("settings") {
                SettingsScreen(
                    state.preferences, viewModel::setTheme, viewModel::setTone, onLanguageChanged,
                    viewModel::setDynamicColor,
                    providerType = providerType,
                    onModels = { viewModel.loadModels(); navController.navigate("models") },
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
            composable("connect") {
                val current = state.preferences.accounts.firstOrNull { it.id == state.preferences.currentAccountId }
                ConnectScreen(
                    current = current,
                    busy = state.busy,
                    error = state.error,
                    rememberedDeviceToken = state.rememberedDeviceToken,
                    rememberedUserPassword = state.rememberedUserPassword,
                    initiallyRememberCredentials = state.rememberCredentials,
                    onConnect = viewModel::connect,
                )
            }
            }
        }
    }
}

