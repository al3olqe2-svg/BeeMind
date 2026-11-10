package com.beemind.personal

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.beemind.personal.data.RoomNotesRepository
import com.beemind.personal.data.local.*
import com.beemind.personal.domain.*
import com.beemind.personal.notifications.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class ReminderRepositoryTest {
    private lateinit var db: BeeMindDatabase
    private lateinit var repository: RoomNotesRepository
    private lateinit var coordinator: ReminderCoordinator
    private val clock = MutableClock()
    private val scheduler = FakeScheduler()
    private val notifier = FakeNotifier()
    @Before fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BeeMindDatabase::class.java).build()
        coordinator = ReminderCoordinator(db, scheduler, notifier, clock)
        repository = RoomNotesRepository(db, clock) { coordinator.changed(it) }
    }
    @After fun close() { db.close() }
    private suspend fun create(id: String = "a", due: Long = clock.now + 300_000) = repository.save(id, "عنوان", "النص", true, id,
        ExpressiveTrait.GLOW, ReminderEdit(true, due))
    private suspend fun row(id: String = "a") = checkNotNull(db.reminderDao().get(id))
    private suspend fun due(id: String = "a"): ReminderEntity {
        val r = row(id); clock.now = r.dueAt; coordinator.alarm(id, r.generation); return row(id)
    }
    @Test fun savedReminderPersistsOneRowAndPreservesCreationAndMasonryOrder() = runBlocking {
        val first = create()
        clock.now++
        create("b")
        val old = row()
        repository.save("a", "edited", "نص جديد", false, "s", ExpressiveTrait.ANGER)
        assertEquals(old.generation, row().generation)
        assertEquals(first.createdAt, repository.getNote("a")!!.createdAt)
        assertEquals(ExpressiveTrait.ANGER, repository.getNote("a")!!.expressiveTrait)
        assertEquals(listOf("b", "a"), repository.observeNotes().first().map { it.id })
        assertEquals(2, db.reminderDao().active().size)
        assertEquals(row().dueAt, repository.getNote("a")!!.reminder!!.dueAt)
    }
    @Test fun editsInvalidateOldAlarmAndOldActionsAndDeletionCascades() = runBlocking {
        create(); val old = row()
        repository.save("a", "عنوان", "النص", false, "s", ExpressiveTrait.GLOW, ReminderEdit(true, clock.now + 600_000))
        val next = row()
        assertNotEquals(old.generation, next.generation)
        clock.now = old.dueAt
        coordinator.alarm("a", old.generation)
        assertTrue(notifier.shown.isEmpty())
        assertFalse(coordinator.action("a", old.generation, "DONE"))
        assertEquals(1, scheduler.alarms.size)
        repository.delete("a")
        assertNull(db.reminderDao().get("a"))
        assertTrue(scheduler.alarms.isEmpty())
        coordinator.alarm("a", next.generation)
        assertTrue(notifier.shown.isEmpty())
    }
    @Test fun doneIsIdempotentAndKeepsTheWholeNote() = runBlocking {
        val saved = create(); val delivered = due()
        assertEquals("DELIVERED", delivered.delivery)
        assertTrue(coordinator.action("a", delivered.generation, "DONE", true))
        assertFalse(coordinator.action("a", delivered.generation, "DONE", true))
        assertFalse(coordinator.action("a", delivered.generation, "SNOOZE", true))
        coordinator.alarm("a", delivered.generation); coordinator.reconcile()
        assertEquals(1, notifier.shown.size)
        assertEquals("DONE", row().status)
        assertEquals(saved.createdAt, repository.getNote("a")!!.createdAt)
        assertEquals(saved.body, repository.getNote("a")!!.body)
        assertTrue(scheduler.alarms.isEmpty())
        assertTrue(notifier.visible.isEmpty())
    }
    @Test fun snoozeUsesActionTimeCanRepeatAndRejectsDuplicateBroadcast() = runBlocking {
        create(); var delivered = due()
        repeat(3) {
            clock.now += 120_000
            val actionAt = clock.now
            assertTrue(coordinator.action("a", delivered.generation, "SNOOZE", true))
            assertFalse(coordinator.action("a", delivered.generation, "SNOOZE", true))
            assertEquals(actionAt + 600_000, row().dueAt)
            assertEquals("SNOOZED", row().status)
            assertEquals(1, scheduler.alarms.size)
            assertTrue(notifier.visible.isEmpty())
            delivered = due()
        }
        assertEquals(4, notifier.shown.size)
    }
    @Test fun blockedNotificationsAreVisibleAndCatchUpOccursOnceAfterPermissionReturns() = runBlocking {
        create(); notifier.enabled = false
        val delivered = due()
        assertEquals("BLOCKED", delivered.delivery)
        coordinator.reconcile(); assertTrue(notifier.shown.isEmpty())
        notifier.enabled = true
        coordinator.reconcile(); coordinator.reconcile(); coordinator.alarm("a", delivered.generation)
        assertEquals(1, notifier.shown.size)
        assertEquals("DELIVERED", row().delivery)
    }
    @Test fun reconciliationRestoresFutureAlarmsAndExposesSchedulerFailure() = runBlocking {
        create(); scheduler.alarms.clear()
        coordinator.reconcile()
        assertEquals(1, scheduler.alarms.size)
        scheduler.fail = true
        coordinator.reconcile()
        assertEquals("FAILED", row().scheduling)
        scheduler.fail = false; coordinator.reconcile()
        assertEquals("INEXACT", row().scheduling)
    }
    @Test fun overdueBatchIsBoundedAndNeverReplaysOnLaunch() = runBlocking {
        repeat(6) { create("n$it") }
        clock.now += 400_000
        coordinator.reconcile(); coordinator.reconcile()
        assertEquals(3, notifier.shown.size)
        assertEquals(3, db.reminderDao().active().count { it.delivery == "MISSED" })
        create("ancient"); clock.now += 86_700_001
        coordinator.reconcile()
        assertEquals("MISSED", row("ancient").delivery)
        assertEquals(3, notifier.shown.size)
    }
    @Test fun claimCrashIsNotRepeatedAndEarlyAlarmsWaitForSavedInstant() = runBlocking {
        create(); val r = row()
        coordinator.alarm("a", r.generation)
        assertTrue(notifier.shown.isEmpty())
        db.reminderDao().delivery("a", r.generation, "CLAIMED", null)
        clock.now = r.dueAt
        coordinator.reconcile(); coordinator.reconcile()
        assertEquals("MISSED", row().delivery)
        assertTrue(notifier.shown.isEmpty())
    }
    @Test fun draftHasNoAlarmAndFailedSavePreservesPreviousReminderAndDraft() = runBlocking {
        create(); val old = row()
        val draft = EditorDraft("s", "a", "جديد", "نص", clock.now, ExpressiveTrait.ANGER, ReminderEdit(true, clock.now - 1))
        repository.putDraft(draft)
        assertEquals(draft, repository.getDraft("s"))
        assertEquals(old, row())
        try { repository.save("a", draft.title, draft.body, false, "s", draft.expressiveTrait, draft.reminderEdit); fail("Past time") }
        catch (_: ReminderTimeException) { }
        assertEquals(old, row())
        assertEquals(draft, repository.getDraft("s"))
        assertEquals("عنوان", repository.getNote("a")!!.title)
        repository.putDraft(draft.copy(sessionId = "new", noteId = null))
        assertEquals(1, scheduler.alarms.size)
        repository.discardDraft("s"); assertEquals(old, row())
    }
    @Test fun cancelAndNewReminderAfterDoneUseSameNoteWithoutRevivingOldGeneration() = runBlocking {
        val saved = create(); val old = row()
        coordinator.action("a", old.generation, "DONE")
        create() // Existing ID remains one note; intentional new reminder generation.
        val fresh = row()
        assertNotEquals(old.generation, fresh.generation)
        repository.save("a", "عنوان", "النص", false, "s", ExpressiveTrait.GLOW, ReminderEdit(true, null))
        assertEquals("CANCELLED", row().status)
        clock.now = fresh.dueAt; coordinator.alarm("a", fresh.generation); coordinator.reconcile()
        assertTrue(notifier.shown.isEmpty())
        assertTrue(scheduler.alarms.isEmpty())
        assertEquals(saved.createdAt, repository.getNote("a")!!.createdAt)
        assertEquals(1, repository.observeNotes().first().size)
    }
    @Test fun concurrentActionsAcceptOnlyOneGenerationTransition() = runBlocking {
        create(); val r = due()
        val results = coroutineScope { (1..12).map {
            async(Dispatchers.Default) { coordinator.action("a", r.generation, "SNOOZE", true) }
        }.awaitAll() }
        assertEquals(1, results.count { it })
        assertEquals(1, scheduler.alarms.size)
        assertEquals(r.dueAt + 600_000, row().dueAt)
    }

    @Test fun alarmAndResumeRecoveryHaveDistinctDurableDeliveryEvidence() = runBlocking {
        create(); due()
        val key = com.beemind.personal.notifications.ReminderDiagnostics.key("a")
        assertTrue(db.foundationDao().observeValue(key).first()!!.contains("|ALARM_DELIVERED|"))
        create("recovery")
        clock.now += 400_000
        coordinator.reconcile()
        val recovered = db.foundationDao().observeValue(com.beemind.personal.notifications.ReminderDiagnostics.key("recovery")).first()!!
        assertTrue(recovered.contains("|RECOVERY_DELIVERED|"))
        repository.delete("a")
        assertNull(db.foundationDao().observeValue(key).first())
    }

    private class MutableClock(var now: Long = 1_800_000_000_000) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(now)
    }
    private class FakeScheduler : ReminderScheduler {
        val alarms = mutableMapOf<String, ReminderEntity>(); var fail = false
        override fun schedule(reminder: ReminderEntity): String { if (fail) error("Injected"); alarms[reminder.noteId] = reminder; return "INEXACT" }
        override fun cancel(reminder: ReminderEntity) { alarms.remove(reminder.noteId) }
    }
    private class FakeNotifier : ReminderNotifier {
        var enabled = true
        val shown = mutableListOf<String>(); val visible = mutableSetOf<String>()
        override fun allowed() = enabled
        override fun show(reminder: ReminderEntity) { shown += reminder.generation; visible += reminder.noteId }
        override fun cancel(noteId: String) { visible -= noteId }
    }
}
