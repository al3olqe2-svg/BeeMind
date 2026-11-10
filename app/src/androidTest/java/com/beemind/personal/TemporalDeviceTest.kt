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

/** Disposable isolated database; never touches the owner's beemind.db. */
@RunWith(AndroidJUnit4::class)
class TemporalDeviceTest {
    @Test fun realSqliteMigrationAndRestartPreservePrivacyAndTemporalCycles() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "stage4-isolated.db"
        context.deleteDatabase(name)
        val sqlite = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        try {
            val schema = org.json.JSONObject(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets.open("com.beemind.personal.data.local.BeeMindDatabase/4.json")
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
            sqlite.execSQL("INSERT INTO notes VALUES ('old', 'عنوان سري', 'رسالة خاصة', 123, 456, 'GLOW')")
            sqlite.execSQL("INSERT INTO reminders VALUES ('old', 1800000000050, 'SNOOZED', 'stable', 'PENDING', NULL, 'EXACT')")
            sqlite.version = 4
        } finally { sqlite.close() }
        var db = Room.databaseBuilder(context, BeeMindDatabase::class.java, name).addMigrations(BeeMindDatabase.MIGRATION_4_5).build()
        val clock = DeviceClock()
        try {
            var repo = RoomNotesRepository(db, clock)
            val original = repo.getNote("old")!!
            repo.save("old", original.title!!, original.body, false, "s", original.expressiveTrait,
                temporalEdit = TemporalEdit(true, clock.now + 10, clock.now + 20))
            assertNull(repo.getNote("old")!!.title)
            assertEquals("", repo.observeNotes().first().single().preview)
            clock.now += 10; repo.opened("old")
            assertNull(repo.getNote("old")!!.resurfaceSeenAt)
            db.close()
            db = Room.databaseBuilder(context, BeeMindDatabase::class.java, name).build()
            repo = RoomNotesRepository(db, clock)
            assertTrue(repo.getNote("old")!!.locked)
            assertTrue(repo.observeNotes().first().single().resurfaced)
            clock.now += 10
            assertEquals(original.body, repo.getNote("old")!!.body)
            assertEquals(123L, repo.getNote("old")!!.createdAt)
            repo.opened("old")
            assertFalse(repo.observeNotes().first().single().resurfaced)
            assertEquals("stable", db.reminderDao().get("old")!!.generation)
            assertEquals("SNOOZED", db.reminderDao().get("old")!!.status)
            assertEquals(ExpressiveTrait.GLOW, repo.getNote("old")!!.expressiveTrait)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    private class DeviceClock(var now: Long = 1800000000000L) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(now)
    }
}
