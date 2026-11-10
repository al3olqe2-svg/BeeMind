package com.beemind.personal.ui.notes

import androidx.compose.foundation.background
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
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (state is HomeState.Ready && state.notes.isNotEmpty()) {
                ExtendedFloatingActionButton(onClick = onCreate, containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.testTag("new_note"),
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.new_note)) })
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
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(5.dp).background(BeeDesign.rainbow, BeeDesign.card))
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
                            Text(stringResource(R.string.note_count, state.notes.size), style = MaterialTheme.typography.labelLarge,
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
    val color = BeeDesign.cardColors[Math.floorMod(note.id.hashCode(), BeeDesign.cardColors.size)]
    Card(onClick = onClick, shape = BeeDesign.card, colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp).testTag("note_${note.id}")
            .semantics { traversalIndex = index.toFloat() + 1 }) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!note.title.isNullOrBlank()) {
                Text(note.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (note.preview.isNotBlank()) {
                Text(note.preview, style = MaterialTheme.typography.bodyMedium, maxLines = 8,
                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(formatNoteDate(note.createdAt), style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
