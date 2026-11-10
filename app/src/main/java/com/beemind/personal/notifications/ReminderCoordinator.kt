package com.beemind.personal.notifications

import androidx.room.withTransaction
import com.beemind.personal.data.local.BeeMindDatabase
import com.beemind.personal.data.local.ReminderEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.util.UUID

/** All app/receiver mutations share this gate; persisted generation checks survive process death. */
object ReminderGate { val mutex = Mutex() }
interface ReminderScheduler {
    fun schedule(reminder: ReminderEntity): String
    fun cancel(reminder: ReminderEntity)
}
interface ReminderNotifier {
    fun allowed(): Boolean
    fun show(reminder: ReminderEntity)
    fun cancel(noteId: String)
    fun cleanup(valid: List<ReminderEntity>) {}
}

class ReminderCoordinator(private val db: BeeMindDatabase, private val scheduler: ReminderScheduler,
    private val notifier: ReminderNotifier, private val clock: Clock = Clock.systemUTC()) {
    private val dao = db.reminderDao()

    suspend fun reconcile() = ReminderGate.mutex.withLock { reconcileLocked() }

    /** Called by the notes repository only after its transaction, while holding ReminderGate. */
    suspend fun changed(previous: ReminderEntity?) {
        previous?.let { safely { scheduler.cancel(it) }; safely { notifier.cancel(it.noteId) } }
        reconcileLocked()
    }

    private suspend fun reconcileLocked() {
        val active = dao.active()
        safely { notifier.cleanup(active) }
        var catchUp = 0
        for (r in active) {
            if (r.dueAt > clock.millis()) {
                if (r.delivery in setOf("PENDING", "BLOCKED", "FAILED")) schedule(r)
            } else if (r.delivery == "CLAIMED") {
                // Crash between durable claim and notify: don't alert again on every launch.
                dao.delivery(r.noteId, r.generation, "MISSED", null)
                safely { notifier.cancel(r.noteId) }
            } else if (r.delivery in setOf("PENDING", "BLOCKED", "FAILED")) {
                if (clock.millis() - r.dueAt > DAY || catchUp >= 3) {
                    dao.delivery(r.noteId, r.generation, "MISSED", null)
                } else {
                    val shown = deliverLocked(r)
                    if (shown) catchUp++
                }
            }
        }
    }

    private suspend fun schedule(r: ReminderEntity) {
        val mode = try { scheduler.schedule(r) } catch (_: RuntimeException) { "FAILED" }
        dao.scheduling(r.noteId, r.generation, mode)
    }

    suspend fun alarm(id: String, generation: String) = ReminderGate.mutex.withLock {
        val r = dao.get(id) ?: return@withLock
        if (r.generation != generation || !r.toReminder().active || r.delivery !in setOf("PENDING", "BLOCKED", "FAILED")) return@withLock
        // A clock adjustment or stale early broadcast must not deliver before the persisted instant.
        if (r.dueAt > clock.millis()) schedule(r) else {
            if (clock.millis() - r.dueAt > DAY) dao.delivery(id, generation, "MISSED", null)
            else deliverLocked(r)
        }
    }

    private suspend fun deliverLocked(r: ReminderEntity): Boolean {
        if (!notifier.allowed()) {
            dao.delivery(r.noteId, r.generation, "BLOCKED", null)
            return false
        }
        // Commit the claim first. The unavoidable Room/Android crash gap is visible as MISSED.
        if (dao.delivery(r.noteId, r.generation, "CLAIMED", null) != 1) return false
        return try {
            notifier.show(r)
            dao.delivery(r.noteId, r.generation, "DELIVERED", clock.millis())
            true
        } catch (_: SecurityException) {
            dao.delivery(r.noteId, r.generation, "BLOCKED", null); false
        } catch (_: RuntimeException) {
            dao.delivery(r.noteId, r.generation, "FAILED", null); false
        }
    }

    suspend fun action(id: String, generation: String, action: String, notification: Boolean = false): Boolean = ReminderGate.mutex.withLock {
        if (action !in setOf("DONE", "SNOOZE", "CANCEL")) return@withLock false
        val r = dao.get(id) ?: return@withLock false
        if (r.generation != generation || !r.toReminder().active || (notification && r.delivery != "DELIVERED")) return@withLock false
        val status = when (action) { "SNOOZE" -> "SNOOZED"; "DONE" -> "DONE"; else -> "CANCELLED" }
        val due = if (action == "SNOOZE") clock.millis() + 600_000 else r.dueAt
        val updated = db.withTransaction { dao.transition(id, generation, status, due, UUID.randomUUID().toString()) }
        if (updated == 0) return@withLock false
        safely { scheduler.cancel(r) }
        safely { notifier.cancel(id) }
        if (action == "SNOOZE") schedule(checkNotNull(dao.get(id)))
        true
    }

    private inline fun safely(block: () -> Unit) { try { block() } catch (_: RuntimeException) { /* Generation already invalidated. */ } }
    private companion object { const val DAY = 86_400_000L }
}
