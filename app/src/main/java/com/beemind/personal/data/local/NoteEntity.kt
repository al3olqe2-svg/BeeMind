package com.beemind.personal.data.local

import androidx.room.ColumnInfo
import com.beemind.personal.domain.ExpressiveTrait
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.beemind.personal.domain.Note

@Entity(tableName = "notes", indices = [Index(value = ["createdAt", "id"])])
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String?,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "'NONE'") val expressiveTrait: ExpressiveTrait = ExpressiveTrait.NONE,
    @ColumnInfo(defaultValue = "NULL") val resurfaceAt: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val resurfaceSeenAt: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val unlockAt: Long? = null,
) {
    fun toNote() = Note(id, title, body, createdAt, updatedAt, expressiveTrait)
}

/** Small rows keep very long diary text out of Android's bounded CursorWindow. */
data class NoteHeader(val id: String, val createdAt: Long, val updatedAt: Long, val titleBytes: Int?, val bodyBytes: Int, val expressiveTrait: ExpressiveTrait, val resurfaceAt: Long?, val resurfaceSeenAt: Long?, val unlockAt: Long?)
