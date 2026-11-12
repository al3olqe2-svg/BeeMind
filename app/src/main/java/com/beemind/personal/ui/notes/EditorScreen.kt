package com.beemind.personal.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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

@Composable
fun EditorScreen(vm: EditorViewModel, state: EditorState, onBack: () -> Unit, onSaved: (String) -> Unit) {
    var level by remember { mutableStateOf(ToolExpansionState.Closed) }
    val keyboard = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    fun navigateBack() {
        if (level != ToolExpansionState.Closed) level = level.back() else vm.requestExit()
    }
    BackHandler {
        when {
            level != ToolExpansionState.Closed -> level = level.back()
            imeVisible -> keyboard?.hide()
            else -> vm.requestExit()
        }
    }
    LaunchedEffect(state.savedId) { state.savedId?.let(onSaved) }
    LaunchedEffect(state.exited) { if (state.exited) onBack() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, vm) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) vm.persistOnBackground() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (state.lockedUntil != null) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            BeeBack(vm::requestExit, !state.busy)
            CapsuleCover(state.lockedUntil, Modifier.padding(24.dp))
        }
        return
    }
    val reminderDue = if (state.reminderEdit.edited) state.reminderEdit.dueAt else state.savedReminder?.takeIf { it.active }?.dueAt
    val resurfaceDue = if (state.temporalEdit.resurfaceEdited) state.temporalEdit.resurfaceAt else state.savedResurfaceAt
    val indicator = listOfNotNull(
        reminderDue?.let { "تذكير: ${formatNoteTimestamp(it)}" },
        resurfaceDue?.let { "عودة: ${formatNoteTimestamp(it)}" },
        state.temporalEdit.sealUntil?.let { "كبسولة بانتظار الإغلاق: ${formatNoteTimestamp(it)}" }).joinToString("، ")
    Box(Modifier.fillMaxSize().testTag("editor_surface")) {
        EditorAtmosphere(state.expressiveTrait, Modifier.matchParentSize())
        // One owner consumes IME/system bars; the writing viewport ends above the dock.
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            val toolMaxHeight = maxHeight * .45f
            Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                BeeBack(::navigateBack, !state.busy)
                if (state.busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.5.dp)
            }
            Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = BeeDesign.page)) {
                when {
                    state.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.loadFailed -> ErrorPanel(stringResource(if (state.error == EditorError.MISSING) R.string.note_missing else R.string.note_load_error), onRetry = vm::load)
                    else -> {
                        state.error?.let { error ->
                            Text(stringResource(when (error) {
                                EditorError.REMINDER_TIME -> R.string.reminder_time_error
                                EditorError.EMPTY -> R.string.empty_error
                                EditorError.SAVE -> R.string.save_error
                                EditorError.DRAFT -> R.string.draft_error
                                EditorError.MISSING -> R.string.note_missing
                            }), color = BeeDesign.error, style = MaterialTheme.typography.bodySmall)
                            Row {
                                TextButton(onClick = vm::requestExit) { Text("إعادة المحاولة والخروج") }
                                TextButton(onClick = vm::clearError) { Text(stringResource(R.string.dismiss_error)) }
                            }
                        }
                        EditorialWritingField(vm, !state.busy, Modifier.fillMaxSize())
                    }
                }
            }
            FloatingTraitTools(level, state.expressiveTrait, indicator,
                enabled = !state.loading && !state.loadFailed && !state.busy,
                onLevel = { level = it }, onTrait = vm::selectTrait,
                temporalContent = {
                    key(level) {
                        val due = when (level) {
                            ToolExpansionState.Reminder -> reminderDue
                            ToolExpansionState.Resurface -> resurfaceDue
                            else -> state.temporalEdit.sealUntil
                        }
                        TemporalToolOptions(level, due, state, onApply = {
                            when (level) {
                                ToolExpansionState.Reminder -> if (it != due) vm.selectReminder(it)
                                ToolExpansionState.Resurface -> if (it != due) vm.selectResurface(it)
                                ToolExpansionState.Capsule -> if (it != due) vm.selectCapsule(it)
                                else -> Unit
                            }
                            level = ToolExpansionState.Closed
                        }, onCancel = { level = ToolExpansionState.Primary }, maxHeight = toolMaxHeight)
                    }
                }, modifier = Modifier.align(Alignment.End).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 8.dp))
            }
        }
    }
}

/** The VM owns TextFieldState throughout tool/atmosphere changes, recreation and IO. */
@Composable
private fun EditorialWritingField(vm: EditorViewModel, enabled: Boolean, modifier: Modifier) {
    val bodyLabel = stringResource(R.string.body_hint)
    BasicTextField(state = vm.body, enabled = enabled,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = BeeDesign.editorialForeground, textDirection = TextDirection.ContentOrRtl),
        cursorBrush = SolidColor(BeeDesign.control),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier.padding(bottom = 8.dp).testTag("editor_body").semantics { contentDescription = bodyLabel },
        decorator = { inner -> Box(Modifier.fillMaxSize().padding(top = 8.dp)) {
            if (vm.body.text.isEmpty()) Text(bodyLabel, style = MaterialTheme.typography.bodyLarge, color = BeeDesign.mutedText)
            inner()
        } })
}
