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

    @Test fun uninterruptedTypingCheckpointsWithinTwoSecondsWithoutCreatingRepeatedNotes() = runTest {
        val repo = FakeRepository()
        val vm = EditorViewModel(repo, "session", null) { testScheduler.currentTime }.also { store.put("continuous", it) }
        repeat(25) {
            vm.body.edit { append("ع") }
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); runCurrent()
            advanceTimeBy(100); runCurrent()
        }
        assertNotNull("Continuous typing must not postpone recovery indefinitely", repo.draft)
        assertTrue(repo.draft!!.body.length >= 19)
        assertTrue(repo.notes.isEmpty())
        vm.requestExit()
        assertEquals("ع".repeat(25), repo.notes["session"]!!.body)
        assertEquals(1, repo.notes.size)
    }
    @Test fun externalCapsuleLockClearsRetainedEditorAndStopsPendingAutosave() = runTest {
        val base = FakeRepository().apply { notes["old"] = Note("old", "", "خاص", 1, 1) }
        val updates = kotlinx.coroutines.flow.MutableStateFlow(base.notes["old"])
        val repo = object : NotesRepository by base { override fun observeNote(id: String) = updates }
        val vm = EditorViewModel(repo, "session", "old").also { store.put("locked-external", it) }
        vm.body.edit { append(" تعديل") }
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); runCurrent()
        updates.value = Note("old", null, "", 1, 1, unlockAt = 10000, locked = true)
        runCurrent(); advanceUntilIdle()
        assertEquals("", vm.body.text.toString()); assertEquals(10000L, vm.state.value.lockedUntil)
        assertEquals(0, base.saveCalls)
        vm.requestExit(); assertTrue(vm.state.value.exited)
    }
    @Test fun typingDebouncePersistsDraftThenCommitsWithoutDeparture() = runTest {
        val repo = FakeRepository(); val vm = editor(repo)
        vm.body.edit { append("حفظ بلا مغادرة") }
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        runCurrent(); advanceTimeBy(201); runCurrent()
        assertEquals("حفظ بلا مغادرة", repo.draft!!.body)
        assertTrue(repo.notes.isEmpty())
        advanceTimeBy(600); runCurrent()
        assertEquals("حفظ بلا مغادرة", repo.notes["session"]!!.body)
        assertNull(vm.state.value.savedId)
    }
    @Test fun titlelessBackCommitsLastCharactersWithoutConfirmation() = runTest {
        val repo = FakeRepository(); val vm = editor(repo)
        vm.body.edit { append("آخر حرف ي") }
        vm.requestExit()
        assertEquals("آخر حرف ي", repo.notes["session"]!!.body)
        assertEquals("", repo.notes["session"]!!.title)
        assertTrue(vm.state.value.exited)
    }
    @Test fun emptyAndWhitespaceExitNeverCreateNotes() = runTest {
        val repo = FakeRepository(); val vm = editor(repo)
        vm.body.edit { append("  \n ") }; vm.requestExit()
        assertTrue(vm.state.value.exited); assertTrue(repo.notes.isEmpty()); assertNull(repo.draft)
    }
    @Test fun autoAndManualWritesAreSerializedAndNavigationWaitsForCommit() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeRepository().apply { saveGate = gate }; val vm = editor(repo)
        vm.body.edit { append("الأول") }; vm.persistOnBackground()
        assertEquals(1, repo.saveCalls)
        vm.body.edit { append(" والأخير") }; vm.save(); vm.save()
        assertTrue(vm.state.value.busy); assertNull(vm.state.value.savedId)
        gate.complete(Unit); advanceUntilIdle()
        assertEquals("الأول والأخير", repo.notes["session"]!!.body)
        assertEquals(1, repo.notes.size); assertEquals("session", vm.state.value.savedId)
    }
    @Test fun saveFailureKeepsDurableRecoveryTextAndDoesNotNavigate() = runTest {
        val repo = FakeRepository().apply { failSave = true }; val vm = editor(repo)
        vm.body.edit { append("كتابة محفوظة للاستعادة") }; vm.save()
        assertEquals(EditorError.SAVE, vm.state.value.error); assertNull(vm.state.value.savedId)
        assertEquals(vm.body.text.toString(), repo.draft!!.body)
        assertFalse(vm.state.value.busy)
        repo.failSave = false; vm.save(); assertEquals("session", vm.state.value.savedId)
    }
    @Test fun emptyExistingEditKeepsOriginalAndRecoveryDraft() = runTest {
        val repo = FakeRepository().apply { notes["old"] = Note("old", null, "محتوى أصلي", 1, 1) }
        val vm = EditorViewModel(repo, "session", "old").also { store.put("old", it) }
        vm.body.edit { replace(0, length, "") }; vm.requestExit()
        assertEquals("محتوى أصلي", repo.notes["old"]!!.body)
        assertEquals("", repo.draft!!.body); assertTrue(vm.state.value.exited)
    }
    @Test fun backgroundCommitPreservesCreatedAtAndOtherTraits() = runTest {
        val repo = FakeRepository().apply { notes["old"] = Note("old", null, "أصلي", 1, 1, ExpressiveTrait.GLOW) }
        val vm = EditorViewModel(repo, "session", "old").also { store.put("old", it) }
        vm.body.edit { append(" جديد") }; vm.selectTrait(ExpressiveTrait.ANGER); vm.persistOnBackground()
        advanceUntilIdle()
        assertEquals(1L, repo.notes["old"]!!.createdAt)
        assertEquals(ExpressiveTrait.ANGER, repo.notes["old"]!!.expressiveTrait)
        assertNull(vm.state.value.savedId)
    }
    @Test fun freshVmRestoresLongDurableDraftWithoutGeneratingTitle() = runTest {
        val text = "عربي 🌈\n".repeat(6000)
        val repo = FakeRepository().apply { draft = EditorDraft("session", null, "", text, 1) }
        val vm = editor(repo)
        assertEquals(text, vm.body.text.toString()); assertEquals("", vm.title.text.toString())
        vm.save(); assertEquals(text, repo.notes["session"]!!.body)
    }
    @Test fun loadFailureExitDoesNotDiscardUnreadDraft() = runTest {
        val repo = FakeRepository().apply { failLoad = true; draft = EditorDraft("session", null, "", "خاص", 1) }
        val vm = editor(repo); vm.requestExit()
        assertTrue(vm.state.value.exited); assertNotNull(repo.draft); assertEquals(0, repo.discardCalls)
    }
    @Test fun capsuleDraftDoesNotSealWhileTypingButBackSealsAndClearsLiveText() = runTest {
        val repo = FakeRepository(); val vm = editor(repo)
        vm.body.edit { append("سري") }; vm.selectCapsule(10000)
        // Snapshot changes are delivered by Compose; explicitly flush lifecycle here.
        vm.requestExit()
        assertEquals(10000L, vm.state.value.lockedUntil)
        assertEquals("", vm.body.text.toString()); assertEquals("session", vm.state.value.savedId)
    }
    @Test fun lockedRecoveryNeverLoadsDraftOrAllowsSave() = runTest {
        val repo = FakeRepository().apply {
            notes["session"] = Note("session", null, "", 1, 1, unlockAt = 10000, locked = true)
            draft = EditorDraft("session", null, "", "سري", 1)
        }
        val vm = editor(repo); vm.save(); vm.persistOnBackground()
        assertEquals("", vm.body.text.toString()); assertEquals(0, repo.saveCalls)
        assertEquals(10000L, vm.state.value.lockedUntil)
    }
    @Test fun unchangedEditDoesNotIssueContentWrite() = runTest {
        val repo = FakeRepository().apply { notes["old"] = Note("old", null, "ثابت", 1, 1) }
        val vm = EditorViewModel(repo, "session", "old").also { store.put("old", it) }
        vm.save(); assertEquals(0, repo.saveCalls); assertEquals("old", vm.state.value.savedId)
    }
    private class FakeRepository : NotesRepository {
        override fun observeThreadNotes(id: String, linkedOnly: Boolean) = kotlinx.coroutines.flow.flowOf(emptyList<com.beemind.personal.domain.ThreadNote>())
        override suspend fun setLinked(a: String, b: String, linked: Boolean) { error("Not used by editor tests") }
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
        override suspend fun save(id: String, title: String, body: String, isNew: Boolean, sessionId: String, expressiveTrait: ExpressiveTrait, reminderEdit: ReminderEdit, temporalEdit: TemporalEdit): Note {
            saveCalls++
            saveGate?.await()
            if (failSave) throw IllegalStateException("Injected write failure")
            return Note(id, title, body, 1, 1, expressiveTrait, if (reminderEdit.edited && reminderEdit.dueAt != null)
                Reminder(id, reminderEdit.dueAt, "SCHEDULED", "test") else null, unlockAt = temporalEdit.sealUntil, locked = temporalEdit.sealUntil != null).also { notes[id] = it }
        }
        override suspend fun delete(id: String) { notes.remove(id) }
    }
}
