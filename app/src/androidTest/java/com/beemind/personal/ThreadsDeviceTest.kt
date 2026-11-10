package com.beemind.personal

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.beemind.personal.data.RoomNotesRepository
import com.beemind.personal.data.local.BeeMindDatabase
import com.beemind.personal.domain.*
import java.time.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Native SQLite, disposable Stage 4 fixture, never the owner's database. */
@RunWith(AndroidJUnit4::class)
class ThreadsDeviceTest {
    @Test fun stageFiveMigrationRestartCyclesAndCascadeKeepAllSixTraitsSafe() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "stage5-isolated.db"
        context.deleteDatabase(name)
        val sqlite = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        try {
            val schema = org.json.JSONObject(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets.open("com.beemind.personal.data.local.BeeMindDatabase/5.json")
                .bufferedReader().use { it.readText() }).getJSONObject("database")
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                sqlite.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
                val indices = e.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) sqlite.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) sqlite.execSQL(setup.getString(i))
            sqlite.execSQL("INSERT INTO notes VALUES ('a', 'فكرة', 'نص عربي', 123, 456, 'GLOW', 1800000000010, NULL, NULL)")
            sqlite.execSQL("INSERT INTO notes VALUES ('b', 'عنوان سري', 'رسالة خاصة', 124, 457, 'ANGER', 1800000000010, NULL, 1800000000020)")
            sqlite.execSQL("INSERT INTO notes VALUES ('c', NULL, 'فكرة ثالثة', 125, 458, 'NONE', NULL, NULL, NULL)")
            sqlite.execSQL("INSERT INTO reminders VALUES ('b', 1800000000050, 'SNOOZED', 'stable', 'PENDING', NULL, 'EXACT')")
            sqlite.execSQL("INSERT INTO editor_drafts VALUES ('draft', 'a', 'مسودة', 'تعديل محفوظ', 789, 'GLOW', 1, 1800000000030, 1, 1800000000040, NULL)")
            sqlite.version = 5
        } finally { sqlite.close() }
        var db = Room.databaseBuilder(context, BeeMindDatabase::class.java, name).addMigrations(BeeMindDatabase.MIGRATION_5_6).build()
        val clock = DeviceClock()
        try {
            var repo = RoomNotesRepository(db, clock)
            val a = repo.getNote("a")!!
            val draft = repo.getDraft("draft")!!
            val reminder = db.reminderDao().get("b")!!
            repo.setLinked("b", "a", true); repo.setLinked("a", "b", true)
            repo.setLinked("b", "c", true); repo.setLinked("c", "a", true)
            try { repo.setLinked("a", "a", true); fail("Self") } catch (_: IllegalArgumentException) { }
            try { db.openHelper.writableDatabase.execSQL("INSERT INTO note_links VALUES ('c','b')"); fail("Reverse") } catch (_: android.database.SQLException) { }
            db.close()
            db = Room.databaseBuilder(context, BeeMindDatabase::class.java, name).build()
            repo = RoomNotesRepository(db, clock)
            assertEquals(2, repo.observeThreadNotes("a", true).first().size)
            val locked = repo.observeThreadNotes("a", true).first().single { it.note.id == "b" }.note
            assertTrue(locked.locked); assertNull(locked.title); assertEquals("", locked.preview)
            clock.now += 10; repo.opened("b"); assertNull(repo.getNote("b")!!.resurfaceSeenAt)
            clock.now += 10; repo.refreshTime()
            assertEquals("رسالة خاصة", repo.getNote("b")!!.body)
            assertNull(repo.getNote("b")!!.resurfaceSeenAt)
            repo.opened("b"); assertNotNull(repo.getNote("b")!!.resurfaceSeenAt)
            repo.setLinked("a", "b", false); repo.setLinked("b", "a", true)
            repeat(20) { for (id in listOf("a", "b", "c")) assertEquals(2, repo.observeThreadNotes(id, true).first().size) }
            assertEquals(reminder, db.reminderDao().get("b")); assertEquals(draft, repo.getDraft("draft"))
            assertEquals(a, repo.getNote("a")); assertEquals(ExpressiveTrait.ANGER, repo.getNote("b")!!.expressiveTrait)
            repo.delete("b")
            assertNull(db.reminderDao().get("b")); assertEquals(listOf("c"), repo.observeThreadNotes("a", true).first().map { it.note.id })
            assertEquals(a, repo.getNote("a")); assertEquals(draft, repo.getDraft("draft"))
            assertEquals(6, db.openHelper.writableDatabase.version)
            db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            db.openHelper.writableDatabase.query("PRAGMA integrity_check").use { assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0)) }
        } finally { db.close(); context.deleteDatabase(name) }
    }
    private class DeviceClock(var now: Long = 1800000000000L) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(now)
    }
}
