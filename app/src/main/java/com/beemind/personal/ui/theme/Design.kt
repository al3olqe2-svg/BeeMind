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

/** Stage 1.5: quiet cosmic surfaces, with Pride for identity, touch, and explicitly selected expressive traits. */
object BeeDesign {
    val page = 24.dp
    val gap = 12.dp
    val cardPadding = 18.dp
    val card = RoundedCornerShape(20.dp)
    val panel = RoundedCornerShape(24.dp)
    val button = RoundedCornerShape(18.dp)
    const val pressMillis = 180
    val background = Color(0xFF0B0E17)
    val surface = Color(0xFF181D29)
    val border = Color(0xFF303849)
    val ink = Color(0xFFF1F3F8)
    val secondary = Color(0xFFB0B8C9)
    val accent = Color(0xFFC3B8F5)
    val spectrum = listOf(Color(0xFFE98A87), Color(0xFFEDAE7E), Color(0xFFE8D78B),
        Color(0xFF8DD0AD), Color(0xFF89B9E8), Color(0xFFB39AE8))
    val glowEdge = Brush.sweepGradient(listOf(Color(0xFFFF719A), Color(0xFFFFC66D),
        Color(0xFFDCEB83), Color(0xFF65E5BF), Color(0xFF70BDFF), Color(0xFFC792FF), Color(0xFFFF719A)))
    val angerEdge = Brush.linearGradient(listOf(Color(0xFFB82C4E), Color(0xFFFF6E79), Color(0xFFE43F51), Color(0xFF9C2846)))
    val rainbow = Brush.linearGradient(spectrum)
    val atmosphere = Brush.verticalGradient(listOf(Color(0xFF131827), background, background))
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
