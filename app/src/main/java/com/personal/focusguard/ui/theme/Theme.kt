package com.personal.focusguard.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.personal.focusguard.data.ThemeMode

private val Dark = darkColorScheme(
    primary = Color(0xFF7FD0FF),
    secondary = Color(0xFFB8C8FF),
    tertiary = Color(0xFFFFB86B),
    background = Color(0xFF101418),
    surface = Color(0xFF101418),
)

private val Light = lightColorScheme(
    primary = Color(0xFF00658E),
    secondary = Color(0xFF4A5C92),
    tertiary = Color(0xFF8A5100),
)

/** Colores fijos para distinguir los dos tipos de límite en toda la app. */
object LimitColors {
    @Composable fun time(): Color = MaterialTheme.colorScheme.tertiary
    @Composable fun opens(): Color = MaterialTheme.colorScheme.secondary
}

/** true si la interfaz debe dibujarse en oscuro según la preferencia del usuario. */
@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun FocusGuardTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = isDarkTheme(mode)
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
