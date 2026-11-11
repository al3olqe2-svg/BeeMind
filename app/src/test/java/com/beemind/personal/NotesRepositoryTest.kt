package com.beemind.personal

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.beemind.personal.data.RoomNotesRepository
import com.beemind.personal.data.local.BeeMindDatabase
import com.beemind.personal.domain.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class NotesRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: BeeMindDatabase
    private lateinit var repository: RoomNotesRepository
    private lateinit var name: String
    private val clock = MutableClock()

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        name = "notes-${UUID.randomUUID()}.db"
        open()
    }
    private fun open() {
        database = Room.databaseBuilder(context, BeeMindDatabase::class.java, name)
            .addMigrations(BeeMindDatabase.MIGRATION_1_2, BeeMindDatabase.MIGRATION_2_3, BeeMindDatabase.MIGRATION_3_4).build()
        repository = RoomNotesRepository(database, clock)
    }
    @After fun close() { database.close(); context.deleteDatabase(name) }

    @Test fun titleOnlyAndBodyOnlyAreRealPersistentNotes() = runBlocking {
        val a = repository.save("a", "عنوان فقط", "", true, "draft-a")
        val b = repository.save("b", "", "نص فقط", true, "draft-b")
        assertEquals("عنوان فقط", a.title)
        assertNull(b.title)
        database.close()
        open()
        assertEquals(a, repository.getNote("a"))
        assertEquals(b, repository.getNote("b"))
    }

    @Test fun newDraftRecoveryIsNotMaskedByANewerExistingNoteEdit() = runBlocking {
        repository.save("existing", "", "نص محفوظ", true, "created")
        repository.putDraft(EditorDraft("unfinished-new", null, "", "مسودة جديدة", 10))
        repository.putDraft(EditorDraft("empty-existing-edit", "existing", "", "", 20))
        assertEquals(DraftSummary("unfinished-new", null), repository.observeLatestDraft().first())
        assertEquals(DraftSummary("empty-existing-edit", "existing"), repository.latestDraftForNote("existing"))
        assertEquals("مسودة جديدة", repository.getDraft("unfinished-new")!!.body)
        assertEquals("", repository.getDraft("empty-existing-edit")!!.body)
        assertEquals("نص محفوظ", repository.getNote("existing")!!.body)
    }

    @Test fun blankNotesAreRejectedWithoutRows() = runBlocking {
        for ((title, body) in listOf("" to "", " \n\t" to "\u00a0 \n")) {
            try { repository.save("empty", title, body, true, "draft"); fail("Blank note was saved") }
            catch (_: EmptyNoteException) { }
        }
        assertTrue(repository.observeNotes().first().isEmpty())
    }

    @Test fun editKeepsCreatedAtAndNoOpSaveKeepsUpdatedAt() = runBlocking {
        val original = repository.save("a", "عنوان", "النص الأول", true, "s")
        clock.now += 1_000
        val edited = repository.save("a", "عنوان", "نص معدل", false, "s")
        assertEquals(original.createdAt, edited.createdAt)
        assertTrue(edited.updatedAt > original.updatedAt)
        clock.now += 5_000
        assertEquals(edited, repository.save("a", "عنوان", "نص معدل", false, "s"))
        // Even after a device clock rollback, an actual edit has a later updatedAt.
        clock.now = 0
        assertTrue(repository.save("a", "عنوان", "تعديل آخر", false, "s").updatedAt > edited.updatedAt)
        assertEquals(1, repository.observeNotes().first().size)
    }

    @Test fun concurrentSaveRetriesWithOneStableIdNeverDuplicateCreation() = runBlocking {
        coroutineScope {
            repeat(12) { launch(Dispatchers.Default) { repository.save("same-id", "متكرر", "مرة واحدة", true, "session") } }
        }
        assertEquals(listOf("same-id"), repository.observeNotes().first().map { it.id })
        val note = repository.getNote("same-id")!!
        assertEquals(note.createdAt, note.updatedAt)
    }

    @Test fun flowEmitsCreationAndDeletionAndDeleteClearsRelatedDrafts() = runBlocking {
        val events = Channel<List<NoteSummary>>(Channel.UNLIMITED)
        val collector = launch(Dispatchers.Default) { repository.observeNotes().collect { events.send(it) } }
        try {
            assertTrue(withTimeout(10_000) { events.receive() }.isEmpty())
            repository.save("reactive", "تفاعلية", "", true, "s")
            assertEquals("reactive", withTimeout(10_000) { events.receive() }.single().id)
            repository.save("reactive", "تفاعلية", "", false, "s", ExpressiveTrait.GLOW)
            assertEquals(ExpressiveTrait.GLOW, withTimeout(10_000) { events.receive() }.single().expressiveTrait)
            assertEquals(ExpressiveTrait.GLOW, repository.observeNote("reactive").first()!!.expressiveTrait)
            repository.putDraft(EditorDraft("edit", "reactive", "تغيير", "نص", 10))
            repository.delete("reactive")
            assertTrue(withTimeout(10_000) { events.receive() }.isEmpty())
            assertNull(repository.getNote("reactive"))
            assertNull(repository.getDraft("edit"))
            try { repository.save("reactive", "", "قديم", false, "edit"); fail("Deleted note resurrected") }
            catch (_: NoteMissingException) { }
        } finally { collector.cancelAndJoin(); events.close() }
    }

    @Test fun creationOrderIsStableOnTiesAndEditingDoesNotPromote() = runBlocking {
        clock.now = 1_000
        repository.save("b", "ب", "", true, "b")
        repository.save("a", "أ", "", true, "a")
        clock.now = 2_000
        repository.save("c", "ج", "", true, "c")
        clock.now = 3_000
        repository.save("a", "تعديل", "", false, "a")
        assertEquals(listOf("c", "a", "b"), repository.observeNotes().first().map { it.id })
    }

    @Test fun longArabicTextAndDraftRoundTripWithoutTruncation() = runBlocking {
        val text = "يومياتي: العربية، الترقيم؟ سطر جديد 🌈\n".repeat(6_000)
        repository.putDraft(EditorDraft("session", null, "يوم طويل", text, 1))
        database.close(); open()
        assertEquals(text, repository.getDraft("session")!!.body)
        repository.save("arabic", "يوم طويل", text, true, "session")
        assertNull(repository.getDraft("session"))
        database.close(); open()
        assertEquals(text, repository.getNote("arabic")!!.body)
        val preview = repository.observeNotes().first().single().preview
        assertTrue(preview.codePointCount(0, preview.length) <= 420)
    }

    @Test fun textBeyondTheDefaultAndroidCursorWindowIsReadInFull() = runBlocking {
        val text = "يوميات طويلة جدًا 🌈\n".repeat(100_000) + "\u0000نهاية النص"
        repository.putDraft(EditorDraft("huge-draft", null, "نص كبير", text, 1))
        database.close(); open()
        assertEquals(text, repository.getDraft("huge-draft")!!.body)
        repository.save("huge", "نص يتجاوز حجم نافذة SQLite", text, true, "huge-draft")
        database.close(); open()
        assertEquals(text, repository.getNote("huge")!!.body)
    }

    @Test fun expressiveTransitionsPersistWithoutChangingIdentityTextOrOrder() = runBlocking {
        val original = repository.save("traits", "عنوان عربي", "محتوى 🌈", true, "s")
        assertEquals(ExpressiveTrait.NONE, original.expressiveTrait)
        repository.save("newer", "أحدث", "", true, "n", ExpressiveTrait.ANGER)
        for (trait in listOf(ExpressiveTrait.GLOW, ExpressiveTrait.ANGER, ExpressiveTrait.GLOW, ExpressiveTrait.NONE)) {
            val saved = repository.save("traits", "عنوان عربي", "محتوى 🌈", false, "s", trait)
            assertEquals(original.id, saved.id)
            assertEquals(original.createdAt, saved.createdAt)
            assertEquals(original.title, saved.title)
            assertEquals(original.body, saved.body)
            database.close(); open()
            assertEquals(saved, repository.getNote("traits"))
            assertEquals(trait, repository.observeNotes().first().single { it.id == "traits" }.expressiveTrait)
            assertEquals(listOf("newer", "traits"), repository.observeNotes().first().map { it.id })
        }
        for (trait in ExpressiveTrait.entries) {
            val saved = repository.save(trait.name, "سمة", "", true, "s", trait)
            assertEquals(trait, repository.getNote(saved.id)!!.expressiveTrait)
        }
        assertEquals(3, ExpressiveTrait.entries.size)
    }

    @Test fun expressiveDraftSurvivesDatabaseReopenAndDeletionClearsIt() = runBlocking {
        repository.save("a", "أصل", "نص", true, "s", ExpressiveTrait.GLOW)
        val draft = EditorDraft("edit", "a", "تعديل", "نص محفوظ كمسودة", 42, ExpressiveTrait.ANGER)
        repository.putDraft(draft)
        database.close(); open()
        assertEquals(draft, repository.getDraft("edit"))
        assertEquals(ExpressiveTrait.GLOW, repository.getNote("a")!!.expressiveTrait)
        repository.delete("a")
        assertNull(repository.getDraft("edit"))
    }

    @Test fun reminderAndDraftEditSurviveClosingAndReopeningRoom() = runBlocking {
        val saved = repository.save("reminder", "عنوان", "نص", true, "s", ExpressiveTrait.GLOW,
            ReminderEdit(true, clock.millis() + 300_000))
        repository.putDraft(EditorDraft("draft-reminder", "reminder", "تعديل", "مسودة", 42,
            ExpressiveTrait.ANGER, ReminderEdit(true, clock.millis() + 600_000)))
        database.close(); open()
        assertEquals(saved, repository.getNote("reminder"))
        assertEquals(saved.reminder!!.dueAt, repository.observeNotes().first().single().reminderDueAt)
        assertEquals(ReminderEdit(true, clock.millis() + 600_000), repository.getDraft("draft-reminder")!!.reminderEdit)
        assertEquals(saved.reminder, repository.getNote("reminder")!!.reminder)
    }

    private class MutableClock(var now: Long = 1_000_000) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(now)
        override fun millis(): Long = now
    }
}
