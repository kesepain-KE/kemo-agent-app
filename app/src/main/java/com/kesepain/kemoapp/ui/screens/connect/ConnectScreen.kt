package com.kesepain.kemoapp.ui.screens.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kesepain.kemoapp.R
import com.kesepain.kemoapp.data.local.AccountConfig
import com.kesepain.kemoapp.ui.components.LoadingButton
import com.kesepain.kemoapp.ui.components.LoadingFilledTonalButton
import com.kesepain.kemoapp.ui.components.LoadingOutlinedButton

@Composable
fun ConnectScreen(
    current: AccountConfig?,
    busy: Boolean,
    error: String,
    rememberedDeviceToken: String,
    rememberedUserPassword: String,
    initiallyRememberCredentials: Boolean,
    onConnect: (String, String, String, String, String, String, Boolean) -> Unit,
    onEnterDirectly: () -> Unit,
    onRename: ((String) -> Unit)? = null,
) {
    var displayName by remember(current) { mutableStateOf(current?.displayName?.ifBlank { current.username }.orEmpty()) }
    var baseUrl by remember(current) { mutableStateOf(current?.baseUrl ?: "http://10.0.2.2:8742") }
    var token by remember(current, rememberedDeviceToken) { mutableStateOf(rememberedDeviceToken) }
    var username by remember(current) { mutableStateOf(current?.username.orEmpty()) }
    var password by remember(current, rememberedUserPassword) { mutableStateOf(rememberedUserPassword) }
    var appPassword by remember { mutableStateOf("") }
    var rememberCredentials by remember(current, initiallyRememberCredentials) { mutableStateOf(initiallyRememberCredentials) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.connect_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.connect_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Field(displayName, { displayName = it }, R.string.account_name)
        if (current != null && onRename != null) {
            LoadingOutlinedButton(
                onClick = { onRename(displayName) },
                enabled = displayName.isNotBlank() && displayName.trim() != current.displayName.ifBlank { current.username },
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(bottom = 10.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text(stringResource(R.string.save_account_name)) }
        }
        Field(baseUrl, { baseUrl = it }, R.string.server_url)
        Field(token, { token = it }, R.string.device_token, true)
        Field(username, { username = it }, R.string.username)
        Field(password, { password = it }, R.string.password, true)
        Field(appPassword, { appPassword = it }, R.string.app_password, true)
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.remember_credentials), style = MaterialTheme.typography.bodyMedium)
            Checkbox(checked = rememberCredentials, onCheckedChange = { rememberCredentials = it })
        }
        Text(
            stringResource(R.string.remember_credentials_limit),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LoadingButton(
            onClick = { onConnect(displayName, baseUrl, token, username, password, appPassword, rememberCredentials) },
            enabled = baseUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
            loading = busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(18.dp),
        ) { Text(stringResource(if (busy) R.string.connecting else R.string.connect)) }
        Spacer(Modifier.height(10.dp))
        LoadingFilledTonalButton(
            onClick = onEnterDirectly,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(18.dp),
        ) { Text(stringResource(R.string.enter_app_directly)) }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: Int, password: Boolean = false) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(stringResource(label)) },
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), singleLine = true,
        shape = RoundedCornerShape(20.dp),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
    )
}
