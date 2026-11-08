package com.bettercallsoul.ui

import com.bettercallsoul.ThemeChoice
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.bettercallsoul.container
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CallGreen = Color(0xFF12A85F)
val EndRed = Color(0xFFE5484D)
val MessageBlue = Color(0xFF2F8CF0)
val WhatsAppGreen = Color(0xFF25D366)
val SpamOrange = Color(0xFFF2994A)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FB2FF),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceContainer = Color(0xFF1B1B1D),
    surfaceContainerHigh = Color(0xFF28282B),
    onSurfaceVariant = Color(0xFFA3A3A8),
    outlineVariant = Color(0xFF2E2E31),
)

private val Light = lightColorScheme(
    primary = Color(0xFF2264D1),
    background = Color(0xFFF2F2F4),
    onBackground = Color(0xFF111113),
    surface = Color(0xFFF2F2F4),
    onSurface = Color(0xFF111113),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFE9E9EC),
    onSurfaceVariant = Color(0xFF6C6C72),
    outlineVariant = Color(0xFFE2E2E6),
)

@Composable
fun BcsTheme(dark: Boolean? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Without a fixed choice from the screen, the user's theme; on Android 12 and later the system
    // already applies it, and this keeps older phones in step.
    val systemDark = isSystemInDarkTheme()
    val night = dark ?: when (context.container.themeChoice) {
        ThemeChoice.System -> systemDark
        ThemeChoice.Light -> false
        ThemeChoice.Dark -> true
    }
    var system by remember { mutableStateOf(systemAnimationsOn(context)) }
    // The system switch can change while the app sits in the background.
    LifecycleResumeEffect(Unit) {
        system = systemAnimationsOn(context)
        onPauseOrDispose { }
    }
    val motion = system && context.container.animationsEnabled
    MaterialTheme(colorScheme = if (night) Dark else Light) {
        CompositionLocalProvider(LocalMotion provides motion, content = content)
    }
}
