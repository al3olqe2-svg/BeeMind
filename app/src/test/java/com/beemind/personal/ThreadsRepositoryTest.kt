package com.beemind.personal

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.beemind.personal.data.RoomNotesRepository
import com.beemind.personal.data.local.*
import com.beemind.personal.domain.*
import com.beemind.personal.ui.notes.matchesThreadSearch
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class ThreadsRepositoryTest {
    private lateinit var db: BeeMindDatabase
    private lateinit var repo: RoomNotesRepository
    private val clock = ThreadClock()
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), BeeMindDatabase::class.java)
            .addCallback(BeeMindDatabase.LINK_CONSTRAINTS).build()
        repo = RoomNotesRepository(db, clock)
    }
    @After fun close() { db.close() }
    private suspend fun create(id: String, trait: ExpressiveTrait = ExpressiveTrait.NONE, capsule: Boolean = false) =
        repo.save(id, "عنوان $id سري", "يوميات عربية 🌈\n".repeat(3000), true, id, trait,
            ReminderEdit(true, clock.now + 1000), TemporalEdit(true, clock.now + 100, if (capsule) clock.now + 200 else null))
    private suspend fun linked(id: String) = repo.observeThreadNotes(id, true).first()

    @Test fun canonicalEdgesAreMutualUniqueAndCannotBeSelfOrOrphaned() = runBlocking {
        create("a"); create("b")
        coroutineScope { repeat(20) { i -> launch(Dispatchers.IO) { repo.setLinked(if (i % 2 == 0) "a" else "b", if (i % 2 == 0) "b" else "a", true) } } }
        assertEquals(listOf("b"), linked("a").map { it.note.id })
        assertEquals(listOf("a"), linked("b").map { it.note.id })
        assertTrue(linked("a").single().linked)
        try { repo.setLinked("a", "a", true); fail("Self edge") } catch (_: IllegalArgumentException) { }
        try { repo.setLinked("a", "missing", true); fail("Orphan") } catch (_: NoteMissingException) { }
        val sql = db.openHelper.writableDatabase
        for (pair in listOf("'a','a'", "'b','a'", "'a','missing'")) {
            try { sql.execSQL("INSERT INTO note_links VALUES ($pair)"); fail("Invalid raw edge $pair") } catch (_: android.database.SQLException) { }
        }
        try { sql.execSQL("UPDATE note_links SET lowId = highId"); fail("Invalid update") } catch (_: android.database.SQLException) { }
        sql.query("SELECT count(*) FROM note_links").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
        repo.setLinked("b", "a", false); assertTrue(linked("a").isEmpty()); assertTrue(linked("b").isEmpty())
        repo.setLinked("b", "a", true); assertEquals(1, linked("a").size)
    }

    @Test fun cyclesDeletionAndDraftsLeaveOtherTraitsAndTextIntact() = runBlocking {
        val a = create("a", ExpressiveTrait.GLOW)
        val b = create("b", ExpressiveTrait.ANGER)
        create("c")
        repo.putDraft(EditorDraft("recovery", "b", "مسودة", "تعديل لم يُحفظ", clock.now, ExpressiveTrait.ANGER))
        val reminder = db.reminderDao().get("b")
        repo.setLinked("a", "b", true); repo.setLinked("b", "c", true); repo.setLinked("c", "a", true)
        repeat(50) { for (id in listOf("a", "b", "c")) assertEquals(2, linked(id).size) }
        repo.delete("a")
        assertEquals(listOf("c"), linked("b").map { it.note.id })
        assertEquals(listOf("b"), linked("c").map { it.note.id })
        assertEquals(b, repo.getNote("b")); assertEquals(reminder, db.reminderDao().get("b"))
        assertEquals("تعديل لم يُحفظ", repo.getDraft("recovery")!!.body)
        assertNull(db.reminderDao().get(a.id))
        db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }

    @Test fun boundedCapsuleProjectionsSearchAndResurfaceShareExistingPrivacyRules() = runBlocking {
        create("a", ExpressiveTrait.GLOW); create("b", ExpressiveTrait.ANGER, capsule = true)
        val reminder = db.reminderDao().get("b")
        val callbacks = mutableListOf<ReminderEntity?>()
        repo = RoomNotesRepository(db, clock) { callbacks += it }
        clock.now += 100
        repo.setLinked("a", "b", true)
        val locked = linked("a").single().note
        assertTrue(locked.locked); assertNull(locked.title); assertEquals("", locked.preview)
        assertFalse(matchesThreadSearch(locked, "سري")); assertFalse(matchesThreadSearch(locked, "يوميات"))
        assertTrue(matchesThreadSearch(locked, "كبسولة"))
        assertNull(repo.getNote("b")!!.resurfaceSeenAt)
        repo.opened("b"); assertNull(repo.getNote("b")!!.resurfaceSeenAt)
        assertTrue(repo.observeThreadNotes("b", false).first().none { it.note.id == "b" })
        clock.now += 100; repo.refreshTime()
        val readable = linked("a").single().note
        assertFalse(readable.locked); assertTrue(readable.preview.length <= 420)
        assertNull(repo.getNote("b")!!.resurfaceSeenAt) // Merely displayed, never consumed.
        repo.opened("b"); assertNotNull(repo.getNote("b")!!.resurfaceSeenAt)
        repo.setLinked("a", "b", false)
        assertEquals(reminder, db.reminderDao().get("b")); assertTrue(callbacks.isEmpty())
    }

    @Test fun flowUpdatesBothEndsAndUnlocksWithoutAnotherSubscription() = runBlocking {
        create("a"); create("b", capsule = true)
        val values = mutableListOf<List<ThreadNote>>()
        val job = launch { repo.observeThreadNotes("a", true).collect { values += it } }
        try {
            withTimeout(5000) { while (values.isEmpty()) delay(10) }
            repo.setLinked("b", "a", true)
            withTimeout(5000) { while (values.last().isEmpty()) delay(10) }
            assertTrue(values.last().single().note.locked)
            clock.now += 200; repo.refreshTime()
            withTimeout(5000) { while (values.last().single().note.locked) delay(10) }
            repo.setLinked("a", "b", false)
            withTimeout(5000) { while (values.last().isNotEmpty()) delay(10) }
        } finally { job.cancelAndJoin() }
    }

    @Test fun hundredsOfLongNotesAndIndexedEdgesUseBoundedSingleProjection() = runBlocking {
        val start = System.nanoTime()
        for (i in 0 until 200) {
            create("n$i")
            if (i > 0) repo.setLinked("n0", "n$i", true)
        }
        val readStart = System.nanoTime()
        val list = linked("n0")
        assertEquals(199, list.size); assertTrue(list.all { it.note.preview.length <= 420 && it.note.title.orEmpty().length <= 160 })
        val readMs = (System.nanoTime() - readStart) / 1_000_000
        println("Threads synthetic: 200 long notes / 199 links, bounded query ${readMs}ms, fixture + query ${(System.nanoTime() - start) / 1_000_000}ms; not a phone frame benchmark")
        val plan = db.openHelper.writableDatabase.query("EXPLAIN QUERY PLAN SELECT highId FROM note_links WHERE lowId = 'n0' UNION ALL SELECT lowId FROM note_links WHERE highId = 'n0'")
        plan.use { val lines = mutableListOf<String>(); while (it.moveToNext()) lines += it.getString(3); assertTrue(lines.count { line -> line.contains("INDEX") } >= 2) }
    }
    private class ThreadClock(var now: Long = 1800000000000L) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(now)
    }
}
