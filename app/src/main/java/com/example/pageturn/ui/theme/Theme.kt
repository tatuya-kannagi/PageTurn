package net.kannagi.pageturn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val PageTurnColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Surface,
    primaryContainer = PrimaryTint,
    onPrimaryContainer = PrimaryDark,
    secondary = TextMuted,
    onSecondary = Surface,
    secondaryContainer = PrimaryTint,
    onSecondaryContainer = PrimaryDark,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = Background,
    onSurfaceVariant = TextMuted,
    outline = BorderStrong,
    outlineVariant = BorderSoft,
    error = Warning,
    onError = Surface,
    errorContainer = Warning,
    onErrorContainer = Surface,
)

@Composable
fun PageTurnTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PageTurnColorScheme,
        typography = Typography,
        content = content,
    )
}
