package com.beemind.personal.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.beemind.personal.ui.theme.BeeDesign
import com.beemind.personal.ui.theme.HomeMaterials

/** Deliberately timestamp-only, independent of the detail screen's CapsuleCover. */
@Composable
fun HomeCapsuleCover(unlockAt: Long) {
    Column(Modifier.fillMaxWidth().testTag("home_capsule_cover"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().height(62.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val pearl = HomeMaterials.pearl
                // Asymmetric mineral reflection; no imitation text or hidden-content transition.
                drawOval(Brush.linearGradient(listOf(pearl.copy(alpha = .02f), pearl.copy(alpha = .48f), pearl.copy(alpha = .02f))),
                    Offset(size.width * .08f, size.height * .22f), Size(size.width * .84f, size.height * .52f), style = Stroke(.7.dp.toPx()))
                drawCircle(Brush.radialGradient(listOf(pearl.copy(alpha = .13f), androidx.compose.ui.graphics.Color.Transparent)), 29.dp.toPx())
            }
            Icon(Icons.Outlined.Lock, null, Modifier.size(25.dp), tint = HomeMaterials.pearl)
        }
        Text("كبسولة مغلقة", color = BeeDesign.darkForeground, style = MaterialTheme.typography.labelLarge)
        Text("تُفتح في ${reminderTimestamp(unlockAt)}", color = BeeDesign.darkSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun HomeResurfaceIndicator(onLight: Boolean = false) {
    Text("↗ عادت للواجهة", color = if (onLight) androidx.compose.ui.graphics.Color(0xFF345E53) else HomeMaterials.returned, style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.testTag("home_resurfaced").background(HomeMaterials.returned.copy(alpha = .08f), BeeDesign.button)
            .padding(horizontal = 7.dp, vertical = 3.dp))
}
