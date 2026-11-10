package com.beemind.personal.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.beemind.personal.R
import com.beemind.personal.domain.ExpressiveTrait
import com.beemind.personal.domain.DraftSummary
import com.beemind.personal.domain.NoteSummary
import com.beemind.personal.ui.components.*
import com.beemind.personal.ui.theme.BeeDesign

@Composable
fun HomeScreen(state: HomeState, onCreate: () -> Unit, onOpen: (String) -> Unit,
    onResume: (DraftSummary) -> Unit, onRetry: () -> Unit) {
    val loadingLabel = stringResource(R.string.loading_notes)
    val density = LocalDensity.current
    val widthPx = LocalWindowInfo.current.containerSize.width
    val columns = masonryColumns((widthPx / density.density).toInt(), density.fontScale)
    Scaffold(
        modifier = Modifier.background(BeeDesign.atmosphere),
        containerColor = Color.Transparent,
        floatingActionButton = {
            if (state is HomeState.Ready && state.notes.isNotEmpty()) {
                IridescentAction(onClick = onCreate, modifier = Modifier.testTag("new_note")) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.new_note))
                }
            }
        },
    ) { padding ->
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize().padding(padding).testTag("notes_grid").semantics { isTraversalGroup = true },
            contentPadding = PaddingValues(start = BeeDesign.page, end = BeeDesign.page, top = 24.dp, bottom = 112.dp),
            horizontalArrangement = Arrangement.spacedBy(BeeDesign.gap),
            verticalItemSpacing = BeeDesign.gap,
        ) {
            item(span = StaggeredGridItemSpan.FullLine, key = "header") {
                Column(Modifier.padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge.copy(fontFamily = BeeDesign.latin),
                        color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.padding(top = 12.dp).width(40.dp).height(2.dp).background(BeeDesign.rainbow, BeeDesign.card))
                }
            }
            when (state) {
                HomeState.Loading -> item(span = StaggeredGridItemSpan.FullLine) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = loadingLabel })
                }
                HomeState.Error -> item(span = StaggeredGridItemSpan.FullLine) {
                    ErrorPanel(stringResource(R.string.notes_error), onRetry = onRetry)
                }
                is HomeState.Ready -> {
                    state.draft?.let { draft ->
                        item(span = StaggeredGridItemSpan.FullLine, key = "draft") {
                            OutlinedButton(onClick = { onResume(draft) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                                Text(stringResource(R.string.resume_draft))
                            }
                        }
                    }
                    if (state.notes.isEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine, key = "empty") { EmptyNotes(onCreate) }
                    } else {
                        item(span = StaggeredGridItemSpan.FullLine, key = "order") {
                            Text(if (state.notes.any { it.resurfaced }) "${state.notes.size} ملاحظة • العائدة أولًا، ثم الأحدث إنشاءً" else stringResource(R.string.note_count, state.notes.size), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                        }
                        itemsIndexed(state.notes, key = { _, note -> note.id }, contentType = { _, _ -> "note" }) { index, note ->
                            NoteCard(note, index, onClick = { onOpen(note.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteCard(note: NoteSummary, index: Int, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val illumination by animateFloatAsState(if (pressed) 1f else 0f,
        animationSpec = tween(BeeDesign.pressMillis), label = "card touch")
    val edge = if (illumination > 0f) Brush.linearGradient(BeeDesign.spectrum.map {
        androidx.compose.ui.graphics.lerp(BeeDesign.border, it, illumination * 0.75f)
    }) else Brush.linearGradient(listOf(BeeDesign.border, BeeDesign.border))
    val traitLabel = stringResource(note.expressiveTrait.labelResource())
    Box(Modifier.fillMaxWidth().expressiveHalo(note.expressiveTrait)) {
        Card(onClick = onClick, interactionSource = interaction, border = BorderStroke(if (note.expressiveTrait == ExpressiveTrait.NONE) 1.dp else 2.dp,
            if (note.expressiveTrait == ExpressiveTrait.NONE) edge else note.expressiveTrait.edgeBrush()), shape = BeeDesign.card, colors = CardDefaults.cardColors(containerColor = BeeDesign.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp).testTag("note_${note.id}")
                .semantics { traversalIndex = index.toFloat() + 1; stateDescription = traitLabel + (note.reminderDueAt?.let { "، تذكير: ${reminderTimestamp(it)}" } ?: "") }) {
            if (note.locked) {
                if (note.resurfaced) Box(Modifier.padding(12.dp)) { ResurfaceBadge() }
                CapsuleCover(checkNotNull(note.unlockAt))
            } else Column(Modifier.padding(BeeDesign.cardPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (note.resurfaced) ResurfaceBadge()
                if (!note.title.isNullOrBlank()) {
                    Text(note.title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                if (note.preview.isNotBlank()) {
                    Text(note.preview, style = MaterialTheme.typography.bodyMedium, maxLines = 8,
                        overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text((if (note.reminderDueAt != null) "◷  " else "") + formatNoteDate(note.createdAt), style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
