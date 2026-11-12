package com.beemind.personal.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import com.beemind.personal.ui.theme.HomeMaterials
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.beemind.personal.ui.theme.BeeDesign

@Composable
fun RainbowLine() {
    Canvas(Modifier.fillMaxWidth().height(16.dp).testTag("rainbow_line")) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val y = size.height / 2
        listOf(10f to .035f, 6f to .09f, 3f to .22f, 1.5f to 1f).forEach { (width, alpha) ->
            drawLine(BeeDesign.rainbow, Offset(0f, y), Offset(size.width, y), width.dp.toPx(), alpha = alpha,
                cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

/** 42dp visual disc, 52dp accessible touch area. No animated aura. */
@Composable
fun MiniGlassAdd(enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.size(52.dp).testTag(if (enabled) "new_note" else "new_note_loading")) {
        Box(Modifier.size(42.dp).shadow(3.dp, CircleShape)
            .background(Brush.linearGradient(listOf(BeeDesign.darkBorder, HomeMaterials.control, HomeMaterials.control)), CircleShape)
            .border(.8.dp, HomeMaterials.edge, CircleShape), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Icon(Icons.Default.Add, "إضافة ملاحظة", Modifier.size(23.dp),
                tint = if (enabled) BeeDesign.darkForeground else BeeDesign.disabled)
        }
    }
}
