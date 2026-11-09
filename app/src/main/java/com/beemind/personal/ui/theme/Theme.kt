package com.beemind.personal.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val Colors = lightColorScheme(
    primary = Color(0xFF7040B6), onPrimary = Color.White,
    primaryContainer = Color(0xFFEFE5FC), onPrimaryContainer = Color(0xFF35135E),
    background = Color(0xFFFCFAFF), onBackground = Color(0xFF272031),
    surface = Color(0xFFFCFAFF), onSurface = Color(0xFF272031),
    onSurfaceVariant = Color(0xFF655B70), outline = Color(0xFF81738F),
)

@Composable
fun BeeMindTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = Colors, content = content)
    }
}
