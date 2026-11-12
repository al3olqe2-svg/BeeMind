package com.beemind.personal.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.foundation.border
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.beemind.personal.ui.theme.BeeDesign

@Composable
fun TemporalPicker(label: String, dueAt: Long?, enabled: Boolean, onSelect: (Long?) -> Unit, isDraft: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
    if (open) ReminderSheet(dueAt, { open = false }, { if (it != dueAt) onSelect(it); open = false }, label, isDraft)
}

/** No original note content is accepted by this component, including semantics. */
@Composable
fun CapsuleCover(unlockAt: Long, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xB3E2E4EB), Color(0xE68F9AAB), Color(0xD9455267))), BeeDesign.card)
        .border(.8.dp, Color(0xB3F4F0E4), BeeDesign.card).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(128.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width.coerceAtMost(180.dp.toPx())
                val left = (size.width - w) / 2
                drawOval(Brush.linearGradient(listOf(Color(0x20FFF8E6), Color(0xFFE9E3D6), Color(0xFF92A5BD))),
                    Offset(left, size.height * .22f), Size(w, size.height * .56f), style = Stroke(.8.dp.toPx()))
                drawCircle(Color(0x30FFF8E6), 27.dp.toPx())
                drawCircle(Color(0xFFF6EBD5), 3.dp.toPx(), Offset(left + w * .88f, size.height * .32f))
            }
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = BeeDesign.ink, modifier = Modifier.size(27.dp))
        }
        Text("كبسولة مغلقة", style = MaterialTheme.typography.labelLarge, color = BeeDesign.ink)
        Text("تُفتح في ${reminderTimestamp(unlockAt)}", style = MaterialTheme.typography.bodySmall, color = Color.White)
    }
}

@Composable
fun ResurfaceBadge() {
    Box(Modifier.background(Brush.horizontalGradient(listOf(Color(0xFF28394A), Color(0xFF3B2E53))), BeeDesign.button)
        .padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text("↗ عادت للواجهة", color = BeeDesign.darkForeground, style = MaterialTheme.typography.labelLarge)
    }
}
