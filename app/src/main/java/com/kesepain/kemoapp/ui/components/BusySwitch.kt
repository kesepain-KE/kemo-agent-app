package com.kesepain.kemoapp.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kesepain.kemoapp.R

@Composable
fun BusySwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    busy: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val pendingDescription = stringResource(R.string.loading)
    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .then(if (busy) Modifier.semantics { contentDescription = pendingDescription } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Switch(
            checked = checked,
            onCheckedChange = { target ->
                haptic.performHapticFeedback(
                    if (target) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                )
                onCheckedChange(target)
            },
            enabled = enabled && !busy,
        )
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
            )
        }
    }
}
