package com.beemind.personal.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Internal metadata only. Note/trait tables arrive in their explicitly approved stages. */
@Entity(tableName = "app_metadata")
data class FoundationEntity(@PrimaryKey val key: String, val value: String)
