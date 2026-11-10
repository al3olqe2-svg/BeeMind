package com.beemind.personal.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val Colors = darkColorScheme(
    primary = BeeDesign.accent, onPrimary = BeeDesign.background,
    primaryContainer = Color(0xFF2A2941), onPrimaryContainer = BeeDesign.ink,
    background = BeeDesign.background, onBackground = BeeDesign.ink,
    surface = BeeDesign.background, onSurface = BeeDesign.ink,
    surfaceVariant = BeeDesign.surface, onSurfaceVariant = BeeDesign.secondary,
    surfaceDim = BeeDesign.background, surfaceBright = Color(0xFF303748),
    surfaceContainerLowest = Color(0xFF080B12), surfaceContainerLow = Color(0xFF121721),
    surfaceContainer = BeeDesign.surface, surfaceContainerHigh = Color(0xFF202634),
    surfaceContainerHighest = Color(0xFF293141), inverseSurface = BeeDesign.ink,
    inverseOnSurface = BeeDesign.background, inversePrimary = Color(0xFF51407B),
    outline = Color(0xFF818CA2), outlineVariant = BeeDesign.border,
    error = Color(0xFFFFA5AD), errorContainer = Color(0xFF40232D), onErrorContainer = Color(0xFFFFDADF),
)

@Composable
fun BeeMindTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = Colors, typography = BeeDesign.typography,
            shapes = Shapes(medium = BeeDesign.button, large = BeeDesign.card, extraLarge = BeeDesign.panel),
            content = { Surface(Modifier.fillMaxSize(), color = Colors.background, contentColor = Colors.onBackground, content = content) })
    }
}
