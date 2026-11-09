package com.beemind.personal.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FoundationEntity::class], version = 1, exportSchema = true)
abstract class BeeMindDatabase : RoomDatabase() {
    abstract fun foundationDao(): FoundationDao

    companion object {
        fun create(context: Context): BeeMindDatabase = Room.databaseBuilder(
            context.applicationContext, BeeMindDatabase::class.java, "beemind.db",
        ).build()
    }
}
