package com.beemind.personal.ui.wallpaper

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beemind.personal.BeeMindApplication
import com.beemind.personal.ui.components.BeeBack
import com.beemind.personal.ui.theme.BeeDesign
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperSettingsScreen(vm: WallpaperViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val sessions = (LocalContext.current.applicationContext as BeeMindApplication).wallpaperSessions
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        sessions.pickingImages = false
        vm.import(uris)
    }
    var removing by remember { mutableStateOf<String?>(null) }
    var slider by remember(state.blur) { mutableFloatStateOf(state.blur.toFloat()) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snack.showSnackbar(it); vm.dismissMessage() } }
    Scaffold(containerColor = BeeDesign.editorialBackground,
        topBar = { TopAppBar(title = { Text("الخلفيات", style = MaterialTheme.typography.titleLarge) }, navigationIcon = { BeeBack(onBack) }) },
        snackbarHost = { SnackbarHost(snack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("wallpaper_settings"),
            contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                Text("مساحة لأفكارك", style = MaterialTheme.typography.headlineSmall)
                Text("صورك تصنع أجواء الرئيسية فقط. تتبدّل عند دخول التطبيق، أو تبقى معك عند تثبيتها.",
                    color = BeeDesign.secondary, style = MaterialTheme.typography.bodyMedium)
            }
            item {
                Box(Modifier.fillMaxWidth().height(180.dp)) {
                    WallpaperBackground(state.copy(blur = slider.roundToInt()), vm.repository, vm.images, Modifier.fillMaxSize())
                    Text("معاينة الأجواء", color = BeeDesign.darkForeground,
                        modifier = Modifier.align(Alignment.BottomStart).padding(16.dp), style = MaterialTheme.typography.labelLarge)
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("شدة الضبابية", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text("${slider.roundToInt()}٪", color = BeeDesign.secondary)
                }
                Slider(value = slider, onValueChange = { slider = it }, valueRange = 20f..100f,
                    onValueChangeFinished = { vm.blur(slider.roundToInt()) }, enabled = state.loaded,
                    modifier = Modifier.fillMaxWidth().testTag("wallpaper_blur").semantics { contentDescription = "شدة ضبابية الخلفية" })
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("صور الخلفية", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = {
                        sessions.pickingImages = true
                        try { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                        catch (_: android.content.ActivityNotFoundException) { sessions.pickingImages = false; vm.pickerFailed() }
                    }, enabled = state.loaded && !busy, modifier = Modifier.testTag("add_wallpapers")) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Text("إضافة صور")
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.ids.isEmpty() && !busy) Text("لم تضف صورًا بعد. اختر صورة واحدة أو مجموعة من جهازك؛ تُحفظ نسخة خاصة دون تغيير الأصل.",
                    style = MaterialTheme.typography.bodyMedium, color = BeeDesign.secondary)
            }
            items(state.ids, key = { it }) { id ->
                val pinned = state.pinnedId == id
                Surface(shape = BeeDesign.card, color = BeeDesign.surface,
                    border = BorderStroke(1.dp, if (pinned) BeeDesign.accent else BeeDesign.border),
                    modifier = Modifier.fillMaxWidth().testTag("wallpaper_$id")) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        WallpaperThumbnail(id, vm.repository, vm.images, Modifier.fillMaxWidth().height(140.dp))
                        Text(if (pinned) "مثبتة · خلفيتك الدائمة" else if (state.currentId == id) "الخلفية الحالية" else "ضمن التبديل التلقائي",
                            style = MaterialTheme.typography.labelLarge, color = BeeDesign.ink)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { vm.pin(if (pinned) null else id) }, modifier = Modifier.weight(1f)
                                .semantics { stateDescription = if (pinned) "مثبتة" else "غير مثبتة" }) {
                                Text(if (pinned) "إلغاء التثبيت" else "تثبيت الصورة")
                            }
                            IconButton(onClick = { removing = id }) {
                                Icon(Icons.Outlined.Delete, "إزالة الصورة", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
    removing?.let { id -> AlertDialog(onDismissRequest = { removing = null },
        title = { Text("إزالة الخلفية؟") }, text = { Text("ستُحذف نسخة التطبيق فقط. تبقى الصورة الأصلية في جهازك.") },
        confirmButton = { TextButton(onClick = { vm.remove(id); removing = null }) { Text("إزالة") } },
        dismissButton = { TextButton(onClick = { removing = null }) { Text("إلغاء") } }) }
}
