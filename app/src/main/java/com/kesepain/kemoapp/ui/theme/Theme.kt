package com.kesepain.kemoapp.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun KemoTheme(
    tone: KemoTone,
    darkTheme: Boolean,
    dynamicColor: Boolean = false,
    backgroundActive: Boolean = false,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val baseScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(view.context) else dynamicLightColorScheme(view.context)
    } else kemoColorScheme(tone, darkTheme)
    val scheme = if (backgroundActive) {
        baseScheme.copy(
            background = baseScheme.background.copy(alpha = 0.42f),
            surface = baseScheme.surface.copy(alpha = 0.58f),
            surfaceVariant = baseScheme.surfaceVariant.copy(alpha = 0.68f),
            surfaceContainerLowest = baseScheme.surfaceContainerLowest.copy(alpha = 0.50f),
            surfaceContainerLow = baseScheme.surfaceContainerLow.copy(alpha = 0.68f),
            surfaceContainer = baseScheme.surfaceContainer.copy(alpha = 0.72f),
            surfaceContainerHigh = baseScheme.surfaceContainerHigh.copy(alpha = 0.76f),
            surfaceContainerHighest = baseScheme.surfaceContainerHighest.copy(alpha = 0.80f),
            primaryContainer = baseScheme.primaryContainer.copy(alpha = 0.78f),
            secondaryContainer = baseScheme.secondaryContainer.copy(alpha = 0.76f),
            tertiaryContainer = baseScheme.tertiaryContainer.copy(alpha = 0.76f),
        )
    } else baseScheme
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = (if (backgroundActive) Color.Transparent else scheme.surface).toArgb()
            window.navigationBarColor = (if (backgroundActive) Color.Transparent else scheme.surfaceContainer).toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = KemoTypography,
        shapes = KemoShapes,
        content = content,
    )
}
