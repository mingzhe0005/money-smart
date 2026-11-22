package com.moneysmart.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moneysmart.app.data.local.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = CoralAccent,
    onPrimary = Color.White,
    primaryContainer = PeachSoft,
    onPrimaryContainer = EspressoInk,
    secondary = EspressoInk,
    onSecondary = Color.White,
    secondaryContainer = TrackBeige,
    onSecondaryContainer = EspressoInk,
    tertiary = ApricotHero,
    onTertiary = EspressoInk,
    tertiaryContainer = PeachTint,
    onTertiaryContainer = EspressoInk,
    background = WarmCanvas,
    onBackground = EspressoInk,
    surface = PureCard,
    onSurface = EspressoInk,
    surfaceDim = LightSurfaceHighest,
    surfaceBright = PureCard,
    surfaceContainerLowest = LightSurfaceLowest,
    surfaceContainerLow = LightSurfaceLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerHighest = LightSurfaceHighest,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = WarmStone,
    outline = WarmOutline,
    outlineVariant = WarmOutlineVariant,
    error = ExpenseRed,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = CoralAccent,
    onPrimary = Color.White,
    primaryContainer = EspressoSurface,
    onPrimaryContainer = PeachSoft,
    secondary = PeachSoft,
    onSecondary = EspressoInk,
    secondaryContainer = DarkContainer,
    onSecondaryContainer = DarkCreamInk,
    tertiary = ApricotHero,
    onTertiary = EspressoInk,
    background = DarkEspressoBg,
    onBackground = DarkCreamInk,
    surface = DarkCard,
    onSurface = DarkCreamInk,
    surfaceContainerLowest = DarkEspressoBg,
    surfaceContainerLow = DarkCard,
    surfaceContainer = DarkContainer,
    surfaceContainerHigh = DarkContainerHigh,
    surfaceContainerHighest = DarkContainerHighest,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkWarmMuted,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = ExpenseRedDark,
    onError = DarkEspressoBg
)

val MoneySmartShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
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
        shapes = MoneySmartShapes,
        content = content
    )
}
