package com.kesepain.kemoapp.ui.screens.unlock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kesepain.kemoapp.R

@Composable
fun UnlockScreen(error: String, onBiometric: (() -> Unit)?, onPassword: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(56.dp))
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.unlock_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.unlock_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            if (onBiometric != null) {
                Button(onClick = onBiometric, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Default.Fingerprint, null); Spacer(Modifier.height(4.dp)); Text(stringResource(R.string.biometric_unlock))
                }
                Spacer(Modifier.height(18.dp))
            }
            Text(stringResource(R.string.password_unlock), style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true, visualTransformation = PasswordVisualTransformation())
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { onPassword(password) }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), enabled = password.isNotBlank()) { Text(stringResource(R.string.unlock)) }
        }
    }
}
