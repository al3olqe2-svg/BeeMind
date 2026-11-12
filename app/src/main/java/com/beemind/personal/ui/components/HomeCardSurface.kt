package com.beemind.personal.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.beemind.personal.domain.ExpressiveTrait
import com.beemind.personal.ui.theme.BeeDesign
import com.beemind.personal.ui.theme.HomeMaterials as Material

/** Cached static material. All brushes and strokes are built at size changes, never per frame.
 * Aura extends at most 4dp into a 12dp gutter. No blur/bitmap/offscreen layer. */
fun Modifier.homeCardMaterial(trait: ExpressiveTrait, locked: Boolean) = drawWithCache {
    val radius = CornerRadius(BeeDesign.cardRadius.toPx())
    val colors = when {
        locked -> Material.capsule
        trait == ExpressiveTrait.GLOW -> Material.glow
        trait == ExpressiveTrait.ANGER -> Material.anger
        else -> Material.smoke
    }
    val surface = Brush.linearGradient(colors, Offset.Zero, Offset(size.width, size.height))
    val edge = when {
        locked -> Brush.linearGradient(listOf(Material.pearl.copy(alpha = .65f), Material.edge, Material.pearl.copy(alpha = .12f)))
        trait == ExpressiveTrait.GLOW -> BeeDesign.glowEdge
        trait == ExpressiveTrait.ANGER -> BeeDesign.angerEdge
        else -> Brush.linearGradient(listOf(Material.edge, Material.edge.copy(alpha = .12f), Material.edge))
    }
    val glow = trait == ExpressiveTrait.GLOW && !locked
    val anger = trait == ExpressiveTrait.ANGER && !locked
    val light = when { glow -> Color(0xFF87E6D5); anger -> Material.crimson; locked -> Material.pearl; else -> Material.reflection }
    val illumination = Brush.radialGradient(listOf(light.copy(alpha = if (glow || anger) .30f else .09f), Color.Transparent),
        Offset(size.width * (if (anger) .98f else .12f), size.height * (if (anger) .9f else .12f)), size.maxDimension.coerceAtLeast(1f) * .85f)
    val shade = Brush.verticalGradient(listOf(Color(0x38202736), Color(0x80101520), Color.Transparent))
    val sheen = Brush.linearGradient(listOf(Color.White.copy(alpha = .12f), Color.Transparent, light.copy(alpha = .06f)))
    val auraOuter = Stroke(8.dp.toPx())
    val auraMiddle = Stroke(5.dp.toPx())
    val auraInner = Stroke(2.dp.toPx())
    val hairline = Stroke(.8.dp.toPx())
    val inset = .5.dp.toPx()
    onDrawBehind {
        if (size.minDimension <= 0f) return@onDrawBehind
        drawRoundRect(Color.Black, cornerRadius = radius, style = auraMiddle, alpha = .08f)
        if (glow || anger) {
            drawRoundRect(edge, cornerRadius = radius, style = auraOuter, alpha = .045f)
            drawRoundRect(edge, cornerRadius = radius, style = auraMiddle, alpha = .09f)
            drawRoundRect(edge, cornerRadius = radius, style = auraInner, alpha = .23f)
        }
        drawRoundRect(surface, cornerRadius = radius)
        drawRoundRect(illumination, cornerRadius = radius)
        drawRoundRect(shade, cornerRadius = radius)
        drawRoundRect(sheen, cornerRadius = radius)
        drawRoundRect(edge, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), radius, style = hairline,
            alpha = if (glow || anger) .95f else .7f)
    }
}
