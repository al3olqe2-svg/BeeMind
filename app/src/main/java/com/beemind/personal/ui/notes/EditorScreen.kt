package com.beemind.personal.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
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
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) vm.persistOnBackground() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val focus = LocalFocusManager.current
    val save = { focus.clearFocus(); vm.save() }
    Scaffold(modifier = Modifier.imePadding(), topBar = {
        TopAppBar(title = { Text(stringResource(R.string.editor_title), style = MaterialTheme.typography.titleMedium) },
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
                    Text(stringResource(when {
                        state.draftStored -> R.string.draft_stored
                        state.dirty -> R.string.draft_pending
                        else -> R.string.editor_hint
                    }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.error?.let { error ->
                        ErrorPanel(stringResource(when (error) {
                            EditorError.EMPTY -> R.string.empty_error
                            EditorError.SAVE -> R.string.save_error
                            EditorError.DRAFT -> R.string.draft_error
                            EditorError.MISSING -> R.string.note_missing
                        }))
                        TextButton(onClick = vm::clearError) { Text(stringResource(R.string.dismiss_error)) }
                    }
                    TraitPicker(state.expressiveTrait, !state.busy, vm::selectTrait)
                    val titleLabel = stringResource(R.string.title_hint)
                    BasicTextField(state = vm.title, enabled = !state.busy,
                        textStyle = MaterialTheme.typography.headlineMedium.copy(color = MaterialTheme.colorScheme.onSurface, textDirection = TextDirection.ContentOrRtl),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 3),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("editor_title").semantics { contentDescription = titleLabel },
                        decorator = { inner -> Box(Modifier.padding(vertical = 8.dp)) {
                            if (vm.title.text.isEmpty()) Text(titleLabel, style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            inner()
                        } })
                    HorizontalDivider(color = BeeDesign.border.copy(alpha = 0.55f))
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
    if (state.confirmExit) AlertDialog(onDismissRequest = vm::stay,
        title = { Text(stringResource(R.string.unsaved_title)) },
        text = { Text(stringResource(R.string.unsaved_body)) },
        confirmButton = { TextButton(onClick = save, enabled = state.canSave && !state.busy) { Text(stringResource(R.string.save_and_leave)) } },
        dismissButton = { Column {
            TextButton(onClick = vm::stay) { Text(stringResource(R.string.keep_writing)) }
            TextButton(onClick = vm::discardAndExit, enabled = !state.busy) { Text(stringResource(R.string.discard_changes), color = MaterialTheme.colorScheme.error) }
        } })
}
