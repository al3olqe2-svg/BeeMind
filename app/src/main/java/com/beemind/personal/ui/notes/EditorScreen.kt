package com.beemind.personal.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.beemind.personal.R
import com.beemind.personal.ui.components.*
import com.beemind.personal.ui.theme.BeeDesign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel, state: EditorState, onBack: () -> Unit, onSaved: (String) -> Unit) {
    BackHandler { vm.requestExit() }
    LaunchedEffect(state.savedId) { state.savedId?.let(onSaved) }
    LaunchedEffect(state.exited) { if (state.exited) onBack() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, vm) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) vm.persistOnBackground() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val focus = LocalFocusManager.current
    var traitsOpen by remember { mutableStateOf(false) }
    val save = { focus.clearFocus(); vm.save() }
    if (state.lockedUntil != null) {
        Scaffold(topBar = { TopAppBar(title = { Text("كبسولة زمنية") }, navigationIcon = { BeeBack(vm::requestExit, !state.busy) }) }) { padding ->
            CapsuleCover(state.lockedUntil, Modifier.padding(padding).padding(24.dp))
        }
        return
    }
    Scaffold(modifier = Modifier.imePadding(), topBar = {
        TopAppBar(title = { Text("") },
            navigationIcon = { BeeBack(vm::requestExit, !state.busy) },
            actions = { Button(onClick = save, enabled = !state.loading && !state.loadFailed && !state.busy && state.canSave,
                shape = BeeDesign.button, modifier = Modifier.padding(end = 12.dp).testTag("save_note")) {
                Text(stringResource(if (state.busy) R.string.saving else R.string.save_note))
            } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = BeeDesign.page),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                state.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                state.loadFailed -> ErrorPanel(stringResource(if (state.error == EditorError.MISSING) R.string.note_missing else R.string.note_load_error), onRetry = vm::load)
                else -> {
                    state.error?.let { error ->
                        ErrorPanel(stringResource(when (error) {
                            EditorError.REMINDER_TIME -> R.string.reminder_time_error
                            EditorError.EMPTY -> R.string.empty_error
                            EditorError.SAVE -> R.string.save_error
                            EditorError.DRAFT -> R.string.draft_error
                            EditorError.MISSING -> R.string.note_missing
                        }))
                        TextButton(onClick = vm::clearError) { Text(stringResource(R.string.dismiss_error)) }
                    }
                    TextButton(onClick = { traitsOpen = true }, enabled = !state.busy) { Text("السمات") }
                    val bodyLabel = stringResource(R.string.body_hint)
                    BasicTextField(state = vm.body, enabled = !state.busy,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, textDirection = TextDirection.ContentOrRtl),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 16.dp).testTag("editor_body").semantics { contentDescription = bodyLabel },
                        decorator = { inner -> Box(Modifier.fillMaxSize().padding(top = 8.dp)) {
                            if (vm.body.text.isEmpty()) Text(bodyLabel, style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            inner()
                        } })
                }
            }
        }
    }
    if (traitsOpen) ModalBottomSheet(onDismissRequest = { traitsOpen = false }, containerColor = BeeDesign.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)
            .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { TraitPicker(state.expressiveTrait, !state.busy, vm::selectTrait) }
                        Box(Modifier.weight(1f)) { ReminderPicker(state.reminderEdit, state.savedReminder, !state.busy, vm::selectReminder) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val resurface = if (state.temporalEdit.resurfaceEdited) state.temporalEdit.resurfaceAt else state.savedResurfaceAt
                        Box(Modifier.weight(1f)) { TemporalPicker("↗ العودة", resurface, !state.busy, vm::selectResurface) }
                        Box(Modifier.weight(1f)) { TemporalPicker("◈ كبسولة زمنية", state.temporalEdit.sealUntil, !state.busy, vm::selectCapsule) }
                    }
                    val resurfaceDue = if (state.temporalEdit.resurfaceEdited) state.temporalEdit.resurfaceAt else state.savedResurfaceAt
                    resurfaceDue?.let { Text("العودة: ${reminderTimestamp(it)}", style = MaterialTheme.typography.bodySmall, color = BeeDesign.accent) }
                    state.temporalEdit.sealUntil?.let { Text("إغلاق حتى: ${reminderTimestamp(it)}", style = MaterialTheme.typography.bodySmall, color = BeeDesign.accent) }
                    val reminderDue = if (state.reminderEdit.edited) state.reminderEdit.dueAt else state.savedReminder?.takeIf { it.active }?.dueAt
                    reminderDue?.let { Text("◷ ${reminderTimestamp(it)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary) }
            if (!state.reminderEdit.edited) ReminderDetails(state.savedReminder, onEdit = { }, locked = true)
            TextButton(onClick = { traitsOpen = false }) { Text("تم") }
        }
    }
}
