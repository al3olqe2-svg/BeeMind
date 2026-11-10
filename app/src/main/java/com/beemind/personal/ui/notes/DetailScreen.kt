package com.beemind.personal.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.beemind.personal.R
import com.beemind.personal.ui.components.*
import com.beemind.personal.ui.theme.BeeDesign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: DetailViewModel, content: DetailContent, deletion: DeleteState,
    onBack: () -> Unit, onEdit: () -> Unit) {
    BackHandler(enabled = deletion.busy) { }
    LaunchedEffect(deletion.deleted) { if (deletion.deleted) onBack() }
    val note = (content as? DetailContent.Ready)?.note
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.note_details), style = MaterialTheme.typography.titleMedium) },
            navigationIcon = { BeeBack(onBack, !deletion.busy) }, actions = {
                if (note != null) {
                    IconButton(onClick = onEdit, enabled = !deletion.busy) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_note))
                    }
                    IconButton(onClick = { vm.confirmDelete(true) }, enabled = !deletion.busy) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_note), tint = MaterialTheme.colorScheme.error)
                    }
                }
            })
    }) { padding ->
        when (content) {
            DetailContent.Loading -> LinearProgressIndicator(Modifier.padding(padding).fillMaxWidth())
            DetailContent.Error -> Box(Modifier.padding(padding).padding(BeeDesign.page)) {
                ErrorPanel(stringResource(R.string.note_load_error), onRetry = vm::retry)
            }
            is DetailContent.Ready -> {
                if (note == null) Box(Modifier.padding(padding).padding(BeeDesign.page)) {
                    ErrorPanel(stringResource(R.string.note_missing))
                } else {
                    val chunks = remember(note.body) { readingChunks(note.body) }
                    SelectionContainer {
                        LazyColumn(Modifier.fillMaxSize().padding(padding),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            item(key = "dates") {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(stringResource(R.string.created_date, formatNoteTimestamp(note.createdAt)),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (note.updatedAt != note.createdAt) Text(
                                        stringResource(R.string.updated_date, formatNoteTimestamp(note.updatedAt)),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            note.title?.let { title -> item(key = "title") {
                                Text(title, style = MaterialTheme.typography.headlineMedium,
                                    modifier = Modifier.semantics { heading() })
                            } }
                            item(key = "divider") { HorizontalDivider(Modifier.padding(vertical = 8.dp), color = BeeDesign.border.copy(alpha = 0.55f)) }
                            itemsIndexed(chunks, key = { index, _ -> "body_$index" }) { _, chunk ->
                                Text(chunk, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }
    }
    if (deletion.confirming) AlertDialog(
        onDismissRequest = { vm.confirmDelete(false) },
        title = { Text(stringResource(R.string.delete_title)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.delete_body))
            if (deletion.error) Text(stringResource(R.string.delete_error), color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(onClick = vm::delete, enabled = !deletion.busy) {
            Text(stringResource(if (deletion.busy) R.string.deleting else R.string.delete_confirm), color = MaterialTheme.colorScheme.error)
        } },
        dismissButton = { TextButton(onClick = { vm.confirmDelete(false) }, enabled = !deletion.busy) { Text(stringResource(R.string.cancel)) } },
    )
}
