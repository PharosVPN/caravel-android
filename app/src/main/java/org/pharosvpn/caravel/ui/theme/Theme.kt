// SPDX-License-Identifier: Apache-2.0
// Copyright (C) 2026 The PharosVPN Authors

package org.pharosvpn.caravel.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ───────── brand palette (matches caravel-mac + the website) ─────────
val Maroon = Color(0xFF5A1F2B)
val Cream = Color(0xFFF4EBDD)
val Teal = Color(0xFF4FD1C4) // LandMap.teal (0.31, 0.82, 0.77)
val Control = Color(0xFF9E8CF2) // LandMap.control (0.62, 0.55, 0.95)

// Deep-ocean surfaces (LandMap gradient stops).
val Ocean = Color(0xFF080B12)
val OceanDeep = Color(0xFF050608)
val Panel = Color(0xFF12161D) // sidebar background (0.07, 0.09, 0.13)
val PanelElevated = Color(0xFF1A1F28)

private val CaravelDark = darkColorScheme(
    primary = Teal,
    onPrimary = Color(0xFF06201D),
    secondary = Control,
    onSecondary = Color.White,
    tertiary = Maroon,
    background = Ocean,
    onBackground = Cream,
    surface = Panel,
    onSurface = Cream,
    surfaceVariant = PanelElevated,
    onSurfaceVariant = Color(0xFFB9C0CC),
    error = Color(0xFFFF6B6B),
)

@Composable
fun CaravelTheme(content: @Composable () -> Unit) {
    val colors = CaravelDark // the app is intentionally always dark (the map)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}

@Suppress("unused")
private val isDarkPreview @Composable get() = isSystemInDarkTheme()
