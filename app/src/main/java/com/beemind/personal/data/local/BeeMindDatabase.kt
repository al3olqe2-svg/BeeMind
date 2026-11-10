package com.beemind.personal.data.local

import android.content.Context
import androidx.room.TypeConverters
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [FoundationEntity::class, NoteEntity::class, EditorDraftEntity::class, ReminderEntity::class], version = 4, exportSchema = true)
@TypeConverters(TraitConverters::class)
abstract class BeeMindDatabase : RoomDatabase() {
    abstract fun foundationDao(): FoundationDao
    abstract fun notesDao(): NotesDao
    abstract fun reminderDao(): ReminderDao

    companion object {
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

        fun create(context: Context): BeeMindDatabase = Room.databaseBuilder(
            context.applicationContext, BeeMindDatabase::class.java, "beemind.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
    }
}
