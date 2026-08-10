package com.kesepain.kemoapp.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

enum class KemoTone(val seed: Color) {
    Purple(Color(0xFF6750A4)),
    Blue(Color(0xFF4355B9)),
    Green(Color(0xFF006A60)),
    Orange(Color(0xFF8B5000)),
}

object KemoColors {
    val Success = Color(0xFF4CAF50)
    val SuccessDark = Color(0xFF81C995)
    val Warning = Color(0xFFF4B400)
    val WarningDark = Color(0xFFFDD663)
    val OnSurfaceWeakLight = Color(0xFF9CA3AF)
    val OnSurfaceWeakDark = Color(0xFF8B8790)
}

private data class TonePalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
)

private val purpleLight = TonePalette(
    Color(0xFF6750A4), Color.White, Color(0xFFEADDFF), Color(0xFF21005D),
    Color(0xFF625B71), Color(0xFFE8DEF8), Color(0xFF1D192B),
    Color(0xFF7D5260), Color(0xFFFFD8E4), Color(0xFF31111D),
)
private val blueLight = TonePalette(
    Color(0xFF4355B9), Color.White, Color(0xFFDBE1FF), Color(0xFF00125A),
    Color(0xFF5A5D72), Color(0xFFDEE1F9), Color(0xFF171A2C),
    Color(0xFF7A5265), Color(0xFFFFD8E4), Color(0xFF31111D),
)
private val greenLight = TonePalette(
    Color(0xFF006A60), Color.White, Color(0xFFB2F5EB), Color(0xFF00201C),
    Color(0xFF4B635F), Color(0xFFCDE8E3), Color(0xFF06201C),
    Color(0xFF436279), Color(0xFFC9E6FF), Color(0xFF001F31),
)
private val orangeLight = TonePalette(
    Color(0xFF8B5000), Color.White, Color(0xFFFFDCC2), Color(0xFF2C1600),
    Color(0xFF745A42), Color(0xFFFFDCC2), Color(0xFF2A1806),
    Color(0xFF58642A), Color(0xFFDCEAA2), Color(0xFF161D00),
)

private val purpleDark = TonePalette(
    Color(0xFFD0BCFF), Color(0xFF381E72), Color(0xFF4F378B), Color(0xFFEADDFF),
    Color(0xFFCCC2DC), Color(0xFF4A4458), Color(0xFFE8DEF8),
    Color(0xFFEFB8C8), Color(0xFF633B48), Color(0xFFFFD8E4),
)
private val blueDark = TonePalette(
    Color(0xFFBEC6FF), Color(0xFF0A1E8F), Color(0xFF2A3AA0), Color(0xFFDBE1FF),
    Color(0xFFC2C6DD), Color(0xFF464A5E), Color(0xFFDEE1F9),
    Color(0xFFEFB8C8), Color(0xFF633B48), Color(0xFFFFD8E4),
)
private val greenDark = TonePalette(
    Color(0xFF4DD9CB), Color(0xFF003731), Color(0xFF005048), Color(0xFF6FF7E9),
    Color(0xFFB0CCC7), Color(0xFF394B47), Color(0xFFCDE8E3),
    Color(0xFFA7C8EB), Color(0xFF3F4A5E), Color(0xFFC9E6FF),
)
private val orangeDark = TonePalette(
    Color(0xFFFFB77C), Color(0xFF482900), Color(0xFF663D00), Color(0xFFFFDCC2),
    Color(0xFFE3BF9F), Color(0xFF54442F), Color(0xFFFFDCC2),
    Color(0xFFC8D6A0), Color(0xFF444D2B), Color(0xFFDCEAA2),
)

fun kemoColorScheme(tone: KemoTone, dark: Boolean): ColorScheme {
    val palette = when (tone) {
        KemoTone.Purple -> if (dark) purpleDark else purpleLight
        KemoTone.Blue -> if (dark) blueDark else blueLight
        KemoTone.Green -> if (dark) greenDark else greenLight
        KemoTone.Orange -> if (dark) orangeDark else orangeLight
    }
    return if (dark) {
        darkColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            primaryContainer = palette.primaryContainer,
            onPrimaryContainer = palette.onPrimaryContainer,
            secondary = palette.secondary,
            onSecondary = Color(0xFF332D41),
            secondaryContainer = palette.secondaryContainer,
            onSecondaryContainer = palette.onSecondaryContainer,
            tertiary = palette.tertiary,
            onTertiary = Color(0xFF492532),
            tertiaryContainer = palette.tertiaryContainer,
            onTertiaryContainer = palette.onTertiaryContainer,
            error = Color(0xFFF2B8B5),
            onError = Color(0xFF601410),
            errorContainer = Color(0xFF8C1D18),
            onErrorContainer = Color(0xFFF9DEDC),
            background = Color(0xFF141218),
            onBackground = Color(0xFFE6E0E9),
            surface = Color(0xFF141218),
            onSurface = Color(0xFFE6E0E9),
            surfaceVariant = Color(0xFF36343B),
            onSurfaceVariant = Color(0xFFCAC4D0),
            outline = Color(0xFF938F99),
            outlineVariant = Color(0xFF49454F),
            inverseSurface = Color(0xFFE6E0E9),
            inverseOnSurface = Color(0xFF322F35),
            inversePrimary = Color(0xFF6750A4),
            surfaceContainerLowest = Color(0xFF0F0D13),
            surfaceContainerLow = Color(0xFF1D1B20),
            surfaceContainer = Color(0xFF211F26),
            surfaceContainerHigh = Color(0xFF2B2930),
            surfaceContainerHighest = Color(0xFF36343B),
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            primaryContainer = palette.primaryContainer,
            onPrimaryContainer = palette.onPrimaryContainer,
            secondary = palette.secondary,
            onSecondary = Color.White,
            secondaryContainer = palette.secondaryContainer,
            onSecondaryContainer = palette.onSecondaryContainer,
            tertiary = palette.tertiary,
            onTertiary = Color.White,
            tertiaryContainer = palette.tertiaryContainer,
            onTertiaryContainer = palette.onTertiaryContainer,
            error = Color(0xFFB3261E),
            onError = Color.White,
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF410E0B),
            background = Color.White,
            onBackground = Color(0xFF171717),
            surface = Color.White,
            onSurface = Color(0xFF171717),
            surfaceVariant = Color(0xFFE4E4E4),
            onSurfaceVariant = Color(0xFF49454F),
            outline = Color(0xFF79747E),
            outlineVariant = Color(0xFFCAC4D0),
            inverseSurface = Color(0xFF322F35),
            inverseOnSurface = Color(0xFFF4EFF4),
            inversePrimary = Color(0xFFD0BCFF),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFF7F2FA),
            surfaceContainer = Color(0xFFF5F5F5),
            surfaceContainerHigh = Color(0xFFEBEBEB),
            surfaceContainerHighest = Color(0xFFE4E4E4),
        )
    }
}
