package com.beemind.personal.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beemind.personal.R

/** Atmospheric Editorial: explicit light editorial and dark atmospheric roles. */
object BeeDesign {
    val page = 24.dp
    val gap = 12.dp
    val cardPadding = 18.dp
    val cardRadius = 14.dp
    val card = RoundedCornerShape(cardRadius)
    val panel = RoundedCornerShape(20.dp)
    val button = RoundedCornerShape(12.dp)
    const val pressMillis = 180
    val editorialBackground = Color(0xFFF2F3F5)
    val editorialForeground = Color(0xFF252830)
    val secondaryText = Color(0xFF535964)
    val mutedText = Color(0xFF626976)
    val editorialSurface = Color(0xFFFCFCFD)
    val editorialBorder = Color(0xFFD1D4DC)
    val darkBackground = Color(0xFF11151F)
    val darkSurface = Color(0xFF1D2330)
    val darkForeground = Color(0xFFF2F3F7)
    val darkSecondary = Color(0xFFCCD1DC)
    val darkBorder = Color(0xFF485263)
    val glassSurface = Color(0xD9212734)
    val glassBorder = Color(0x527B879D)
    val control = Color(0xFF654A83)
    val onControl = Color.White
    val darkAccent = Color(0xFFD9C8ED)
    val error = Color(0xFFAE2943)
    val disabled = Color(0xFF757B86)
    val disabledSurface = Color(0xFFE0E2E7)
    val atmosphericOverlay = Color(0x70202639)
    val scrim = Color(0x990D111B)
    // Compatibility aliases for existing editorial screens. Home uses explicit dark roles.
    val background = editorialBackground
    val surface = editorialSurface
    val border = editorialBorder
    val ink = editorialForeground
    val secondary = secondaryText
    val accent = control
    val touchTarget = 48.dp
    const val quickMillis = 120
    const val transitionMillis = 180
    val spectrum = listOf(Color(0xFFE98A87), Color(0xFFEDAE7E), Color(0xFFE8D78B),
        Color(0xFF8DD0AD), Color(0xFF89B9E8), Color(0xFFB39AE8))
    val glowColors = listOf(Color(0xFFFF719A), Color(0xFFFFC66D),
        Color(0xFFDCEB83), Color(0xFF65E5BF), Color(0xFF70BDFF), Color(0xFFC792FF), Color(0xFFFF719A))
    val glowEdge = Brush.sweepGradient(glowColors)
    val angerColors = listOf(Color(0xFFB82C4E), Color(0xFFFF6E79), Color(0xFFE43F51), Color(0xFF9C2846))
    val angerEdge = Brush.linearGradient(angerColors)
    val rainbow = Brush.linearGradient(spectrum)
    val atmosphere = Brush.verticalGradient(listOf(Color(0xFF303247), darkBackground))
    // Script-aware merged OFL fonts; see tools/build_fonts.py and licenses/fonts.
    val writing = FontFamily(Font(R.font.beemind_regular), Font(R.font.beemind_bold, FontWeight.Bold))
    val latin = writing
    private fun text(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
        fontFamily = writing, fontSize = size.sp, lineHeight = height.sp, fontWeight = weight,
        textDirection = TextDirection.ContentOrRtl)
    val typography = Typography(
        displayLarge = text(36, 48, FontWeight.Bold), displayMedium = text(30, 42, FontWeight.Bold),
        displaySmall = text(26, 36, FontWeight.Bold),
        headlineLarge = text(28, 40, FontWeight.Bold), headlineSmall = text(22, 34, FontWeight.Bold),
        headlineMedium = text(25, 38, FontWeight.Bold),
        titleLarge = text(22, 32, FontWeight.Bold),
        titleMedium = text(18, 28, FontWeight.Bold), titleSmall = text(16, 26, FontWeight.Bold),
        bodyLarge = text(19, 33), bodyMedium = text(16, 27), bodySmall = text(13, 22),
        labelLarge = text(15, 24, FontWeight.Bold), labelMedium = text(13, 20), labelSmall = text(12, 18),
    )
}
