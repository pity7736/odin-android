package dev.raiseexception.odin.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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

private val DarkColorScheme = darkColorScheme(
    primary = Slate50,
    onPrimary = Slate900,
    primaryContainer = Slate800,
    onPrimaryContainer = Slate50,
    secondary = OrangePrimary,
    onSecondary = Slate900,
    secondaryContainer = OrangeActive,
    onSecondaryContainer = OrangeSubtle,
    tertiary = Slate400,
    onTertiary = Slate900,
    background = Slate900,
    onBackground = Slate50,
    surface = Slate800,
    onSurface = Slate50,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate400,
    outline = Slate500,
    outlineVariant = Slate600,
    error = ExpenseRed,
    onError = Slate900,
    errorContainer = ExpenseDark,
    onErrorContainer = ExpenseBadge,
)

@Composable
fun OdinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = OdinTypography,
        content = content
    )
}
