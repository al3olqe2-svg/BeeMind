package com.beemind.personal.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beemind.personal.domain.ExpressiveTrait
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.beemind.personal.R
import com.beemind.personal.ui.components.*
import com.beemind.personal.ui.theme.BeeDesign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: DetailViewModel, content: DetailContent, deletion: DeleteState,
    onBack: () -> Unit, onEdit: () -> Unit, onOpenLinked: (String) -> Unit) {
    BackHandler { if (!deletion.busy) onBack() }
    LaunchedEffect(deletion.deleted) { if (deletion.deleted) onBack() }
    val readingState = rememberLazyListState()
    var moreOpen by remember { mutableStateOf(false) }
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
    Box(Modifier.fillMaxSize().testTag("reading_surface")) {
    EditorAtmosphere(if (note?.locked == false) note.expressiveTrait else ExpressiveTrait.NONE, Modifier.matchParentSize())
    Scaffold(containerColor = Color.Transparent, topBar = {
        Row(Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 8.dp)
            .testTag("reading_actions"), verticalAlignment = Alignment.CenterVertically) {
            MicroTool("رجوع", Icons.AutoMirrored.Outlined.ArrowBack, enabled = !deletion.busy, onClick = onBack)
            Spacer(Modifier.weight(1f))
            if (note != null) {
                if (!note.locked) MicroTool(stringResource(R.string.edit_note), Icons.Default.Edit,
                    enabled = !deletion.busy, onClick = onEdit)
                MicroTool("الخيوط", EditorGlyphs.link, tag = "manage_threads", enabled = !deletion.busy,
                    onClick = { threadsOpen = true })
                Box {
                    MicroTool("إجراءات إضافية", EditorGlyphs.more, tag = "reading_more", enabled = !deletion.busy,
                        onClick = { moreOpen = true })
                    DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete_note)) },
                            onClick = { moreOpen = false; vm.confirmDelete(true) },
                            leadingIcon = { Icon(Icons.Default.Delete, null) })
                    }
                }
            }
        }
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
                            Surface(onClick = { lockedTraitsOpen = true }, color = Color.Transparent, shape = BeeDesign.card,
                                modifier = Modifier.semantics { contentDescription = "إدارة مواعيد الكبسولة" }) {
                                Column {
                                    CapsuleCover(checkNotNull(note.unlockAt))
                                    Text("المواعيد", color = BeeDesign.accent, modifier = Modifier.padding(16.dp))
                                }
                            }
                        }

                    }
                } else {
                    ReadingArticle(note.body, vm, readingState, temporalError, !deletion.busy, onOpenLinked,
                        Modifier.fillMaxSize().padding(padding))
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
    if (threadsOpen) Dialog(onDismissRequest = { threadsOpen = false }) {
        Surface(shape = BeeDesign.panel, color = BeeDesign.editorialBackground) {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { threadsOpen = false }) { Text("إغلاق الخيوط") }
                }
                ThreadsSection(vm, { threadsOpen = false; onOpenLinked(it) }, !deletion.busy)
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

/** Links and body share one lazy scroll. Only safe bounded summaries enter the footer. */
@Composable
private fun ReadingArticle(body: String, vm: DetailViewModel, readingState: LazyListState, temporalError: Boolean,
    enabled: Boolean, onOpen: (String) -> Unit, modifier: Modifier) {
    val chunks = remember(body) { readingChunks(body) }
    val linked by vm.linkedNotes.collectAsStateWithLifecycle()
    val notes = (linked as? ThreadContent.Ready)?.notes.orEmpty()
    // A returning entry may be anchored near its footer. Do not clamp that saved anchor
    // against a temporarily shorter list while Room refreshes safe link projections.
    val hasReadingPosition by remember(readingState) { derivedStateOf {
        readingState.firstVisibleItemIndex > 0 || readingState.firstVisibleItemScrollOffset > 0
    } }
    if (linked is ThreadContent.Loading && hasReadingPosition) {
        Box(modifier) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        return
    }
    SelectionContainer {
        LazyColumn(modifier.testTag("reading_list"), state = readingState, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp)) {
            if (temporalError) item(key = "return_error") {
                DisableSelection { TextButton(onClick = vm::readableOpened) { Text("تعذر حفظ حالة العودة؛ إعادة المحاولة") } }
            }
            itemsIndexed(chunks, key = { index, _ -> "body_$index" }) { index, chunk ->
                Text(chunk, color = BeeDesign.ink, style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().testTag("reading_chunk_$index"))
            }
            if (notes.isNotEmpty()) {
                item(key = "related_heading") {
                    DisableSelection {
                        Column(Modifier.fillMaxWidth().padding(top = 36.dp, bottom = 16.dp).testTag("inline_threads")) {
                            HorizontalDivider(color = BeeDesign.editorialBorder.copy(alpha = .65f))
                            Text("أفكار مرتبطة", color = BeeDesign.secondary, style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(top = 16.dp))
                        }
                    }
                }
                items(notes, key = { "linked_${it.note.id}" }, contentType = { "linked" }) { linkedNote ->
                    DisableSelection {
                        InlineThreadCard(linkedNote.note, enabled, { onOpen(linkedNote.note.id) },
                            Modifier.padding(bottom = 10.dp))
                    }
                }
            }
            if (linked is ThreadContent.Error) item(key = "links_error") {
                DisableSelection { TextButton(onClick = vm::retryThreads) { Text("تعذر تحميل الأفكار المرتبطة؛ إعادة المحاولة") } }
            }
        }
    }
}
