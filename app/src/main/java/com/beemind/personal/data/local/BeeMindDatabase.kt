package com.beemind.personal.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [FoundationEntity::class, NoteEntity::class, EditorDraftEntity::class], version = 2, exportSchema = true)
abstract class BeeMindDatabase : RoomDatabase() {
    abstract fun foundationDao(): FoundationDao
    abstract fun notesDao(): NotesDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS notes (id TEXT NOT NULL PRIMARY KEY, title TEXT, body TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_createdAt_id ON notes (createdAt, id)")
                db.execSQL("CREATE TABLE IF NOT EXISTS editor_drafts (sessionId TEXT NOT NULL PRIMARY KEY, noteId TEXT, title TEXT NOT NULL, body TEXT NOT NULL, editedAt INTEGER NOT NULL)")
            }
        }

        fun create(context: Context): BeeMindDatabase = Room.databaseBuilder(
            context.applicationContext, BeeMindDatabase::class.java, "beemind.db",
        ).addMigrations(MIGRATION_1_2).build()
    }
}
