package com.beemind.personal.ui.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.beemind.personal.domain.ExpressiveTrait
import com.beemind.personal.ui.theme.BeeDesign

@Composable
internal fun MicroTool(label: String, icon: ImageVector? = null, text: String? = null,
    selected: Boolean = false, brush: Brush? = null, tag: String = label,
    enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp).testTag(tag).semantics {
        contentDescription = label
        if (selected) stateDescription = "محدد"
        role = Role.Button
    }) {
        val glow = when (tag) {
            "expressive_GLOW" -> Color(0xB3B78AFF)
            "expressive_ANGER" -> Color(0xB3E43F51)
            else -> Color.Transparent
        }
        Box(Modifier.size(38.dp).drawWithCache {
            val aura = Brush.radialGradient(listOf(glow, Color.Transparent), Offset(size.width / 2, size.height / 2), size.minDimension * .68f)
            val reflection = Brush.radialGradient(listOf(Color.White.copy(alpha = .6f), Color.Transparent),
                Offset(size.width * .3f, size.height * .18f), size.width * .85f)
            onDrawWithContent {
                if (glow != Color.Transparent) drawCircle(aura, size.minDimension * .68f)
                drawContent()
                if (brush != null) drawCircle(reflection)
            }
        }.shadow(if (selected) 5.dp else 2.dp, CircleShape)
            .background(brush ?: Brush.linearGradient(listOf(Color(0xFAFFFFFF), Color(0xDDE3E6EC))), CircleShape)
            .border(if (selected) 1.5.dp else .8.dp, if (selected) BeeDesign.control else Color(0xCCB6BDC9), CircleShape),
            contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, null, tint = BeeDesign.ink, modifier = Modifier.size(19.dp))
            if (text != null) Text(text, color = BeeDesign.ink, style = MaterialTheme.typography.labelMedium.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr))
            if (selected && brush != null) Icon(Icons.Outlined.Check, null, tint = BeeDesign.ink, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
fun FloatingTraitTools(level: ToolExpansionState, trait: ExpressiveTrait, indicator: String,
    enabled: Boolean, onLevel: (ToolExpansionState) -> Unit, onTrait: (ExpressiveTrait) -> Unit,
    temporalContent: @Composable () -> Unit, modifier: Modifier = Modifier) {
    // This is a physical left-hand writing accessory even in RTL. Labels/dialogs stay RTL.
    Column(modifier, horizontalAlignment = androidx.compose.ui.AbsoluteAlignment.Left, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (level.temporal) temporalContent()
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                MicroTool(if (level == ToolExpansionState.Closed) "فتح أدوات الملاحظة" else "إغلاق الأدوات",
                    if (level == ToolExpansionState.Closed) Icons.Outlined.Add else Icons.Outlined.Close,
                    tag = "editor_tools", enabled = enabled,
                    onClick = { onLevel(if (level == ToolExpansionState.Closed) ToolExpansionState.Primary else ToolExpansionState.Closed) })
                if (level == ToolExpansionState.Closed && indicator.isNotEmpty()) {
                    Box(Modifier.size(6.dp).background(BeeDesign.control, CircleShape).semantics { contentDescription = indicator })
                }
                AnimatedVisibility(level == ToolExpansionState.Primary,
                    enter = fadeIn(tween(BeeDesign.quickMillis)), exit = fadeOut(tween(0))) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        MicroTool("السمة التعبيرية", EditorGlyphs.expressive, tag = "tool_expressive", enabled = enabled) { onLevel(ToolExpansionState.Expressive) }
                        MicroTool("التذكير", Icons.Outlined.Notifications, tag = "tool_reminder", enabled = enabled) { onLevel(ToolExpansionState.Reminder) }
                        MicroTool("العودة", EditorGlyphs.resurface, tag = "tool_resurface", enabled = enabled) { onLevel(ToolExpansionState.Resurface) }
                        MicroTool("الكبسولة الزمنية", Icons.Outlined.Lock, tag = "tool_capsule", enabled = enabled) { onLevel(ToolExpansionState.Capsule) }
                    }
                }
                if (level != ToolExpansionState.Closed && level != ToolExpansionState.Primary) {
                    MicroTool("العودة إلى الأدوات", Icons.AutoMirrored.Outlined.ArrowBack, tag = "tools_back", enabled = enabled) { onLevel(ToolExpansionState.Primary) }
                }
                if (level == ToolExpansionState.Expressive) {
                    ExpressiveTrait.entries.forEach { value ->
                        val label = when(value) { ExpressiveTrait.NONE -> "عادي"; ExpressiveTrait.GLOW -> "الوهج"; ExpressiveTrait.ANGER -> "الغضب" }
                        val colors = when(value) {
                            ExpressiveTrait.NONE -> listOf(Color(0xFFD9DDE3), Color(0xFFF9FAFC))
                            ExpressiveTrait.GLOW -> BeeDesign.glowColors
                            ExpressiveTrait.ANGER -> BeeDesign.angerColors
                        }
                        MicroTool(label, selected = trait == value, brush = if (value == ExpressiveTrait.NONE) Brush.linearGradient(colors) else Brush.sweepGradient(colors),
                            tag = "expressive_${value.name}", enabled = enabled) { onTrait(value) }
                    }
                }
            }
        }
    }
}
