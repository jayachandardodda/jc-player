package com.jc.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.jc.player.data.ThemeMode

val Accents = listOf(
    "Blue" to Color(0xFF4F8CFF),
    "Violet" to Color(0xFF9B6BFF),
    "Teal" to Color(0xFF1FBFAE),
    "Orange" to Color(0xFFFF8A3D),
    "Pink" to Color(0xFFFF5C8A),
    "Green" to Color(0xFF4CC36B),
)

@Composable
fun JcTheme(mode: ThemeMode, accentIndex: Int, content: @Composable () -> Unit) {
    val accent = Accents.getOrElse(accentIndex) { Accents[0] }.second
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val scheme = if (dark) {
        val bg = if (mode == ThemeMode.AMOLED) Color.Black else Color(0xFF111318)
        val surface = if (mode == ThemeMode.AMOLED) Color(0xFF0A0A0A) else Color(0xFF181B22)
        darkColorScheme(
            primary = accent,
            onPrimary = Color.White,
            secondary = accent,
            background = bg,
            surface = bg,
            surfaceContainer = surface,
            surfaceContainerHigh = if (mode == ThemeMode.AMOLED) Color(0xFF141414) else Color(0xFF20242C),
            surfaceContainerHighest = if (mode == ThemeMode.AMOLED) Color(0xFF1C1C1C) else Color(0xFF282C35),
            surfaceVariant = surface,
        )
    } else {
        lightColorScheme(primary = accent, onPrimary = Color.White, secondary = accent)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
