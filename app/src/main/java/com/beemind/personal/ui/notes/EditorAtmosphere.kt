package com.beemind.personal.ui.notes

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.beemind.personal.domain.ExpressiveTrait
import com.beemind.personal.ui.theme.BeeDesign

/** Static, cached light fields. Text/selection are separate children; no per-keystroke effects. */
@Composable
fun EditorAtmosphere(trait: ExpressiveTrait, modifier: Modifier = Modifier) {
    Box(modifier.drawWithCache {
        val w = size.width
        val h = size.height
        fun light(color: Color, x: Float, y: Float, radius: Float) = Brush.radialGradient(
            listOf(color, color.copy(alpha = 0f)), Offset(w * x, h * y), w * radius)
        val lights = when (trait) {
            ExpressiveTrait.GLOW -> listOf(
                light(Color(0xE6F489C3), -.12f, .12f, 1.12f),
                light(Color(0xCCAC83EF), 1.05f, .28f, .94f),
                light(Color(0xB35EBEEB), -.1f, .60f, 1.05f),
                light(Color(0xCC6CDDC0), 1f, .88f, 1.1f),
                light(Color(0xCCF3BA81), .15f, 1.08f, .86f))
            ExpressiveTrait.ANGER -> listOf(
                light(Color(0xE699294D), -.12f, .12f, 1.05f),
                light(Color(0xD9CA3559), 1.12f, .42f, 1.0f),
                light(Color(0xB36C233F), -.35f, .67f, 1.1f),
                light(Color(0xD9F77775), .9f, 1.03f, 1.32f))
            ExpressiveTrait.NONE -> emptyList()
        }
        val readingVeil = Brush.horizontalGradient(listOf(
            BeeDesign.editorialBackground.copy(alpha = .08f),
            BeeDesign.editorialBackground.copy(alpha = .78f),
            BeeDesign.editorialBackground.copy(alpha = .78f),
            BeeDesign.editorialBackground.copy(alpha = .12f)), startX = 0f, endX = w)
        onDrawBehind {
            drawRect(BeeDesign.editorialBackground)
            lights.forEach { drawRect(it) }
            if (lights.isNotEmpty()) drawRect(readingVeil)
        }
    })
}
