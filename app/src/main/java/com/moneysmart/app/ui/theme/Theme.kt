package com.moneysmart.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.moneysmart.app.data.local.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = EmeraldOnPrimary,
    primaryContainer = EmeraldPrimaryContainer,
    onPrimaryContainer = EmeraldOnPrimaryContainer,
    secondary = SlateSecondary,
    onSecondary = SlateOnSecondary,
    secondaryContainer = SlateSecondaryContainer,
    onSecondaryContainer = SlateOnSecondaryContainer,
    tertiary = AccentBlue,
    background = LightBackground,
    surface = LightSurface,
    surfaceDim = LightSurfaceContainerHighest,
    surfaceBright = LightSurface,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    surfaceVariant = LightSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    onBackground = LightOnSurface,
    onSurface = LightOnSurface,
    onSurfaceVariant = LightOnSurfaceVariant,
    error = ExpenseRed
)

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldPrimaryContainer,
    onPrimary = EmeraldOnPrimaryContainer,
    primaryContainer = EmeraldPrimary,
    onPrimaryContainer = EmeraldPrimaryContainer,
    secondary = SlateSecondaryContainer,
    onSecondary = SlateOnSecondaryContainer,
    secondaryContainer = SlateSecondary,
    onSecondaryContainer = SlateSecondaryContainer,
    tertiary = TransferBlueDark,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    surfaceVariant = DarkSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    onBackground = DarkOnSurface,
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = ExpenseRedDark
)

@Composable
fun MoneySmartTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = MoneySmartTypography,
        content = content
    )
}
