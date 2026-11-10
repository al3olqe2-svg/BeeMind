package com.beemind.personal.data.local

import androidx.room.ColumnInfo
import com.beemind.personal.domain.ExpressiveTrait
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.beemind.personal.domain.EditorDraft

/** Drafts are private local recovery records, never notes or grid items. */
@Entity(tableName = "editor_drafts")
data class EditorDraftEntity(
    @PrimaryKey val sessionId: String,
    val noteId: String?,
    val title: String,
    val body: String,
    val editedAt: Long,
    @ColumnInfo(defaultValue = "'NONE'") val expressiveTrait: ExpressiveTrait = ExpressiveTrait.NONE,
    @ColumnInfo(defaultValue = "0") val reminderEdited: Boolean = false,
    @ColumnInfo(defaultValue = "NULL") val reminderDueAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val resurfaceEdited: Boolean = false,
    @ColumnInfo(defaultValue = "NULL") val resurfaceAt: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val sealUntil: Long? = null,
) {
    fun toDraft() = EditorDraft(sessionId, noteId, title, body, editedAt, expressiveTrait, com.beemind.personal.domain.ReminderEdit(reminderEdited, reminderDueAt))
}

data class DraftHeader(val sessionId: String, val noteId: String?, val editedAt: Long, val titleBytes: Int, val bodyBytes: Int, val expressiveTrait: ExpressiveTrait, val reminderEdited: Boolean, val reminderDueAt: Long?, val resurfaceEdited: Boolean, val resurfaceAt: Long?, val sealUntil: Long?)
