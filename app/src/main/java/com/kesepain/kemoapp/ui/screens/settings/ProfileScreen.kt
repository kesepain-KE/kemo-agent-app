package com.kesepain.kemoapp.ui.screens.settings

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.kesepain.kemoapp.R
import com.kesepain.kemoapp.data.local.AccountConfig
import com.kesepain.kemoapp.data.local.AccountTransferCodec
import com.kesepain.kemoapp.ui.components.LoadingOutlinedButton
import com.kesepain.kemoapp.ui.components.SectionHeader
import com.kesepain.kemoapp.ui.components.StatusChip
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Composable
fun ProfileScreen(
    accounts: List<AccountConfig>,
    currentId: String,
    connected: Boolean,
    notifications: Boolean,
    tone: String,
    themeMode: String,
    avatarBytes: ByteArray?,
    versions: JsonElement?,
    status: JsonElement?,
    accountImportBusy: Boolean,
    accountExportBusy: Boolean,
    onSwitch: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: () -> Unit,
    onImport: (Uri, String) -> Unit,
    onExport: (String, Uri, String) -> Unit,
    onSettings: () -> Unit,
    onConfiguration: () -> Unit,
    onAppSettings: () -> Unit,
    onNotifications: () -> Unit,
    onSecurity: () -> Unit,
    onStatus: () -> Unit,
    onFrameworkVersion: () -> Unit,
    onAppVersion: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val accountTransferBusy = accountImportBusy || accountExportBusy
    val appVersion = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull().orEmpty().ifBlank { "—" }
    }
    val current = accounts.firstOrNull { it.id == currentId }
    val versionRoot = versions as? JsonObject
    val statusRoot = status as? JsonObject
    val health = statusRoot?.get("health") as? JsonObject
    val frameworkVersion = (versionRoot?.get("version") as? JsonPrimitive)?.contentOrNull
        ?: (health?.get("version") as? JsonPrimitive)?.contentOrNull
        ?: "—"
    var actionAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    var exportPasswordAccountId by remember { mutableStateOf<String?>(null) }
    var exportPassword by remember { mutableStateOf("") }
    var exportPasswordConfirmation by remember { mutableStateOf("") }
    var pendingExport by remember { mutableStateOf<PendingAccountExport?>(null) }
    var importSource by remember { mutableStateOf<Uri?>(null) }
    var importPassword by remember { mutableStateOf("") }
    val createAccountFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(AccountTransferCodec.MIME_TYPE),
    ) { destination ->
        val request = pendingExport
        pendingExport = null
        if (destination != null && request != null) {
            onExport(request.accountId, destination, request.password)
        }
    }
    val openAccountFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { source ->
        if (source != null) {
            importPassword = ""
            importSource = source
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(avatarBytes, current?.displayName?.ifBlank { current.username }.orEmpty())
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(current?.displayName?.ifBlank { current.username } ?: stringResource(R.string.profile_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.kemo_account), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusChip(if (connected) stringResource(R.string.connected) else stringResource(R.string.disconnected))
            }
        }

        item { SectionHeader(stringResource(R.string.account)) }
        items(accounts, key = { it.id }) { account ->
            Card(
                modifier = Modifier.fillMaxWidth().combinedClickable(
                    onClick = { actionAccountId = account.id },
                    onLongClick = { actionAccountId = account.id },
                ),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (account.id == currentId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(account.displayName.ifBlank { account.username }, style = MaterialTheme.typography.titleMedium)
                        Text(account.baseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (account.id != currentId) OutlinedButton(onClick = { onSwitch(account.id) }) { Text(stringResource(R.string.switch_account)) }
                }
            }
        }
        item {
            OutlinedButton(onClick = onAdd, enabled = !accountTransferBusy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.add_account))
            }
        }
        item {
            LoadingOutlinedButton(
                onClick = {
                    openAccountFile.launch(
                        arrayOf(AccountTransferCodec.MIME_TYPE, "application/octet-stream"),
                    )
                },
                enabled = !accountTransferBusy,
                loading = accountImportBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.import_account))
            }
        }

        item { SectionHeader(stringResource(R.string.configuration)) }
        item { NavigationCard(stringResource(R.string.agent_configuration), stringResource(R.string.agent_configuration_summary), onConfiguration) }
        item { NavigationCard(stringResource(R.string.app_configuration), stringResource(R.string.app_configuration_summary), onAppSettings) }
        item { SectionHeader(stringResource(R.string.security)) }
        item { NavigationCard(stringResource(R.string.security), stringResource(R.string.security_summary), onSecurity) }
        item { SectionHeader(stringResource(R.string.notifications)) }
        item { NavigationCard(stringResource(R.string.push_notifications), stringResource(if (notifications) R.string.enabled else R.string.disabled), onNotifications) }
        item { SectionHeader(stringResource(R.string.appearance)) }
        item { NavigationCard(stringResource(R.string.theme), "$tone · $themeMode", onSettings) }
        item { SectionHeader(stringResource(R.string.status_title)) }
        item { NavigationCard(stringResource(R.string.status_title), stringResource(R.string.status_summary), onStatus) }
        item { SectionHeader(stringResource(R.string.about)) }
        item { NavigationCard(stringResource(R.string.about_framework_version), frameworkVersion, onFrameworkVersion) }
        item { NavigationCard(stringResource(R.string.about_app_version), appVersion, onAppVersion) }
        item { OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.logout)) } }
    }
    val actionAccount = accounts.firstOrNull { it.id == actionAccountId }
    if (actionAccount != null) {
        AlertDialog(
            onDismissRequest = { actionAccountId = null },
            title = { Text(actionAccount.displayName.ifBlank { actionAccount.username }) },
            text = {
                Column {
                    Text(stringResource(R.string.account_long_press_hint))
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { actionAccountId = null; onEdit(actionAccount.id) },
                        enabled = !accountTransferBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.edit_account))
                    }
                    TextButton(
                        onClick = {
                            actionAccountId = null
                            exportPassword = ""
                            exportPasswordConfirmation = ""
                            exportPasswordAccountId = actionAccount.id
                        },
                        enabled = !accountTransferBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.export_account))
                    }
                    TextButton(
                        onClick = { actionAccountId = null; onDelete(actionAccount.id) },
                        enabled = !accountTransferBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.delete_account), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { actionAccountId = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    val exportAccount = accounts.firstOrNull { it.id == exportPasswordAccountId }
    if (exportAccount != null) {
        ExportAccountPasswordDialog(
            accountName = exportAccount.displayName.ifBlank { exportAccount.username },
            password = exportPassword,
            confirmation = exportPasswordConfirmation,
            onPasswordChange = { exportPassword = it },
            onConfirmationChange = { exportPasswordConfirmation = it },
            onDismiss = {
                exportPasswordAccountId = null
                exportPassword = ""
                exportPasswordConfirmation = ""
            },
            onConfirm = {
                pendingExport = PendingAccountExport(exportAccount.id, exportPassword)
                exportPasswordAccountId = null
                exportPassword = ""
                exportPasswordConfirmation = ""
                createAccountFile.launch(exportFileName(exportAccount))
            },
        )
    }

    if (importSource != null) {
        ImportAccountPasswordDialog(
            password = importPassword,
            onPasswordChange = { importPassword = it },
            onDismiss = {
                importSource = null
                importPassword = ""
            },
            onConfirm = {
                val source = importSource
                val password = importPassword
                importSource = null
                importPassword = ""
                if (source != null) onImport(source, password)
            },
        )
    }
}

private data class PendingAccountExport(val accountId: String, val password: String)

private fun exportFileName(account: AccountConfig): String {
    val label = account.displayName.ifBlank { account.username }
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .trim()
        .take(48)
        .ifBlank { "account" }
    return "kemo-account-$label.${AccountTransferCodec.FILE_EXTENSION}"
}

@Composable
private fun ExportAccountPasswordDialog(
    accountName: String,
    password: String,
    confirmation: String,
    onPasswordChange: (String) -> Unit,
    onConfirmationChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val passwordLongEnough = password.length >= AccountTransferCodec.MIN_PASSWORD_LENGTH
    val passwordsMatch = password == confirmation
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_account)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.account_export_description, accountName))
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.account_transfer_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = onConfirmationChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.confirm_account_transfer_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    isError = confirmation.isNotEmpty() && !passwordsMatch,
                    singleLine = true,
                )
                Text(
                    stringResource(R.string.account_transfer_password_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = passwordLongEnough && passwordsMatch) {
                Text(stringResource(R.string.choose_export_file))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ImportAccountPasswordDialog(
    password: String,
    onPasswordChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_account)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.account_import_description))
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.account_transfer_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = password.isNotEmpty()) {
                Text(stringResource(R.string.import_account))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun UserAvatar(bytes: ByteArray?, username: String) {
    val bitmap = remember(bytes?.contentHashCode()) {
        bytes?.let { value -> runCatching { BitmapFactory.decodeByteArray(value, 0, value.size)?.asImageBitmap() }.getOrNull() }
    }
    Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap, stringResource(R.string.user_avatar), Modifier.fillMaxSize())
        else Text(username.take(1).uppercase().ifBlank { "K" }, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun NavigationCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
