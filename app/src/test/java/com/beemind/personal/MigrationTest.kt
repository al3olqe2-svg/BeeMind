package com.beemind.personal

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.beemind.personal.data.local.BeeMindDatabase
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class MigrationTest {
    @Test fun actualVersionOneSchemaMigratesAndPreservesItsData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration.db"
        context.deleteDatabase(name)
        // Use the committed Stage 0 schema, including its identity hash, rather than duplicating migration SQL.
        val schema = JSONObject(File("schemas/com.beemind.personal.data.local.BeeMindDatabase/1.json").readText()).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                    }
                    val queries = schema.getJSONArray("setupQueries")
                    for (i in 0 until queries.length()) db.execSQL(queries.getString(i))
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected upgrade")
            }).build())
        val preserved = "بيانات المرحلة صفر 🌈"
        helper.writableDatabase.execSQL("INSERT INTO app_metadata (`key`, value) VALUES (?, ?)", arrayOf("preserve", preserved))
        helper.close()
        val room = Room.databaseBuilder(context, BeeMindDatabase::class.java, name)
            .addMigrations(BeeMindDatabase.MIGRATION_1_2, BeeMindDatabase.MIGRATION_2_3, BeeMindDatabase.MIGRATION_3_4, BeeMindDatabase.MIGRATION_4_5, BeeMindDatabase.MIGRATION_5_6).build()
        try {
            val db = room.openHelper.writableDatabase // Room validates the complete migrated schema here.
            assertEquals(6, db.version)
            db.query("SELECT value FROM app_metadata WHERE `key` = 'preserve'").use {
                assertTrue(it.moveToFirst()); assertEquals(preserved, it.getString(0))
            }
            assertNull(room.notesDao().getHeader("absent"))
            db.query("PRAGMA integrity_check").use { assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0)) }
        } finally { room.close(); context.deleteDatabase(name) }
    }
    @Test fun actualVersionTwoNotesAndRecoveryDraftsMigrateWithoutLoss() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-v2.db"
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/com.beemind.personal.data.local.BeeMindDatabase/2.json").readText()).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                        val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                        for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                    }
                    val queries = schema.getJSONArray("setupQueries")
                    for (i in 0 until queries.length()) db.execSQL(queries.getString(i))
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected upgrade")
            }).build())
        val text = "يومياتي العربية 🌈\n".repeat(2000)
        helper.writableDatabase.execSQL("INSERT INTO notes VALUES (?, ?, ?, ?, ?)", arrayOf<Any>("old", "عنوان", text, 123L, 456L))
        helper.writableDatabase.execSQL("INSERT INTO editor_drafts VALUES (?, ?, ?, ?, ?)", arrayOf<Any>("draft", "old", "مسودة", text, 789L))
        helper.close()
        val room = Room.databaseBuilder(context, BeeMindDatabase::class.java, name)
            .addMigrations(BeeMindDatabase.MIGRATION_2_3, BeeMindDatabase.MIGRATION_3_4, BeeMindDatabase.MIGRATION_4_5, BeeMindDatabase.MIGRATION_5_6).build()
        try {
            val repo = com.beemind.personal.data.RoomNotesRepository(room)
            val note = requireNotNull(repo.getNote("old"))
            assertEquals(com.beemind.personal.domain.Note("old", "عنوان", text, 123L, 456L), note)
            assertEquals(com.beemind.personal.domain.EditorDraft("draft", "old", "مسودة", text, 789L), repo.getDraft("draft"))
            assertEquals(6, room.openHelper.writableDatabase.version)
            room.openHelper.writableDatabase.query("PRAGMA integrity_check").use {
                assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0))
            }
        } finally { room.close(); context.deleteDatabase(name) }
    }
    @Test fun actualVersionThreeExpressiveNotesAndDraftsMigrateWithoutLoss() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-v3.db"
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/com.beemind.personal.data.local.BeeMindDatabase/3.json").readText()).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                        val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                        for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                    }
                    val queries = schema.getJSONArray("setupQueries")
                    for (i in 0 until queries.length()) db.execSQL(queries.getString(i))
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected upgrade")
            }).build())
        val text = "يومياتي العربية 🌈\n".repeat(2000)
        helper.writableDatabase.execSQL("INSERT INTO notes VALUES (?, ?, ?, ?, ?, ?)", arrayOf<Any>("old", "عنوان", text, 123L, 456L, "GLOW"))
        helper.writableDatabase.execSQL("INSERT INTO editor_drafts VALUES (?, ?, ?, ?, ?, ?)", arrayOf<Any>("draft", "old", "مسودة", text, 789L, "ANGER"))
        helper.close()
        val room = Room.databaseBuilder(context, BeeMindDatabase::class.java, name)
            .addMigrations(BeeMindDatabase.MIGRATION_2_3, BeeMindDatabase.MIGRATION_3_4, BeeMindDatabase.MIGRATION_4_5, BeeMindDatabase.MIGRATION_5_6).build()
        try {
            val repo = com.beemind.personal.data.RoomNotesRepository(room)
            val note = requireNotNull(repo.getNote("old"))
            assertEquals(com.beemind.personal.domain.Note("old", "عنوان", text, 123L, 456L, com.beemind.personal.domain.ExpressiveTrait.GLOW), note)
            assertEquals(com.beemind.personal.domain.EditorDraft("draft", "old", "مسودة", text, 789L, com.beemind.personal.domain.ExpressiveTrait.ANGER), repo.getDraft("draft"))
            assertEquals(6, room.openHelper.writableDatabase.version)
            room.openHelper.writableDatabase.query("PRAGMA integrity_check").use {
                assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0))
            }
        } finally { room.close(); context.deleteDatabase(name) }
    }

    @Test fun actualVersionFourNotesTraitsRemindersAndDraftsMigrateWithoutLoss() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-v4.db"
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/com.beemind.personal.data.local.BeeMindDatabase/4.json").readText()).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val e = entities.getJSONObject(i)
                        db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
                        val indices = e.optJSONArray("indices") ?: org.json.JSONArray()
                        for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
                    }
                    val queries = schema.getJSONArray("setupQueries")
                    for (i in 0 until queries.length()) db.execSQL(queries.getString(i))
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected upgrade")
            }).build())
        val sqlite = helper.writableDatabase
        listOf("NONE", "GLOW", "ANGER").forEachIndexed { i, trait ->
            sqlite.execSQL("INSERT INTO notes VALUES (?, ?, ?, ?, ?, ?)", arrayOf<Any>("n$i", "عنوان $i", "نص $i 🌈", 123L + i, 456L + i, trait))
            sqlite.execSQL("INSERT INTO reminders VALUES (?, ?, ?, ?, ?, ?, ?)", arrayOf<Any>("n$i", 1800000000000L + i, listOf("SCHEDULED", "SNOOZED", "DONE")[i], "generation$i", "DELIVERED", 789L, "EXACT"))
        }
        sqlite.execSQL("INSERT INTO editor_drafts VALUES (?, ?, ?, ?, ?, ?, ?, ?)", arrayOf<Any>("draft", "n1", "مسودة", "تعديل محفوظ", 900L, "ANGER", 1, 1800000001000L))
        helper.close()
        val room = Room.databaseBuilder(context, BeeMindDatabase::class.java, name).addMigrations(BeeMindDatabase.MIGRATION_4_5, BeeMindDatabase.MIGRATION_5_6).build()
        try {
            val repo = com.beemind.personal.data.RoomNotesRepository(room)
            listOf("NONE", "GLOW", "ANGER").forEachIndexed { i, trait ->
                val n = repo.getNote("n$i")!!
                assertEquals("عنوان $i", n.title); assertEquals("نص $i 🌈", n.body)
                assertEquals(123L + i, n.createdAt); assertEquals(456L + i, n.updatedAt)
                assertEquals(trait, n.expressiveTrait.name)
                assertNull(n.resurfaceAt); assertNull(n.unlockAt); assertFalse(n.locked)
                val r = room.reminderDao().get("n$i")!!
                assertEquals(listOf("SCHEDULED", "SNOOZED", "DONE")[i], r.status)
                assertEquals("generation$i", r.generation); assertEquals("EXACT", r.scheduling)
                assertEquals(1800000000000L + i, r.dueAt); assertEquals("DELIVERED", r.delivery)
                assertEquals(789L, r.deliveredAt)
            }
            val draft = repo.getDraft("draft")!!
            assertEquals("تعديل محفوظ", draft.body)
            assertEquals(com.beemind.personal.domain.ReminderEdit(true, 1800000001000L), draft.reminderEdit)
            assertEquals(com.beemind.personal.domain.TemporalEdit(), draft.temporalEdit)
            assertEquals(6, room.openHelper.writableDatabase.version)
            room.openHelper.writableDatabase.query("PRAGMA integrity_check").use { assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0)) }
            room.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { room.close(); context.deleteDatabase(name) }
    }

    @Test fun actualVersionFiveMigrationPreservesEveryExistingColumnAndAddsOnlyEdges() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-v5.db"
        context.deleteDatabase(name)
        val schema = JSONObject(File("schemas/com.beemind.personal.data.local.BeeMindDatabase/5.json").readText()).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val e = entities.getJSONObject(i)
                        db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
                        val indices = e.optJSONArray("indices") ?: org.json.JSONArray()
                        for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
                    }
                    val queries = schema.getJSONArray("setupQueries")
                    for (i in 0 until queries.length()) db.execSQL(queries.getString(i))
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected upgrade")
            }).build())
        val old = helper.writableDatabase
        for ((i, trait) in listOf("NONE", "GLOW", "ANGER").withIndex()) {
            old.execSQL("INSERT INTO notes VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", arrayOf<Any?>("n$i", "عنوان $i", "يوميات 🌈\n".repeat(4000), 123L + i, 456L + i, trait, 1800000000010L, if (i == 1) 1800000000011L else null, if (i == 2) 1800000000020L else null))
            old.execSQL("INSERT INTO reminders VALUES (?, ?, ?, ?, ?, ?, ?)", arrayOf<Any?>("n$i", 1800000000050L, listOf("SCHEDULED", "SNOOZED", "DONE")[i], "generation$i", "DELIVERED", 789L, "EXACT"))
            old.execSQL("INSERT INTO editor_drafts VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", arrayOf<Any?>("d$i", "n$i", "مسودة", "نص طويل".repeat(3000), 900L, trait, 1, 1800000000030L, 1, 1800000000040L, 1800000000050L))
        }
        fun snapshot(db: SupportSQLiteDatabase, table: String): List<List<String?>> = db.query("SELECT * FROM $table ORDER BY 1").use { cursor ->
            buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).map { if (cursor.isNull(it)) null else cursor.getString(it) }) }
        }
        val before = listOf("notes", "reminders", "editor_drafts").associateWith { snapshot(old, it) }
        helper.close()
        var room = Room.databaseBuilder(context, BeeMindDatabase::class.java, name).addMigrations(BeeMindDatabase.MIGRATION_5_6).build()
        try {
            val db = room.openHelper.writableDatabase
            assertEquals(6, db.version)
            before.forEach { (table, rows) -> assertEquals(table, rows, snapshot(db, table)) }
            val repo = com.beemind.personal.data.RoomNotesRepository(room)
            repo.setLinked("n0", "n1", true); repo.setLinked("n1", "n2", true)
            room.close()
            room = Room.databaseBuilder(context, BeeMindDatabase::class.java, name).build()
            val reopened = com.beemind.personal.data.RoomNotesRepository(room)
            assertEquals(setOf("n0", "n2"), reopened.observeThreadNotes("n1", true).first().map { it.note.id }.toSet())
            before.forEach { (table, rows) -> assertEquals(table, rows, snapshot(room.openHelper.writableDatabase, table)) }
            room.openHelper.writableDatabase.query("PRAGMA integrity_check").use { assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0)) }
            room.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { room.close(); context.deleteDatabase(name) }
    }

}
