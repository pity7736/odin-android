package dev.raiseexception.odin.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Slate800,
    onPrimary = Slate50,
    primaryContainer = Slate100,
    onPrimaryContainer = Slate900,
    secondary = OrangePrimary,
    onSecondary = Color.White,
    secondaryContainer = OrangeSubtle,
    onSecondaryContainer = OrangeActive,
    tertiary = Slate600,
    onTertiary = Color.White,
    background = Slate50,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate600,
    outline = Slate200,
    outlineVariant = Slate100,
    error = ExpenseRed,
    onError = Color.White,
    errorContainer = ExpenseBadge,
    onErrorContainer = ExpenseDark,
)

@Composable
fun OdinTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = OdinTypography,
        content = content
    )
}
