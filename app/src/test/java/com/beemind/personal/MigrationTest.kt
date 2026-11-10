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
            .addMigrations(BeeMindDatabase.MIGRATION_1_2, BeeMindDatabase.MIGRATION_2_3).build()
        try {
            val db = room.openHelper.writableDatabase // Room validates the complete migrated schema here.
            assertEquals(3, db.version)
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
            .addMigrations(BeeMindDatabase.MIGRATION_2_3).build()
        try {
            val repo = com.beemind.personal.data.RoomNotesRepository(room)
            val note = requireNotNull(repo.getNote("old"))
            assertEquals(com.beemind.personal.domain.Note("old", "عنوان", text, 123L, 456L), note)
            assertEquals(com.beemind.personal.domain.EditorDraft("draft", "old", "مسودة", text, 789L), repo.getDraft("draft"))
            assertEquals(3, room.openHelper.writableDatabase.version)
            room.openHelper.writableDatabase.query("PRAGMA integrity_check").use {
                assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0))
            }
        } finally { room.close(); context.deleteDatabase(name) }
    }

}
