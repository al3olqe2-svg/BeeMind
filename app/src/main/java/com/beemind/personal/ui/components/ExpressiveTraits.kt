package com.beemind.personal.ui.components

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.beemind.personal.R
import com.beemind.personal.domain.ExpressiveTrait
import com.beemind.personal.ui.theme.BeeDesign

fun ExpressiveTrait.labelResource(): Int = when (this) {
    ExpressiveTrait.NONE -> R.string.trait_none
    ExpressiveTrait.GLOW -> R.string.trait_glow
    ExpressiveTrait.ANGER -> R.string.trait_anger
}

fun ExpressiveTrait.edgeBrush(): Brush = when (this) {
    ExpressiveTrait.NONE -> Brush.linearGradient(listOf(BeeDesign.border, BeeDesign.border))
    ExpressiveTrait.GLOW -> BeeDesign.glowEdge
    ExpressiveTrait.ANGER -> BeeDesign.angerEdge
}

/** Static cached strokes: no blur layers, offscreen textures or perpetual animation.
 * The 5dp halo fits within half the grid's 12dp gap. Apply outside the clipped Card.
 * Drawing changes neither the measured bounds nor masonry placement.
 */
fun Modifier.expressiveHalo(trait: ExpressiveTrait): Modifier = if (trait == ExpressiveTrait.NONE) this else drawWithCache {
    val brush = trait.edgeBrush()
    val radius = CornerRadius(20.dp.toPx())
    onDrawBehind {
        drawRoundRect(brush, cornerRadius = radius, style = Stroke(10.dp.toPx()), alpha = 0.055f)
        drawRoundRect(brush, cornerRadius = radius, style = Stroke(7.dp.toPx()), alpha = 0.09f)
        drawRoundRect(brush, cornerRadius = radius, style = Stroke(4.dp.toPx()), alpha = 0.18f)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TraitPicker(selected: ExpressiveTrait, enabled: Boolean, onSelect: (ExpressiveTrait) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.trait_current, stringResource(selected.labelResource())))
    }
    if (open) ModalBottomSheet(onDismissRequest = { open = false }, containerColor = BeeDesign.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = BeeDesign.page).padding(bottom = 24.dp)
            .verticalScroll(rememberScrollState()).selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.trait_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.trait_save_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            ExpressiveTrait.entries.forEach { trait ->
                Row(Modifier.fillMaxWidth().selectable(selected = selected == trait, role = Role.RadioButton,
                    onClick = { onSelect(trait); open = false }).padding(vertical = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).expressiveHalo(trait).background(trait.edgeBrush(), BeeDesign.card)
                        .padding(2.dp).background(BeeDesign.surface, BeeDesign.card))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(trait.labelResource()), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(when (trait) {
                            ExpressiveTrait.NONE -> R.string.trait_none_description
                            ExpressiveTrait.GLOW -> R.string.trait_glow_description
                            ExpressiveTrait.ANGER -> R.string.trait_anger_description
                        }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RadioButton(selected = selected == trait, onClick = null)
                }
            }
        }
    }
}
