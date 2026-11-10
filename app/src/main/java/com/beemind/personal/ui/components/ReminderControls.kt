package com.beemind.personal.ui.components

import androidx.compose.material.icons.filled.DateRange
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import android.Manifest
import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.beemind.personal.BeeMindApplication
import com.beemind.personal.domain.*
import com.beemind.personal.ui.theme.BeeDesign
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.*

fun reminderTimestamp(at: Long): String = com.beemind.personal.ui.notes.formatNoteTimestamp(at)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderPicker(edit: ReminderEdit, saved: Reminder?, enabled: Boolean, onSelect: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val due = if (edit.edited) edit.dueAt else saved?.takeIf { it.active }?.dueAt
    OutlinedButton(onClick = { open = true }, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(if (due == null) "＋ تذكير" else "◷ تذكير مفعّل")
    }
    if (open) ReminderSheet(due, onDismiss = { open = false }, onSelect = { if (it != due) onSelect(it); open = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSheet(initial: Long?, onDismiss: () -> Unit, onSelect: (Long?) -> Unit, temporalLabel: String? = null, isDraft: Boolean = true) {
    val zone = ZoneId.systemDefault()
    var reference by remember { mutableStateOf(Instant.now()) }
    var minutes by remember { mutableIntStateOf(0) }
    var due by remember { mutableStateOf(initial) }
    var date by remember { mutableStateOf(initial?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: LocalDate.now(zone)) }
    var time by remember { mutableStateOf(initial?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() } ?: LocalTime.now(zone).withSecond(0).withNano(0)) }
    var error by remember { mutableStateOf<String?>(null) }
    var dateOpen by remember { mutableStateOf(false) }
    var timeOpen by remember { mutableStateOf(false) }
    val app = LocalContext.current.applicationContext as BeeMindApplication
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun relative() {
        if (minutes == 0) reference = Instant.now()
        minutes = (minutes + 5).coerceAtMost(525600)
        due = ReminderTimes.relative(reference, minutes.toLong())
        error = null
    }
    fun local() {
        minutes = 0
        due = runCatching { ReminderTimes.local(LocalDateTime.of(date, time), zone, Instant.now()) }.getOrNull()
        error = if (due == null) "الوقت ماضٍ، أو غير موجود/ملتبس بسبب التوقيت الصيفي. اختر وقتًا آخر." else null
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = BeeDesign.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(temporalLabel ?: "التذكير", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = ::relative) { Text("+5", style = MaterialTheme.typography.labelLarge.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr)) }
                if (minutes > 0) Text("$minutes د", modifier = Modifier.padding(vertical = 12.dp))
                IconButton(onClick = { dateOpen = true }) {
                    Icon(androidx.compose.material.icons.Icons.Default.DateRange, "اختيار التاريخ")
                }
                IconButton(onClick = { timeOpen = true }, modifier = Modifier.semantics { contentDescription = "اختيار الوقت" }) {
                    val color = MaterialTheme.colorScheme.onSurface
                    Canvas(Modifier.size(24.dp)) {
                        drawCircle(color, radius = size.minDimension * .4f, style = Stroke(2.dp.toPx()))
                        drawLine(color, center, Offset(center.x, size.height * .25f), 2.dp.toPx())
                        drawLine(color, center, Offset(size.width * .7f, size.height * .6f), 2.dp.toPx())
                    }
                }
            }
            due?.let { Text(reminderTimestamp(it), color = MaterialTheme.colorScheme.primary) }
            if (temporalLabel?.contains("كبسولة") == true) Text("سيُحجب النص حتى الموعد، ولا يمكن إلغاء القفل.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (temporalLabel == null) ReminderPermissions()
            Button(onClick = {
                val valid = due?.let { runCatching { ReminderTimes.future(it, Instant.now()) }.isSuccess } == true
                if (!valid) error = "انقضى الموعد. اختر موعدًا مستقبليًا قبل تطبيقه."
                else {
                    if (temporalLabel == null && Build.VERSION.SDK_INT >= 33 && !app.reminderNotifier.allowed()) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    onSelect(due)
                }
            }, enabled = due != null, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("اعتماد") }
            if (initial != null) TextButton(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth()) { Text("إلغاء الموعد") }
        }
    }
    if (dateOpen) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { dateOpen = false }, confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate(); local() }
                dateOpen = false
            }) { Text("اختيار") }
        }, dismissButton = { TextButton(onClick = { dateOpen = false }) { Text("إلغاء") } }) {
            DatePicker(state, title = null, headline = {
                state.selectedDateMillis?.let {
                    Text(com.beemind.personal.ui.notes.formatNoteDate(it, ZoneOffset.UTC), modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                }
            }, modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()))
        }
    }
    if (timeOpen) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
        AlertDialog(onDismissRequest = { timeOpen = false }, title = { Text("الساعة والدقيقة") },
            text = { Box(Modifier.fillMaxWidth()) { TimeInput(state) } },
            confirmButton = { TextButton(onClick = { time = LocalTime.of(state.hour, state.minute); local(); timeOpen = false }) { Text("اختيار") } },
            dismissButton = { TextButton(onClick = { timeOpen = false }) { Text("إلغاء") } })
    }
}

@Composable
fun ReminderPermissions() {
    val context = LocalContext.current
    val app = context.applicationContext as BeeMindApplication
    var tick by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val notifications = remember(tick) { app.reminderNotifier.allowed() }
    val exact = remember(tick) { app.reminderScheduler.exactAllowed() }
    val backgroundRestricted = remember(tick) { app.reminderScheduler.backgroundRestricted() }
    fun settings(intent: Intent) {
        try { context.startActivity(intent) } catch (_: android.content.ActivityNotFoundException) { error = true }
    }
    if (!notifications) {
        Text("الإشعارات أو قناتها معطّلة: سيبقى التذكير محفوظًا، لكن لن يظهر تنبيه حتى تسمح بها.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = {
            if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }, enabled = Build.VERSION.SDK_INT >= 33) { Text("السماح بالإشعارات") }
        TextButton(onClick = { settings(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, com.beemind.personal.notifications.AndroidReminderNotifier.CHANNEL)) }) { Text("إعدادات إشعارات BeeMind") }
    }
    if (backgroundRestricted) {
        Text("Android يقيّد نشاط BeeMind في الخلفية. قد يمنع الإشعار حتى تفتح التطبيق؛ إذن التنبيه الدقيق وحده لا يرفع هذا القيد.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = { settings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:${context.packageName}".toUri())) }) { Text("مراجعة قيود الخلفية في إعدادات التطبيق") }
    }
    if (!exact) {
        Text("التنبيه الدقيق غير مسموح. سيُستخدم تنبيه تقريبي قد يتأخر حسب قيود Android والبطارية.", style = MaterialTheme.typography.bodySmall)
        if (Build.VERSION.SDK_INT >= 31) TextButton(onClick = {
            settings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
        }) { Text("إتاحة التنبيهات الدقيقة") }
    }
    if (error) Text("افتح إعدادات Android ← التطبيقات ← BeeMind لتغيير الأذونات.", color = MaterialTheme.colorScheme.error)
}

@Composable
fun ReminderDetails(reminder: Reminder?, onEdit: () -> Unit, locked: Boolean = false) {
    if (reminder == null || reminder.status == "CANCELLED") return
    val app = LocalContext.current.applicationContext as BeeMindApplication
    val scope = app.reminderScope
    var editLocked by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    fun action(name: String) {
        if (busy) return
        busy = true
        scope.launch {
            try { if (!app.reminders.action(reminder.noteId, reminder.generation, name)) withContext(Dispatchers.Main) { error = true } }
            catch (_: Exception) { withContext(Dispatchers.Main) { error = true } }
            finally { withContext(Dispatchers.Main) { busy = false } }
        }
    }
    if (editLocked) ReminderSheet(reminder.dueAt, { editLocked = false }, { due ->
        editLocked = false
        scope.launch {
            try { app.notesRepository.scheduleReminder(reminder.noteId, due) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { withContext(Dispatchers.Main) { error = true } }
        }
    }, isDraft = false)
    val editAction = { if (locked) editLocked = true else onEdit() }
    Surface(shape = BeeDesign.card, color = BeeDesign.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(reminderTimestamp(reminder.dueAt), style = MaterialTheme.typography.bodyMedium)
            if (reminder.status == "DONE") Text("تم إنجازه", style = MaterialTheme.typography.bodySmall)
            if (reminder.scheduling == "FAILED" || reminder.delivery in setOf("FAILED", "BLOCKED", "MISSED"))
                Text("تعذر تسليم التذكير في موعده؛ راجع الأذونات أو عدّل الموعد.", color = MaterialTheme.colorScheme.error)
            if (reminder.active) {
                ReminderPermissions()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { action("DONE") }, enabled = !busy) { Text("إنجاز") }
                    TextButton(onClick = { action("SNOOZE") }, enabled = !busy) { Text("تأجيل 10 دقائق") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = editAction, enabled = !busy) { Text("تغيير الموعد") }
                    TextButton(onClick = { action("CANCEL") }, enabled = !busy) { Text("إزالة التذكير") }
                }
            } else TextButton(onClick = editAction) { Text("إعداد موعد جديد") }
            if (error) Text("تعذر تنفيذ الإجراء أو تغيّر التذكير. حاول مجددًا.", color = MaterialTheme.colorScheme.error)
        }
    }
}
