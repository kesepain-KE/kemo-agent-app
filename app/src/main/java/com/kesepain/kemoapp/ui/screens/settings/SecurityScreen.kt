package com.kesepain.kemoapp.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.kesepain.kemoapp.R
import com.kesepain.kemoapp.security.BiometricHelper
import com.kesepain.kemoapp.ui.components.BusySwitch
import com.kesepain.kemoapp.ui.components.LoadingButton

@Composable
fun SecurityScreen(
    biometricEnabled: Boolean,
    onBiometricEnabled: (Boolean) -> Unit,
    onBiometricRequired: () -> Unit,
    onBiometricFailed: () -> Unit,
    onPasswordFailed: () -> Unit,
    onChangePassword: (String, String, (Boolean) -> Unit) -> Unit,
) {
    val activity = LocalActivity.current as? FragmentActivity
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var resultMessage by remember { mutableStateOf("") }
    var biometricBusy by remember { mutableStateOf(false) }
    var passwordBusy by remember { mutableStateOf(false) }
    val authTitle = stringResource(R.string.security_auth_title)
    val authSubtitle = stringResource(R.string.security_auth_subtitle)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.security), style = MaterialTheme.typography.headlineSmall) }
        item {
            Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.biometric_unlock), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.biometric_setting_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    BusySwitch(
                        checked = biometricEnabled,
                        onCheckedChange = { target ->
                            val host = activity
                            if (host == null) {
                                onBiometricRequired()
                            } else {
                                onBiometricRequired()
                                biometricBusy = true
                                BiometricHelper.authenticate(host, authTitle, authSubtitle) { success ->
                                    biometricBusy = false
                                    if (success) onBiometricEnabled(target) else onBiometricFailed()
                                }
                            }
                        },
                        busy = biometricBusy,
                        enabled = activity != null,
                    )
                }
            }
        }
        item {
            Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.change_app_password), style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(oldPassword, { oldPassword = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.old_app_password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    OutlinedTextField(newPassword, { newPassword = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.new_app_password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    OutlinedTextField(confirmPassword, { confirmPassword = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.confirm_app_password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                    LoadingButton(
                        onClick = {
                            if (newPassword != confirmPassword || newPassword.length < 4) {
                                resultMessage = "invalid"
                                onPasswordFailed()
                            } else {
                                passwordBusy = true
                                onChangePassword(oldPassword, newPassword) { success ->
                                    passwordBusy = false
                                    resultMessage = if (success) "success" else "failed"
                                    if (success) { oldPassword = ""; newPassword = ""; confirmPassword = "" }
                                }
                            }
                        },
                        loading = passwordBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.update_password)) }
                    if (resultMessage.isNotBlank()) {
                        Text(
                            stringResource(if (resultMessage == "success") R.string.feedback_password_updated else R.string.feedback_password_failed),
                            color = if (resultMessage == "success") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
