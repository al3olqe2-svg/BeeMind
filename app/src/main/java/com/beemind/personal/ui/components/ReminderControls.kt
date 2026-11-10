package com.beemind.personal.ui.components

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import java.time.format.DateTimeFormatter
import java.util.Locale

fun reminderTimestamp(at: Long): String = DateTimeFormatter.ofPattern("EEE، d MMM yyyy • HH:mm:ss z", Locale.forLanguageTag("ar"))
    .format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderPicker(edit: ReminderEdit, saved: Reminder?, enabled: Boolean, onSelect: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val due = if (edit.edited) edit.dueAt else saved?.takeIf { it.active }?.dueAt
    OutlinedButton(onClick = { open = true }, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(if (due == null) "＋ تذكير" else "◷ تذكير مفعّل")
    }
    if (open) ReminderSheet(due, onDismiss = { open = false }, onSelect = { onSelect(it); open = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderSheet(initial: Long?, onDismiss: () -> Unit, onSelect: (Long?) -> Unit) {
    val zone = ZoneId.systemDefault()
    var reference by remember { mutableStateOf(Instant.now()) }
    var minutes by remember { mutableStateOf("5") }
    var due by remember { mutableStateOf(initial) }
    var date by remember { mutableStateOf(initial?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: LocalDate.now(zone)) }
    var time by remember { mutableStateOf(initial?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() } ?: LocalTime.now(zone).withSecond(0).withNano(0)) }
    var error by remember { mutableStateOf<String?>(null) }
    var dateOpen by remember { mutableStateOf(false) }
    var timeOpen by remember { mutableStateOf(false) }
    val app = LocalContext.current.applicationContext as BeeMindApplication
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun relative(value: String, reset: Boolean) {
        minutes = value
        if (reset) reference = Instant.now()
        due = runCatching { ReminderTimes.relative(reference, value.toLong()) }.getOrNull()
        error = if (due == null) "أدخل مدة بين دقيقة و525600 دقيقة." else null
    }
    fun local() {
        due = runCatching { ReminderTimes.local(LocalDateTime.of(date, time), zone, Instant.now()) }.getOrNull()
        error = if (due == null) "الوقت ماضٍ، أو غير موجود/ملتبس بسبب التوقيت الصيفي. اختر وقتًا آخر." else null
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = BeeDesign.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Reminder — موعد لفكرتك", style = MaterialTheme.typography.titleLarge)
            Text("الإعداد جزء من المسودة. لا يسري إلا بعد حفظ الملاحظة.", style = MaterialTheme.typography.bodySmall)
            Text("بعد مدة", style = MaterialTheme.typography.titleSmall)
            Text("تُحسب المدة من لحظة اختيارها. زر +5 يمدد الموعد نفسه.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 30).forEach { n ->
                    OutlinedButton(onClick = { relative(n.toString(), true) }, modifier = Modifier.weight(1f)) { Text("$n د") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = minutes, onValueChange = { relative(it, true) }, label = { Text("دقائق أخرى") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { relative(((minutes.toLongOrNull() ?: 0).coerceAtMost(525600) + 5).toString(), false) },
                    modifier = Modifier.heightIn(min = 56.dp)) { Text("+5 دقائق") }
            }
            Text("أو تاريخ ووقت محليان", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { date = LocalDate.now(zone); local() }) { Text("اليوم") }
                TextButton(onClick = { date = LocalDate.now(zone).plusDays(1); local() }) { Text("غدًا") }
            }
            OutlinedButton(onClick = { dateOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("التاريخ: $date") }
            OutlinedButton(onClick = { timeOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("الوقت: ${time.format(DateTimeFormatter.ofPattern("HH:mm"))}") }
            Text(due?.let { "الموعد النهائي: ${reminderTimestamp(it)}" } ?: "اختر موعدًا مستقبليًا", color = MaterialTheme.colorScheme.primary)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            ReminderPermissions()
            Button(onClick = {
                val valid = due?.let { runCatching { ReminderTimes.future(it, Instant.now()) }.isSuccess } == true
                if (!valid) error = "انقضى الموعد. اختر موعدًا مستقبليًا قبل تطبيقه."
                else {
                    if (Build.VERSION.SDK_INT >= 33 && !app.reminderNotifier.allowed()) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    onSelect(due)
                }
            }, enabled = due != null, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("اعتماد الموعد في المسودة") }
            if (initial != null) TextButton(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth()) { Text("إزالة التذكير عند حفظ الملاحظة") }
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
            DatePicker(state, modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()))
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
fun ReminderDetails(reminder: Reminder?, onEdit: () -> Unit) {
    if (reminder == null || reminder.status == "CANCELLED") return
    val app = LocalContext.current.applicationContext as BeeMindApplication
    val traceKey = com.beemind.personal.notifications.ReminderDiagnostics.key(reminder.noteId)
    val traceFlow = remember(app, traceKey) { app.database.foundationDao().observeValue(traceKey) }
    val traceValue by traceFlow.collectAsStateWithLifecycle(initialValue = null)
    val trace = traceValue?.split('|')?.takeIf { it.size == 3 && it[0] == reminder.generation }
    val scope = app.reminderScope
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
    Surface(shape = BeeDesign.card, color = BeeDesign.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("◷ Reminder — التذكير", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(reminderTimestamp(reminder.dueAt), style = MaterialTheme.typography.bodyMedium)
            val status = when {
                reminder.status == "DONE" -> "تم إنجازه"
                reminder.delivery == "BLOCKED" -> "مستحق • تعذر عرض الإشعار بسبب الأذونات"
                reminder.delivery == "MISSED" -> "فات موعده • راجعه أو أجّله"
                reminder.delivery == "FAILED" -> "تعذر عرض الإشعار • ستُعاد المحاولة عند الاستعادة"
                reminder.delivery == "DELIVERED" -> "مستحق • أُرسل الإشعار"
                reminder.scheduling == "FAILED" -> "فشلت الجدولة • ستُعاد المحاولة عند فتح التطبيق"
                reminder.scheduling == "PENDING" && reminder.dueAt > System.currentTimeMillis() -> "محفوظ • بانتظار تأكيد الجدولة؛ أعد فتح التطبيق للمحاولة"
                reminder.status == "SNOOZED" -> "مؤجل عشر دقائق"
                reminder.dueAt <= System.currentTimeMillis() -> "مستحق • بانتظار التسليم"
                else -> "مجدول"
            }
            Text(status, style = MaterialTheme.typography.bodySmall)
            when (trace?.get(1)) {
                "RECOVERY_DELIVERED" -> Text("وصل متأخرًا عند الاستعادة؛ وصول إنذار الخلفية لم يُؤكد لهذا الموعد.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                "ALARM_DELIVERED" -> Text("تم التسليم عبر إنذار Android.", style = MaterialTheme.typography.bodySmall)
                "ALARM_RECEIVED" -> Text("استُلم إنذار Android؛ لم يتأكد اكتمال التسليم بعد.", style = MaterialTheme.typography.bodySmall)
                "SNOOZE_EXACT" -> Text("أعاد التأجيل جدولة إنذار دقيق لدى Android.", style = MaterialTheme.typography.bodySmall)
                "SNOOZE_INEXACT" -> Text("أعاد التأجيل جدولة إنذار تقريبي قد يتأخر.", style = MaterialTheme.typography.bodySmall)
            }
            if (reminder.active && reminder.scheduling == "INEXACT" && reminder.dueAt > System.currentTimeMillis())
                Text("الجدولة الحالية تقريبية وقد يتأخر التنبيه.", style = MaterialTheme.typography.bodySmall)
            if (reminder.active) {
                ReminderPermissions()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { action("DONE") }, enabled = !busy) { Text("إنجاز") }
                    TextButton(onClick = { action("SNOOZE") }, enabled = !busy) { Text("تأجيل 10 دقائق") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onEdit, enabled = !busy) { Text("تغيير الموعد") }
                    TextButton(onClick = { action("CANCEL") }, enabled = !busy) { Text("إزالة التذكير") }
                }
            } else TextButton(onClick = onEdit) { Text("إعداد موعد جديد") }
            if (error) Text("تعذر تنفيذ الإجراء أو تغيّر التذكير. حاول مجددًا.", color = MaterialTheme.colorScheme.error)
        }
    }
}
