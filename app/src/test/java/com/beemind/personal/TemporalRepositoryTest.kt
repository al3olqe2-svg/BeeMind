package com.beemind.personal

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.beemind.personal.data.RoomNotesRepository
import com.beemind.personal.data.local.BeeMindDatabase
import com.beemind.personal.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class TemporalRepositoryTest {
    private lateinit var db: BeeMindDatabase
    private lateinit var repo: RoomNotesRepository
    private val clock = FakeClock()
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), BeeMindDatabase::class.java).build()
        repo = RoomNotesRepository(db, clock)
    }
    @After fun close() { db.close() }
    private suspend fun create(id: String = "old", trait: ExpressiveTrait = ExpressiveTrait.GLOW, resurface: Long? = null, unlock: Long? = null) =
        repo.save(id, "عنوان سري", "رسالة خاصة 🌈\n".repeat(50), true, "draft", trait,
            temporalEdit = TemporalEdit(resurface != null, resurface, unlock))
    private suspend fun summaries() = repo.observeNotes().first()
    private suspend fun edit(id: String, temporal: TemporalEdit) {
        val n = requireNotNull(repo.getNote(id))
        repo.save(id, n.title.orEmpty(), n.body, false, "s", n.expressiveTrait, temporalEdit = temporal)
    }
    @Test fun dueOrderingUsesResurfaceThenOriginalOrderAndStableIds() = runBlocking {
        val first = create("a", resurface = clock.now + 100)
        create("b", resurface = clock.now + 200)
        create("c", resurface = clock.now + 200)
        clock.now += 10
        create("new")
        assertEquals(listOf("new", "a", "b", "c"), summaries().map { it.id })
        clock.now += 100
        assertEquals(listOf("a", "new", "b", "c"), summaries().map { it.id })
        // Projection reads and recomposition cannot consume a cycle.
        repeat(3) { assertTrue(summaries().first().resurfaced) }
        clock.now += 100
        assertEquals(listOf("b", "c", "a", "new"), summaries().map { it.id })
        repo.opened("b")
        assertEquals(listOf("c", "a", "new", "b"), summaries().map { it.id })
        assertEquals(first.createdAt, repo.getNote("a")!!.createdAt)
        assertEquals(4, summaries().size)
    }
    @Test fun cycleConsumptionSurvivesRepositoryRecreationAndRescheduleStartsFresh() = runBlocking {
        val n = create(resurface = clock.now + 10)
        repo.opened(n.id) // A read before due does not consume a future cycle.
        assertNull(repo.getNote(n.id)!!.resurfaceSeenAt)
        clock.now += 10; repo.opened(n.id)
        repo = RoomNotesRepository(db, clock)
        assertFalse(summaries().single().resurfaced)
        assertNotNull(repo.getNote(n.id)!!.resurfaceSeenAt)
        edit(n.id, TemporalEdit(true, clock.now + 10))
        assertNull(repo.getNote(n.id)!!.resurfaceSeenAt)
        clock.now += 10
        assertTrue(summaries().single().resurfaced)
        edit(n.id, TemporalEdit(true, null))
        assertFalse(summaries().single().resurfaced)
        assertEquals(n.createdAt, repo.getNote(n.id)!!.createdAt)
    }
    @Test fun pastTimeAndElapsedConfirmationRollbackAllChangesAndRetainDraft() = runBlocking {
        val n = create()
        val draft = EditorDraft("s", n.id, "مسودة", "محتوى", clock.now, temporalEdit = TemporalEdit(true, clock.now + 1, clock.now + 1))
        repo.putDraft(draft)
        clock.now += 2
        try { edit(n.id, draft.temporalEdit); fail("Elapsed time") } catch (_: ReminderTimeException) { }
        assertEquals(n, repo.getNote(n.id))
        assertEquals(draft, repo.getDraft("s"))
        try { edit(n.id, TemporalEdit(true, clock.now)); fail("Past resurface") } catch (_: ReminderTimeException) { }
        try { create("empty", unlock = clock.now - 1); fail("Past capsule") } catch (_: ReminderTimeException) { }
    }
    @Test fun lockedModelsContainNoTitleBodyOrPreviewAndEditorWritesAreRejected() = runBlocking {
        val n = create()
        repo.putDraft(EditorDraft("recovery", n.id, "مسودة حساسة", "نص", clock.now))
        edit(n.id, TemporalEdit(sealUntil = clock.now + 100))
        val locked = repo.getNote(n.id)!!
        assertTrue(locked.locked); assertNull(locked.title); assertEquals("", locked.body)
        val card = summaries().single()
        assertTrue(card.locked); assertNull(card.title); assertEquals("", card.preview)
        assertNull(repo.getDraft("recovery"))
        try { repo.save(n.id, "overwrite", "bad", false, "s"); fail("Locked write") } catch (_: CapsuleLockedException) { }
        try { repo.putDraft(EditorDraft("leak", n.id, "leak", "text", clock.now)); fail("Locked draft") } catch (_: CapsuleLockedException) { }
        clock.now += 100
        val unlocked = repo.getNote(n.id)!!
        assertFalse(unlocked.locked); assertEquals(n.title, unlocked.title); assertEquals(n.body, unlocked.body)
        assertEquals(n.createdAt, unlocked.createdAt); assertEquals(n.updatedAt, unlocked.updatedAt)
        assertEquals(n.expressiveTrait, unlocked.expressiveTrait)
        edit(n.id, TemporalEdit(true, clock.now + 20))
    }
    @Test fun emptyCapsuleNeverCreatesNote() = runBlocking {
        try { repo.save("empty", "  ", "\n", true, "s", temporalEdit = TemporalEdit(sealUntil = clock.now + 20)); fail("Empty") }
        catch (_: EmptyNoteException) { }
        assertTrue(summaries().isEmpty())
    }
    @Test fun allTemporalCombinationsRespectReadabilityBeforeConsuming() = runBlocking {
        for ((index, times) in listOf(10L to 20L, 20L to 10L, 10L to 10L).withIndex()) {
            val start = clock.now
            val id = "n$index"
            val n = create(id, if (index % 2 == 0) ExpressiveTrait.GLOW else ExpressiveTrait.ANGER,
                start + times.first, start + times.second)
            clock.now = start + minOf(times.first, times.second)
            repo.opened(id)
            if (times.first < times.second) {
                assertTrue(repo.getNote(id)!!.locked)
                assertNull(repo.getNote(id)!!.resurfaceSeenAt)
                assertTrue(summaries().first { it.id == id }.resurfaced)
            } else if (times.second < times.first) {
                assertFalse(repo.getNote(id)!!.locked)
                assertFalse(summaries().first { it.id == id }.resurfaced)
                assertNull(repo.getNote(id)!!.resurfaceSeenAt)
            }
            clock.now = start + maxOf(times.first, times.second)
            if (times.first != times.second) assertTrue(summaries().first { it.id == id }.resurfaced)
            repo.opened(id)
            assertFalse(summaries().first { it.id == id }.resurfaced)
            assertFalse(repo.getNote(id)!!.locked)
            assertEquals(n.createdAt, repo.getNote(id)!!.createdAt)
        }
    }
    @Test fun sealingKeepsReminderGenerationStatusAndExpressiveTrait() = runBlocking {
        val n = create(trait = ExpressiveTrait.ANGER)
        repo.save(n.id, n.title!!, n.body, false, "s", n.expressiveTrait, ReminderEdit(true, clock.now + 50))
        val reminder = db.reminderDao().get(n.id)
        edit(n.id, TemporalEdit(true, clock.now + 30, clock.now + 100))
        assertEquals(reminder, db.reminderDao().get(n.id))
        assertEquals(ExpressiveTrait.ANGER, summaries().single().expressiveTrait)
        assertNull(summaries().single().title)
        clock.now += 100
        assertEquals(n.body, repo.getNote(n.id)!!.body)
        assertEquals(reminder, db.reminderDao().get(n.id))
    }
    @Test fun flowReevaluatesOpenCapsuleAndDueReturnWithoutReopeningSubscription() = runBlocking {
        create(resurface = clock.now + 60_000, unlock = clock.now + 120_000)
        val values = mutableListOf<NoteSummary>()
        val job = launch { repo.observeNotes().collect { values += it.single() } }
        withTimeout(5000) { while (values.isEmpty()) delay(10) }
        assertTrue(values.last().locked)
        clock.now += 60_000; repo.refreshTime()
        withTimeout(5000) { while (!values.last().resurfaced) delay(10) }
        assertTrue(values.last().locked)
        clock.now += 60_000; repo.refreshTime()
        withTimeout(5000) { while (values.last().locked) delay(10) }
        assertEquals("عنوان سري", values.last().title)
        job.cancelAndJoin()
    }
    @Test fun nearestDeadlineTimerUpdatesAnExistingSubscriptionWithoutLifecycleRefresh() = runBlocking {
        create(resurface = clock.now + 50, unlock = clock.now + 100)
        val values = mutableListOf<NoteSummary>()
        val job = launch { repo.observeNotes().collect { values += it.single() } }
        try {
            withTimeout(5000) { while (values.isEmpty()) delay(1) }
            assertTrue(values.last().locked)
            clock.now += 100
            withTimeout(5000) { while (values.last().locked) delay(1) }
            assertTrue(values.last().resurfaced)
            assertEquals("عنوان سري", values.last().title)
        } finally { job.cancelAndJoin() }
    }
    @Test fun lockedMetadataEditsCannotRevealTextOrLoseReminderAndReturnState() = runBlocking {
        val original = create(unlock = clock.now + 1000)
        repo.scheduleResurface(original.id, clock.now + 10)
        repo.scheduleReminder(original.id, clock.now + 20)
        val reminder = db.reminderDao().get(original.id)!!
        clock.now += 10
        repo.opened(original.id)
        assertTrue(summaries().single().resurfaced)
        assertNull(repo.getNote(original.id)!!.resurfaceSeenAt)
        repo.scheduleResurface(original.id, null)
        assertEquals(reminder, db.reminderDao().get(original.id))
        repo.scheduleReminder(original.id, null)
        assertEquals("CANCELLED", db.reminderDao().get(original.id)!!.status)
        assertTrue(repo.getNote(original.id)!!.locked)
        clock.now += 1000
        assertEquals("عنوان سري", repo.getNote(original.id)!!.title)
        assertEquals(original.createdAt, repo.getNote(original.id)!!.createdAt)
    }
    private class FakeClock(var now: Long = 1_800_000_000_000) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(now)
    }
}
