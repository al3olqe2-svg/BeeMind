package com.beemind.personal.data.local

import androidx.room.*
import com.beemind.personal.domain.Reminder
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "reminders", foreignKeys = [ForeignKey(entity = NoteEntity::class,
    parentColumns = ["id"], childColumns = ["noteId"], onDelete = ForeignKey.CASCADE)])
data class ReminderEntity(@PrimaryKey val noteId: String, val dueAt: Long, val status: String,
    val generation: String, val delivery: String, val deliveredAt: Long?, val scheduling: String) {
    fun toReminder() = Reminder(noteId, dueAt, status, generation, delivery, deliveredAt, scheduling)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE noteId = :id") suspend fun get(id: String): ReminderEntity?
    @Query("SELECT * FROM reminders WHERE noteId = :id") fun observe(id: String): Flow<ReminderEntity?>
    @Query("SELECT * FROM reminders WHERE status IN ('SCHEDULED', 'SNOOZED') ORDER BY dueAt DESC")
    suspend fun active(): List<ReminderEntity>
    @Upsert suspend fun put(value: ReminderEntity)
    @Query("UPDATE reminders SET delivery = :delivery, deliveredAt = :at WHERE noteId = :id AND generation = :generation AND status IN ('SCHEDULED','SNOOZED')")
    suspend fun delivery(id: String, generation: String, delivery: String, at: Long?): Int
    @Query("UPDATE reminders SET scheduling = :mode WHERE noteId = :id AND generation = :generation")
    suspend fun scheduling(id: String, generation: String, mode: String)
    @Query("UPDATE reminders SET status = :status, dueAt = :due, generation = :next, delivery = 'PENDING', deliveredAt = NULL, scheduling = 'PENDING' WHERE noteId = :id AND generation = :expected AND status IN ('SCHEDULED','SNOOZED')")
    suspend fun transition(id: String, expected: String, status: String, due: Long, next: String): Int
}
