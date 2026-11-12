package com.beemind.personal.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beemind.personal.domain.*
import com.beemind.personal.ui.theme.BeeDesign

/** Search only the same redacted projection that can be rendered. Never query private text. */
fun matchesThreadSearch(note: NoteSummary, query: String): Boolean {
    val safe = if (note.locked) "كبسولة مغلقة" else note.title.orEmpty() + " " + note.preview
    return safe.contains(query.trim(), ignoreCase = true)
}

@Composable
fun ThreadsSection(vm: DetailViewModel, onOpen: (String) -> Unit, enabled: Boolean) {
    val links by vm.linkedNotes.collectAsStateWithLifecycle()
    val candidates by vm.candidates.collectAsStateWithLifecycle()
    val busy by vm.linkBusy.collectAsStateWithLifecycle()
    val error by vm.linkError.collectAsStateWithLifecycle()
    var picker by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(vm) { onDispose { vm.showThreadPicker(false) } }
    LaunchedEffect(picker) { vm.showThreadPicker(picker) }
    Column(Modifier.fillMaxWidth().testTag("threads_section"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(EditorGlyphs.link, null, tint = BeeDesign.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("الخيوط", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
            TextButton(onClick = { picker = true }, enabled = enabled, modifier = Modifier.testTag("add_thread")) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Text("ربط فكرة")
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(BeeDesign.rainbow))
        when (val content = links) {
            ThreadContent.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            ThreadContent.Error -> TextButton(onClick = vm::retryThreads) { Text("تعذر تحميل الخيوط؛ إعادة المحاولة") }
            is ThreadContent.Ready -> if (content.notes.isEmpty()) {
                Text("خيط واحد يفتح طريقًا لفكرة أخرى", color = BeeDesign.secondary, style = MaterialTheme.typography.bodySmall)
            } else LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(content.notes, key = { it.note.id }) { candidate ->
                    ThreadCard(candidate.note, Modifier.fillMaxWidth(), enabled && candidate.note.id !in busy,
                        onOpen = { onOpen(candidate.note.id) },
                        onRemove = { vm.setLinked(candidate.note.id, false) }, busy = candidate.note.id in busy)
                }
            }
        }
        if (error) Text("تعذر تغيير الرابط. لم يُحذف أي محتوى؛ حاول مجددًا.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    if (picker) ThreadPicker(candidates, busy, error, enabled, onDismiss = { picker = false }, onToggle = vm::setLinked, onRetry = vm::retryThreads)
}

@Composable
private fun ThreadCard(note: NoteSummary, modifier: Modifier, enabled: Boolean, onOpen: () -> Unit, onRemove: () -> Unit, busy: Boolean = false) {
    Surface(modifier.border(1.dp, BeeDesign.border, BeeDesign.card), shape = BeeDesign.card, color = BeeDesign.surface) {
        Column {
            Surface(onClick = onOpen, enabled = enabled, color = BeeDesign.surface,
                modifier = Modifier.fillMaxWidth().testTag("thread_open_${note.id}")) {
                Column(Modifier.padding(14.dp)) { ThreadPreview(note) }
            }
            TextButton(onClick = onRemove, enabled = enabled, modifier = Modifier.align(Alignment.End).testTag("thread_remove_${note.id}")) {
                Icon(Icons.Default.Close, null, Modifier.size(18.dp))
                Text(if (busy) "جارٍ الحفظ…" else "إزالة الرابط")
            }
        }
    }
}

@Composable
internal fun ThreadPreview(note: NoteSummary) {
    if (note.locked) {
        Text("كبسولة مغلقة", style = MaterialTheme.typography.titleSmall)
        note.unlockAt?.let { Text(formatNoteTimestamp(it), style = MaterialTheme.typography.bodySmall, color = BeeDesign.secondary) }
    } else if (note.preview.isNotBlank()) {
        Text(note.preview, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
            color = BeeDesign.ink)
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadPicker(content: ThreadContent, busy: Set<String>, error: Boolean, enabled: Boolean,
    onDismiss: () -> Unit, onToggle: (String, Boolean) -> Unit, onRetry: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().systemBarsPadding(), color = BeeDesign.background) {
            Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Threads — الخيوط", style = MaterialTheme.typography.titleMedium)
                        Text("اربط الأفكار، واحتفظ بكل ملاحظة مستقلة", style = MaterialTheme.typography.bodySmall, color = BeeDesign.secondary)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "إغلاق اختيار الخيوط") }
                }
                Box(Modifier.fillMaxWidth().height(2.dp).background(BeeDesign.rainbow))
                OutlinedTextField(query, { query = it }, label = { Text("بحث في المعاينات المتاحة") },
                    singleLine = true, shape = BeeDesign.button, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("thread_search"))
                if (error) Text("تعذر تغيير الرابط؛ حاول مجددًا.", color = MaterialTheme.colorScheme.error)
                when (content) {
                    ThreadContent.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    ThreadContent.Error -> TextButton(onClick = onRetry) { Text("تعذر تحميل الملاحظات؛ إعادة المحاولة") }
                    is ThreadContent.Ready -> {
                        val notes = remember(content.notes, query) { content.notes.filter { matchesThreadSearch(it.note, query) } }
                        if (notes.isEmpty()) Text(if (query.isBlank()) "لا توجد ملاحظات أخرى لربطها بعد." else "لا توجد معاينات مطابقة.", color = BeeDesign.secondary)
                        LazyColumn(Modifier.weight(1f).testTag("thread_candidates"), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                            items(notes, key = { it.note.id }) { candidate ->
                                Surface(onClick = { onToggle(candidate.note.id, !candidate.linked) },
                                    enabled = enabled && candidate.note.id !in busy, shape = BeeDesign.card, color = BeeDesign.surface,
                                    modifier = Modifier.fillMaxWidth().border(1.dp, if (candidate.linked) BeeDesign.accent else BeeDesign.border, BeeDesign.card)
                                        .testTag("thread_candidate_${candidate.note.id}").semantics {
                                            stateDescription = if (candidate.linked) "مرتبطة بالفعل؛ اضغط لإزالة الرابط" else "غير مرتبطة؛ اضغط للربط"
                                        }) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        ThreadPreview(candidate.note)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(if (candidate.linked) EditorGlyphs.link else Icons.Default.Add, null, tint = BeeDesign.accent, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(if (candidate.note.id in busy) "جارٍ الحفظ…" else if (candidate.linked) "مرتبطة • اضغط لفك الخيط" else "ربط هذه الفكرة", style = MaterialTheme.typography.labelLarge, color = BeeDesign.accent)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Safe representation: locked branch never composes preview/title, including malformed inputs. */
@Composable
internal fun InlineThreadCard(note: NoteSummary, enabled: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val edge = when {
        note.locked -> Brush.linearGradient(listOf(Color(0xFFCBD0D9), Color(0xFFE5E1D8)))
        note.expressiveTrait == ExpressiveTrait.GLOW -> BeeDesign.rainbow
        note.expressiveTrait == ExpressiveTrait.ANGER -> Brush.linearGradient(listOf(Color(0xFFCE6681), BeeDesign.editorialBorder))
        else -> Brush.linearGradient(listOf(BeeDesign.editorialBorder, BeeDesign.editorialBorder.copy(alpha = .5f)))
    }
    Surface(onClick = onOpen, enabled = enabled, shape = BeeDesign.card,
        color = BeeDesign.editorialSurface.copy(alpha = .82f),
        border = androidx.compose.foundation.BorderStroke(.8.dp, edge),
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("inline_thread_${note.id}")) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { ThreadPreview(note) }
            Spacer(Modifier.width(12.dp))
            Icon(if (note.locked) Icons.Outlined.Lock else EditorGlyphs.link, null,
                Modifier.size(18.dp), tint = BeeDesign.secondary)
        }
    }
}
