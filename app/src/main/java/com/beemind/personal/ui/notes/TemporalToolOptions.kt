package com.beemind.personal.ui.notes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.beemind.personal.BeeMindApplication
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.beemind.personal.ui.components.ReminderPermissions
import com.beemind.personal.ui.components.ReminderDetails
import com.beemind.personal.ui.theme.BeeDesign
import java.time.*

/** Scoped by category at the caller: cancellation never changes domain metadata. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemporalToolOptions(level: ToolExpansionState, initial: Long?, state: EditorState,
    onApply: (Long?) -> Unit, onCancel: () -> Unit, maxHeight: androidx.compose.ui.unit.Dp = 280.dp) {
    var draft by remember(initial) { mutableStateOf(AppointmentDraft(initial)) }
    var dialog by remember { mutableStateOf<String?>(null) }
    val zone = ZoneId.systemDefault()
    val local = draft.localValue ?: (draft.due?.let(Instant::ofEpochMilli) ?: Instant.now()).atZone(zone).toLocalDateTime()
    val label = when(level) { ToolExpansionState.Reminder -> "التذكير"; ToolExpansionState.Resurface -> "العودة"; else -> "الكبسولة الزمنية" }
    fun confirm() {
        if (draft.due == null || (!draft.valid(Instant.now()) && draft.due != initial) || draft.error != null) {
            draft = draft.copy(error = "اختر موعدًا مستقبليًا صالحًا قبل الاعتماد.")
        } else if (level == ToolExpansionState.Capsule && draft.due != initial) dialog = "seal"
        else onApply(draft.due)
    }
    Surface(color = BeeDesign.editorialSurface.copy(alpha = .96f), shape = BeeDesign.panel,
        border = androidx.compose.foundation.BorderStroke(.8.dp, BeeDesign.editorialBorder),
        modifier = Modifier.widthIn(max = 360.dp).testTag("temporal_options")) {
        Column(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(draft.due?.let(::formatNoteTimestamp) ?: "اختر موعدًا", style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("appointment_summary").semantics { liveRegion = LiveRegionMode.Polite })
            if (draft.minutes > 0) Text("${draft.minutes} د", style = MaterialTheme.typography.labelSmall)
            draft.error?.let { Text(it, color = BeeDesign.error, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("appointment_error").semantics { liveRegion = LiveRegionMode.Polite }) }
            if (level == ToolExpansionState.Reminder) ReminderPermissionHint()
            if (level == ToolExpansionState.Reminder && state.savedReminder?.let {
                    it.scheduling == "FAILED" || it.delivery in setOf("FAILED", "BLOCKED", "MISSED") } == true) {
                Text("تعذر تسليم التنبيه؛ راجع الأذونات", color = BeeDesign.error, style = MaterialTheme.typography.bodySmall)
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row {
                    MicroTool("إضافة خمس دقائق", text = "+5", tag = "appointment_plus") { draft = draft.plusFive(Instant.now()) }
                    MicroTool("اختيار التاريخ", Icons.Outlined.DateRange, tag = "appointment_date") { dialog = "date" }
                    MicroTool("اختيار الوقت", EditorGlyphs.clock, tag = "appointment_time") { dialog = "time" }
                    if (level == ToolExpansionState.Reminder) MicroTool("أذونات وحالة التذكير", Icons.Outlined.Info, tag = "reminder_info") { dialog = "permissions" }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = ::confirm, enabled = draft.due != null && (level != ToolExpansionState.Capsule || state.canSave),
                    modifier = Modifier.testTag("appointment_confirm")) { Text("اعتماد") }
                TextButton(onClick = onCancel) { Text("إلغاء") }
                if (initial != null) TextButton(onClick = { onApply(null) }, modifier = Modifier.testTag("appointment_remove")) { Text("إزالة") }
            }
            if (level == ToolExpansionState.Capsule && !state.canSave) Text("اكتب نصًا قبل إغلاق الكبسولة", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (dialog == "date") {
        val picker = rememberDatePickerState(initialSelectedDateMillis = local.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { dialog = null }, confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let {
                    draft = draft.local(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate(), local.toLocalTime(), zone, Instant.now())
                }; dialog = null
            }) { Text("اختيار") }
        }, dismissButton = { TextButton(onClick = { dialog = null }, modifier = Modifier.testTag("appointment_dialog_cancel")) { Text("إلغاء") } }) {
            DatePicker(picker, title = null, headline = {
                picker.selectedDateMillis?.let { Text(formatNoteDate(it, ZoneOffset.UTC), modifier = Modifier.padding(24.dp)) }
            }, modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()))
        }
    }
    if (dialog == "time") {
        val picker = rememberTimePickerState(initialHour = local.hour, initialMinute = local.minute, is24Hour = true)
        AlertDialog(onDismissRequest = { dialog = null }, title = { Text("الساعة والدقيقة") },
            text = { Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) { TimeInput(picker) } },
            confirmButton = { TextButton(onClick = {
                draft = draft.local(local.toLocalDate(), LocalTime.of(picker.hour, picker.minute), zone, Instant.now()); dialog = null
            }) { Text("اختيار") } }, dismissButton = { TextButton(onClick = { dialog = null }, modifier = Modifier.testTag("appointment_dialog_cancel")) { Text("إلغاء") } })
    }
    if (dialog == "seal") {
        AlertDialog(onDismissRequest = { dialog = null }, title = { Text("إغلاق الكبسولة؟") },
            text = { Text("تُفتح في ${draft.due?.let(::formatNoteTimestamp)}. سيُحجب النص حتى هذا الموعد. لا يمكن إلغاء القفل قبله. يُطبّق الإغلاق عند مغادرة المحرر أو انتقال التطبيق للخلفية.") },
            confirmButton = { TextButton(onClick = {
                if (draft.valid(Instant.now())) { dialog = null; onApply(draft.due) }
                else { dialog = null; draft = draft.copy(error = "انقضى الموعد. اختر موعدًا آخر.") }
            }, modifier = Modifier.testTag("capsule_seal_confirm")) { Text("تأكيد قفل الكبسولة") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("متابعة الكتابة") } })
    }
    if (dialog == "permissions") AlertDialog(onDismissRequest = { dialog = null },
        title = { Text("حالة التذكير والأذونات") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            ReminderPermissions()
            ReminderDetails(state.savedReminder, onEdit = { dialog = null }, locked = false)
        } }, confirmButton = { TextButton(onClick = { dialog = null }) { Text("تم") } })
}

/** Short warning stays visible in the tool; detailed actions live behind the adjacent info icon. */
@Composable
private fun ReminderPermissionHint() {
    val app = LocalContext.current.applicationContext as BeeMindApplication
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val warning = remember(tick) {
        when {
            !app.reminderNotifier.allowed() -> "الإشعارات معطّلة؛ افتح معلومات التذكير للسماح بها."
            app.reminderScheduler.backgroundRestricted() -> "الخلفية مقيّدة؛ راجع معلومات التذكير."
            !app.reminderScheduler.exactAllowed() -> "سيكون التنبيه تقريبيًا؛ التفاصيل في معلومات التذكير."
            else -> null
        }
    }
    warning?.let { Text(it, color = BeeDesign.error, style = MaterialTheme.typography.bodySmall) }
}
