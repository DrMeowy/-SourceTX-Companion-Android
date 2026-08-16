package com.sourcetx.companion.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Immutable
data class SourceTxColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val borderHover: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,

    // Cards
    val installBg: Color,
    val installBorder: Color,
    val installPill: Color,
    val installAccent: Color,

    val updateBg: Color,
    val updateBorder: Color,
    val updatePill: Color,
    val updateAccent: Color,

    val configBg: Color,
    val configBorder: Color,
    val configPill: Color,
    val configAccent: Color,

    val backupBg: Color,
    val backupBorder: Color,
    val backupPill: Color,
    val backupAccent: Color,

    val restoreBg: Color,
    val restoreBorder: Color,
    val restorePill: Color,
    val restoreAccent: Color
)

val DarkSourceTxColors = SourceTxColors(
    background = DarkBackground,
    surface = DarkSurface,
    surfaceElevated = DarkSurfaceElevated,
    border = DarkBorder,
    borderHover = DarkBorderHover,
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    textMuted = DarkTextMuted,
    accent = CyanAccent,

    installBg = CardInstallBgDark,
    installBorder = CardInstallBorderDark,
    installPill = CardInstallPillDark,
    installAccent = CardInstallAccent,

    updateBg = CardUpdateBgDark,
    updateBorder = CardUpdateBorderDark,
    updatePill = CardUpdatePillDark,
    updateAccent = CardUpdateAccent,

    configBg = CardConfigBgDark,
    configBorder = CardConfigBorderDark,
    configPill = CardConfigPillDark,
    configAccent = CardConfigAccent,

    backupBg = CardBackupBgDark,
    backupBorder = CardBackupBorderDark,
    backupPill = CardBackupPillDark,
    backupAccent = CardBackupAccent,

    restoreBg = CardRestoreBgDark,
    restoreBorder = CardRestoreBorderDark,
    restorePill = CardRestorePillDark,
    restoreAccent = CardRestoreAccent
)

val LightSourceTxColors = SourceTxColors(
    background = LightBackground,
    surface = LightSurface,
    surfaceElevated = LightSurfaceElevated,
    border = LightBorder,
    borderHover = LightBorderHover,
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    textMuted = LightTextMuted,
    accent = CyanAccent,

    installBg = CardInstallBgLight,
    installBorder = CardInstallBorderLight,
    installPill = CardInstallPillLight,
    installAccent = CardInstallAccent,

    updateBg = CardUpdateBgLight,
    updateBorder = CardUpdateBorderLight,
    updatePill = CardUpdatePillLight,
    updateAccent = CardUpdateAccent,

    configBg = CardConfigBgLight,
    configBorder = CardConfigBorderLight,
    configPill = CardConfigPillLight,
    configAccent = CardConfigAccent,

    backupBg = CardBackupBgLight,
    backupBorder = CardBackupBorderLight,
    backupPill = CardBackupPillLight,
    backupAccent = CardBackupAccent,

    restoreBg = CardRestoreBgLight,
    restoreBorder = CardRestoreBorderLight,
    restorePill = CardRestorePillLight,
    restoreAccent = CardRestoreAccent
)

val LocalSourceTxColors = staticCompositionLocalOf { DarkSourceTxColors }

object SourceTxTheme {
    val colors: SourceTxColors
        @Composable
        get() = LocalSourceTxColors.current
}

@Composable
fun SourceTxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkSourceTxColors else LightSourceTxColors

    val materialColorScheme = if (darkTheme) {
        darkColorScheme(
            background = DarkBackground,
            surface = DarkSurface,
            primary = CyanAccent,
            onBackground = DarkTextPrimary,
            onSurface = DarkTextPrimary
        )
    } else {
        lightColorScheme(
            background = LightBackground,
            surface = LightSurface,
            primary = CyanAccent,
            onBackground = LightTextPrimary,
            onSurface = LightTextPrimary
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.surfaceElevated.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalSourceTxColors provides colors) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = Typography,
            content = content
        )
    }
}
