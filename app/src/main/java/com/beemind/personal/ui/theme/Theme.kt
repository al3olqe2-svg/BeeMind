package com.beemind.personal.ui.theme

import androidx.compose.material3.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val EditorialColors = lightColorScheme(
    primary = BeeDesign.control, onPrimary = BeeDesign.onControl,
    primaryContainer = Color(0xFFE7DFF0), onPrimaryContainer = Color(0xFF322240),
    background = BeeDesign.editorialBackground, onBackground = BeeDesign.editorialForeground,
    surface = BeeDesign.editorialBackground, onSurface = BeeDesign.editorialForeground,
    surfaceVariant = Color(0xFFE7E9EE), onSurfaceVariant = BeeDesign.secondaryText,
    surfaceContainerLowest = BeeDesign.editorialSurface, surfaceContainerLow = Color(0xFFF6F6F8),
    surfaceContainer = Color(0xFFEDEEF2), surfaceContainerHigh = Color(0xFFE7E9EE),
    surfaceContainerHighest = Color(0xFFE1E3EA),
    outline = BeeDesign.mutedText, outlineVariant = BeeDesign.editorialBorder,
    error = BeeDesign.error, onError = Color.White,
    errorContainer = Color(0xFFF9DFE3), onErrorContainer = Color(0xFF65172A), scrim = BeeDesign.scrim,
)
private val AtmosphericColors = darkColorScheme(
    primary = BeeDesign.darkAccent, onPrimary = BeeDesign.darkBackground,
    background = BeeDesign.darkBackground, onBackground = BeeDesign.darkForeground,
    surface = BeeDesign.darkSurface, onSurface = BeeDesign.darkForeground,
    surfaceVariant = BeeDesign.darkSurface, onSurfaceVariant = BeeDesign.darkSecondary,
    outline = BeeDesign.darkSecondary, outlineVariant = BeeDesign.darkBorder,
    error = Color(0xFFFFB2BE), errorContainer = Color(0xFF492632), onErrorContainer = Color(0xFFFFDAE1),
)

@Composable
fun AtmosphericTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AtmosphericColors) {
        CompositionLocalProvider(LocalContentColor provides BeeDesign.darkForeground, content = content)
    }
}

@Composable
fun BeeMindTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = EditorialColors, typography = BeeDesign.typography,
            shapes = Shapes(medium = BeeDesign.button, large = BeeDesign.card, extraLarge = BeeDesign.panel)) {
            Surface(Modifier.fillMaxSize(), color = EditorialColors.background,
                contentColor = EditorialColors.onBackground, content = content)
        }
    }
}
