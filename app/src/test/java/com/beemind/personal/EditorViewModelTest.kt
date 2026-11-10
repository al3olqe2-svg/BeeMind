package com.beemind.personal

import androidx.lifecycle.ViewModelStore
import com.beemind.personal.domain.*
import com.beemind.personal.ui.notes.EditorError
import com.beemind.personal.ui.notes.EditorViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }
    private fun editor(repo: FakeRepository): EditorViewModel = EditorViewModel(repo, "session", null)
        .also { store.put("editor", it) }

    @Test fun leavingAfterLoadFailurePreservesTheUnreadRecoveryDraft() = runTest {
        val repo = FakeRepository().apply {
            draft = EditorDraft("session", null, "خاص", "نص لا يجوز حذفه بسبب خطأ قراءة", 1)
            failLoad = true
        }
        val vm = editor(repo)
        assertTrue(vm.state.value.loadFailed)
        vm.requestExit()
        assertTrue(vm.state.value.exited)
        assertNotNull(repo.draft)
        assertEquals(0, repo.discardCalls)
    }

    @Test fun saveFailureLeavesLiveTextAndReportsAnActualError() = runTest {
        val repo = FakeRepository().apply { failSave = true }
        val vm = editor(repo)
        vm.body.edit { append("نص باقٍ رغم فشل التخزين") }
        vm.save()
        assertEquals("نص باقٍ رغم فشل التخزين", vm.body.text.toString())
        assertEquals(EditorError.SAVE, vm.state.value.error)
        assertNull(vm.state.value.savedId)
        assertFalse(vm.state.value.busy)
        assertTrue(repo.notes.isEmpty())
    }

    @Test fun repeatedSaveClicksOnlyLaunchOneWriteAndUseTheStableSessionId() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeRepository().apply { saveGate = gate }
        val vm = editor(repo)
        vm.title.edit { append("عنوان واحد") }
        vm.save(); vm.save(); vm.save()
        assertTrue(vm.state.value.busy)
        assertEquals(1, repo.saveCalls)
        gate.complete(Unit)
        assertEquals("session", vm.state.value.savedId)
        vm.save()
        assertEquals(1, repo.saveCalls)
        assertEquals(listOf("session"), repo.notes.keys.toList())
    }

    @Test fun freshEditorRestoresTheDiskDraftInsteadOfAnEmptyOrBundledString() = runTest {
        val text = "مسودة عربية طويلة 🌈\n".repeat(6_000)
        val repo = FakeRepository().apply { draft = EditorDraft("session", null, "يومياتي", text, 1) }
        val vm = editor(repo)
        assertEquals(text, vm.body.text.toString())
        assertEquals("يومياتي", vm.title.text.toString())
        assertTrue(vm.state.value.dirty)
    }

    @Test fun backRequiresAChoiceAndDiscardOnlyRemovesTheDraft() = runTest {
        val repo = FakeRepository()
        val vm = editor(repo)
        vm.body.edit { append("كتابة غير محفوظة") }
        vm.requestExit()
        assertTrue(vm.state.value.confirmExit)
        assertFalse(vm.state.value.exited)
        vm.stay()
        assertEquals("كتابة غير محفوظة", vm.body.text.toString())
        vm.discardAndExit()
        assertTrue(vm.state.value.exited)
        assertEquals(1, repo.discardCalls)
        assertTrue(repo.notes.isEmpty())
    }

    @Test fun traitOnlyEditIsProtectedRecoveredAndSavedWithText() = runTest {
        val repo = FakeRepository().apply {
            notes["existing"] = Note("existing", "عنوان", "النص الأصلي", 1, 1, ExpressiveTrait.GLOW)
        }
        val vm = EditorViewModel(repo, "session", "existing").also { store.put("first", it) }
        assertEquals(ExpressiveTrait.GLOW, vm.state.value.expressiveTrait)
        vm.selectTrait(ExpressiveTrait.ANGER)
        vm.requestExit()
        assertTrue(vm.state.value.confirmExit)
        vm.stay()
        vm.persistOnBackground()
        advanceUntilIdle()
        assertEquals(ExpressiveTrait.ANGER, repo.draft!!.expressiveTrait)
        assertEquals(ExpressiveTrait.GLOW, repo.notes["existing"]!!.expressiveTrait)
        val restored = EditorViewModel(repo, "session", "existing").also { store.put("restored", it) }
        assertEquals(ExpressiveTrait.ANGER, restored.state.value.expressiveTrait)
        assertEquals("النص الأصلي", restored.body.text.toString())
        restored.selectTrait(ExpressiveTrait.NONE)
        restored.save()
        assertEquals(ExpressiveTrait.NONE, repo.notes["existing"]!!.expressiveTrait)
    }

    @Test fun reminderDraftRecoversAndDiscardNeverCallsSave() = runTest {
        val repo = FakeRepository()
        val vm = EditorViewModel(repo, "reminder-session", null).also { store.put("reminder", it) }
        vm.body.edit { append("مسودة تذكير") }
        vm.selectTrait(ExpressiveTrait.GLOW)
        val due = System.currentTimeMillis() + 300_000
        vm.selectReminder(due)
        vm.persistOnBackground()
        advanceUntilIdle()
        assertEquals(ReminderEdit(true, due), repo.draft!!.reminderEdit)
        assertEquals(0, repo.saveCalls)
        val restored = EditorViewModel(repo, "reminder-session", null).also { store.put("restored-reminder", it) }
        assertEquals(ReminderEdit(true, due), restored.state.value.reminderEdit)
        restored.requestExit()
        assertTrue(restored.state.value.confirmExit)
        restored.discardAndExit()
        assertEquals(0, repo.saveCalls)
        assertNull(repo.draft)
    }

    /** Fault injection only; the shipped application always uses RoomNotesRepository. */
    private class FakeRepository : NotesRepository {
        val notes = linkedMapOf<String, Note>()
        var draft: EditorDraft? = null
        var failLoad = false
        var failSave = false
        var saveGate: CompletableDeferred<Unit>? = null
        var saveCalls = 0
        var discardCalls = 0
        override fun observeNotes() = flowOf(emptyList<NoteSummary>())
        override fun observeNote(id: String) = flowOf(notes[id])
        override fun observeLatestDraft() = flowOf<DraftSummary?>(null)
        override suspend fun getNote(id: String) = notes[id]
        override suspend fun getDraft(sessionId: String): EditorDraft? {
            if (failLoad) throw IllegalStateException("Injected read failure")
            return draft
        }
        override suspend fun latestDraftForNote(noteId: String): DraftSummary? = null
        override suspend fun putDraft(draft: EditorDraft) { this.draft = draft }
        override suspend fun discardDraft(sessionId: String) { discardCalls++; draft = null }
        override suspend fun save(id: String, title: String, body: String, isNew: Boolean, sessionId: String, expressiveTrait: ExpressiveTrait, reminderEdit: ReminderEdit): Note {
            saveCalls++
            saveGate?.await()
            if (failSave) throw IllegalStateException("Injected write failure")
            return Note(id, title, body, 1, 1, expressiveTrait, if (reminderEdit.edited && reminderEdit.dueAt != null)
                Reminder(id, reminderEdit.dueAt, "SCHEDULED", "test") else null).also { notes[id] = it }
        }
        override suspend fun delete(id: String) { notes.remove(id) }
    }
}
