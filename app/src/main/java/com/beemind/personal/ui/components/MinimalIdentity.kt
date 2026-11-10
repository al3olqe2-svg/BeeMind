package com.beemind.personal.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.beemind.personal.ui.theme.BeeDesign

@Composable
fun RainbowLine() {
    Canvas(Modifier.fillMaxWidth().height(16.dp).testTag("rainbow_line")) {
        val y = size.height / 2
        listOf(14f to .035f, 9f to .075f, 5f to .16f, 2f to 1f).forEach { (width, alpha) ->
            drawLine(BeeDesign.rainbow, Offset(0f, y), Offset(size.width, y), width.dp.toPx(), alpha = alpha,
                cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

@Composable
fun NeonAdd(onClick: () -> Unit) {
    Box(Modifier.padding(8.dp).size(64.dp).drawWithCache {
        val radius = size.minDimension * .85f
        val center = Offset(size.width / 2f, size.height / 2f)
        val halo = if (radius > 0f) Brush.radialGradient(
            listOf(Color(0xFFB475FF).copy(alpha = .45f), Color.Transparent), center, radius) else null
        onDrawBehind { if (halo != null) drawCircle(halo, radius = radius, center = center) }
    }) {
        FloatingActionButton(onClick = onClick, shape = CircleShape,
            containerColor = Color(0xFF9664EA), contentColor = BeeDesign.ink,
            modifier = Modifier.fillMaxSize().testTag("new_note")) {
            Icon(Icons.Default.Add, "إضافة ملاحظة", Modifier.size(30.dp))
        }
    }
}
