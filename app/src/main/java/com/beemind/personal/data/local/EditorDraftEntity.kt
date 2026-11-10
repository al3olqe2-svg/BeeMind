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
) {
    fun toDraft() = EditorDraft(sessionId, noteId, title, body, editedAt, expressiveTrait)
}

data class DraftHeader(val sessionId: String, val noteId: String?, val editedAt: Long, val titleBytes: Int, val bodyBytes: Int, val expressiveTrait: ExpressiveTrait)
