package com.beemind.personal.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.beemind.personal.R
import com.beemind.personal.ui.components.*
import com.beemind.personal.ui.theme.BeeDesign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: DetailViewModel, content: DetailContent, deletion: DeleteState,
    onBack: () -> Unit, onEdit: () -> Unit, onOpenLinked: (String) -> Unit) {
    BackHandler(enabled = deletion.busy) { }
    LaunchedEffect(deletion.deleted) { if (deletion.deleted) onBack() }
    var lockedTraitsOpen by remember { mutableStateOf(false) }
    var threadsOpen by remember { mutableStateOf(false) }
    val temporalError by vm.temporalError.collectAsState()
    val note = (content as? DetailContent.Ready)?.note
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, note?.id, note?.locked) {
        fun opened() { if (note != null && !note.locked) vm.readableOpened() }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) opened()
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) opened()
        onDispose { lifecycle.removeObserver(observer) }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("") },
            actions = {
                if (note != null) {
                    IconButton(onClick = onEdit, enabled = !deletion.busy && !note.locked) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_note))
                    }
                    IconButton(onClick = { threadsOpen = true }, enabled = !deletion.busy, modifier = Modifier.testTag("manage_threads")) {
                        Icon(Icons.Default.Share, "الخيوط")
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
                } else if (note.locked) {
                    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item {
                            Surface(onClick = { lockedTraitsOpen = true }, color = BeeDesign.surface,
                                modifier = Modifier.semantics { contentDescription = "إدارة مواعيد الكبسولة" }) {
                                Column {
                                    CapsuleCover(checkNotNull(note.unlockAt))
                                    Text("المواعيد", color = BeeDesign.accent, modifier = Modifier.padding(16.dp))
                                }
                            }
                        }

                    }
                } else {
                    val chunks = remember(note.body) { readingChunks(note.body) }
                    SelectionContainer {
                        LazyColumn(Modifier.fillMaxSize().padding(padding),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (temporalError) item(key = "return_error") {
                                TextButton(onClick = vm::readableOpened) { Text("تعذر حفظ حالة العودة؛ إعادة المحاولة") }
                            }
                            itemsIndexed(chunks, key = { index, _ -> "body_$index" }) { _, chunk ->
                                Text(chunk, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }
    }
    if (lockedTraitsOpen && note?.locked == true) ModalBottomSheet(onDismissRequest = { lockedTraitsOpen = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = BeeDesign.surface) {
        Column(Modifier.padding(20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TemporalPicker("↗ العودة", note.resurfaceAt, !deletion.busy, vm::scheduleResurface, isDraft = false)
            ReminderDetails(note.reminder, onEdit, locked = true)
            if (temporalError) Text("تعذر حفظ الموعد؛ حاول مجددًا.", color = MaterialTheme.colorScheme.error)
        }
    }
    if (threadsOpen) ModalBottomSheet(onDismissRequest = { threadsOpen = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = BeeDesign.surface) {
        Box(Modifier.padding(20.dp).padding(bottom = 24.dp)) { ThreadsSection(vm, onOpenLinked, !deletion.busy) }
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
