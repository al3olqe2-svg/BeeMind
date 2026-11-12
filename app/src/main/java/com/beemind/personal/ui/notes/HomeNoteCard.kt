package com.beemind.personal.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.beemind.personal.domain.ExpressiveTrait
import com.beemind.personal.domain.NoteSummary
import com.beemind.personal.ui.components.*
import com.beemind.personal.ui.theme.BeeDesign
import com.beemind.personal.ui.theme.HomeMaterials
import androidx.compose.ui.draw.clip

@Composable
internal fun NoteCard(note: NoteSummary, index: Int, onClick: () -> Unit) {
    val size = homeCardSize(note.id)
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val traitLabel = if (note.locked || note.expressiveTrait == ExpressiveTrait.NONE) "" else stringResource(note.expressiveTrait.labelResource())
    // Minimum rather than fixed height: capsule labels and scaled fonts may grow safely.
    Box(Modifier.fillMaxWidth().heightIn(min = (size.heightDp * scale).dp)
        .homeCardMaterial(note.expressiveTrait, note.locked)
        .clip(BeeDesign.card).clickable(onClick = onClick)
        .testTag("note_${note.id}").semantics(mergeDescendants = true) {
            traversalIndex = index.toFloat() + 1
            stateDescription = traitLabel + if (note.locked) "" else (note.reminderDueAt?.let { "، تذكير: ${reminderTimestamp(it)}" } ?: "")
        }) {
        Column(Modifier.fillMaxWidth().padding(HomeMaterials.padding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (note.resurfaced) HomeResurfaceIndicator()
            // Cover only accepts an unlock timestamp; no title/preview can cross this boundary.
            if (note.locked) HomeCapsuleCover(checkNotNull(note.unlockAt))
            else if (note.preview.isNotBlank()) Text(note.preview, color = BeeDesign.darkForeground,
                style = MaterialTheme.typography.bodyMedium, maxLines = (size.previewLines - if (note.resurfaced) 1 else 0).coerceAtLeast(2), overflow = TextOverflow.Ellipsis)
        }
    }
}
