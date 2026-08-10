package com.kesepain.kemoapp.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

fun whiteBackgroundRemovalFilter(tint: Color? = null): ColorFilter {
    // The supplied WebP assets contain opaque near-white checkerboard pixels.
    // Keep only chromatic artwork: neutral pixels have B ~= R ~= G and become
    // transparent, while the blue/purple illustration survives. Including the
    // source alpha and a -255 bias prevents RGB data in already-transparent
    // pixels from becoming visible again.
    val alphaRow = floatArrayOf(-2f, -2f, 4f, 1f, -255f)
    val matrix = if (tint == null) {
        ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                *alphaRow,
            ),
        )
    } else {
        ColorMatrix(
            floatArrayOf(
                0f, 0f, 0f, 0f, tint.red * 255f,
                0f, 0f, 0f, 0f, tint.green * 255f,
                0f, 0f, 0f, 0f, tint.blue * 255f,
                *alphaRow,
            ),
        )
    }
    return ColorFilter.colorMatrix(matrix)
}
