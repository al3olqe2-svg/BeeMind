package com.beemind.personal.data.local

import androidx.room.*
import com.beemind.personal.domain.ThreadNote
import kotlinx.coroutines.flow.Flow

/** One canonical, undirected edge. The primary key indexes its first endpoint. */
@Entity(tableName = "note_links", primaryKeys = ["lowId", "highId"],
    foreignKeys = [ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["lowId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["highId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("highId")])
data class NoteLink(val lowId: String, val highId: String)

@Dao
interface NoteLinksDao {
    // SQLite BINARY order is the sole canonicalization authority, including non-ASCII IDs.
    @Query("INSERT OR IGNORE INTO note_links(lowId, highId) SELECT CASE WHEN :a < :b THEN :a ELSE :b END, CASE WHEN :a < :b THEN :b ELSE :a END WHERE :a != :b")
    suspend fun connect(a: String, b: String)

    @Query("DELETE FROM note_links WHERE lowId = CASE WHEN :a < :b THEN :a ELSE :b END AND highId = CASE WHEN :a < :b THEN :b ELSE :a END")
    suspend fun disconnect(a: String, b: String)

    @Query("SELECT " + NOTE_SUMMARY_COLUMNS + ", 1 AS linked FROM notes n LEFT JOIN reminders r ON r.noteId = n.id WHERE n.id IN (SELECT highId FROM note_links WHERE lowId = :id UNION ALL SELECT lowId FROM note_links WHERE highId = :id) ORDER BY n.createdAt DESC, n.id ASC")
    fun observeLinked(id: String, now: Long): Flow<List<ThreadNote>>

    @Query("SELECT " + NOTE_SUMMARY_COLUMNS + ", CASE WHEN n.id IN (SELECT highId FROM note_links WHERE lowId = :id UNION ALL SELECT lowId FROM note_links WHERE highId = :id) THEN 1 ELSE 0 END AS linked FROM notes n LEFT JOIN reminders r ON r.noteId = n.id WHERE n.id != :id ORDER BY n.createdAt DESC, n.id ASC")
    fun observeCandidates(id: String, now: Long): Flow<List<ThreadNote>>
}
