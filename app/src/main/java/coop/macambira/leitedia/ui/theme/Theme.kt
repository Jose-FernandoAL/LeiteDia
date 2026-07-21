package coop.macambira.leitedia.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Blue600,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEAFF),
    onPrimaryContainer = Navy900,
    secondary = Cyan500,
    onSecondary = Navy900,
    tertiary = Green600,
    error = Danger,
    background = AppBackground,
    onBackground = TextPrimary,
    surface = CardBackground,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFEDF2F8),
    onSurfaceVariant = TextMuted,
    outline = BorderSoft
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7DB3FF),
    onPrimary = Navy900,
    primaryContainer = Navy800,
    secondary = Cyan500,
    tertiary = Color(0xFF54D4A4),
    background = Color(0xFF061426),
    surface = Color(0xFF0B203B),
    surfaceVariant = Navy800,
    onBackground = Color(0xFFEAF1FA),
    onSurface = Color(0xFFEAF1FA),
    outline = Color(0xFF36516F)
)

@Composable
fun LeiteDiaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
