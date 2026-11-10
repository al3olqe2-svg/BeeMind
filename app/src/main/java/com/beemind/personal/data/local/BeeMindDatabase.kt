package com.beemind.personal.data.local

import android.content.Context
import androidx.room.TypeConverters
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [FoundationEntity::class, NoteEntity::class, EditorDraftEntity::class, ReminderEntity::class, NoteLink::class], version = 6, exportSchema = true)
@TypeConverters(TraitConverters::class)
abstract class BeeMindDatabase : RoomDatabase() {
    abstract fun foundationDao(): FoundationDao
    abstract fun notesDao(): NotesDao
    abstract fun reminderDao(): ReminderDao

    abstract fun noteLinksDao(): NoteLinksDao

    companion object {
        private fun enforceLinkOrder(db: SupportSQLiteDatabase) {
            for (operation in listOf("INSERT", "UPDATE")) db.execSQL("CREATE TRIGGER IF NOT EXISTS note_links_order_${operation.lowercase()} BEFORE $operation ON note_links WHEN NEW.lowId >= NEW.highId BEGIN SELECT RAISE(ABORT, 'Note links require ordered distinct endpoints'); END")
        }
        val LINK_CONSTRAINTS = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) { enforceLinkOrder(db) }
        }
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS note_links (lowId TEXT NOT NULL, highId TEXT NOT NULL, PRIMARY KEY(lowId, highId), FOREIGN KEY(lowId) REFERENCES notes(id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(highId) REFERENCES notes(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_links_highId ON note_links (highId)")
                enforceLinkOrder(db)
            }
        }

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS notes (id TEXT NOT NULL PRIMARY KEY, title TEXT, body TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_createdAt_id ON notes (createdAt, id)")
                db.execSQL("CREATE TABLE IF NOT EXISTS editor_drafts (sessionId TEXT NOT NULL PRIMARY KEY, noteId TEXT, title TEXT NOT NULL, body TEXT NOT NULL, editedAt INTEGER NOT NULL)")
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN expressiveTrait TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE editor_drafts ADD COLUMN expressiveTrait TEXT NOT NULL DEFAULT 'NONE'")
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE editor_drafts ADD COLUMN reminderEdited INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE editor_drafts ADD COLUMN reminderDueAt INTEGER DEFAULT NULL")
                db.execSQL("CREATE TABLE IF NOT EXISTS reminders (noteId TEXT NOT NULL, dueAt INTEGER NOT NULL, status TEXT NOT NULL, generation TEXT NOT NULL, delivery TEXT NOT NULL, deliveredAt INTEGER, scheduling TEXT NOT NULL, PRIMARY KEY(noteId), FOREIGN KEY(noteId) REFERENCES notes(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN resurfaceAt INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE notes ADD COLUMN resurfaceSeenAt INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE notes ADD COLUMN unlockAt INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE editor_drafts ADD COLUMN resurfaceEdited INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE editor_drafts ADD COLUMN resurfaceAt INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE editor_drafts ADD COLUMN sealUntil INTEGER DEFAULT NULL")
            }
        }

        fun create(context: Context): BeeMindDatabase = Room.databaseBuilder(
            context.applicationContext, BeeMindDatabase::class.java, "beemind.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).addCallback(LINK_CONSTRAINTS).build()
    }
}
