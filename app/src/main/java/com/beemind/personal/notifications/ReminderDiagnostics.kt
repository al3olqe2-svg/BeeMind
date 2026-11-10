package com.beemind.personal.notifications

import com.beemind.personal.data.local.BeeMindDatabase
import com.beemind.personal.data.local.FoundationEntity
import com.beemind.personal.data.local.ReminderEntity
import kotlinx.coroutines.CancellationException

/** Bounded local operational metadata, not reminder state or note content. No schema change. */
class ReminderDiagnostics(private val db: BeeMindDatabase) {
    suspend fun record(r: ReminderEntity, event: String) {
        try {
            db.foundationDao().put(FoundationEntity(key(r.noteId), "${r.generation}|$event|${System.currentTimeMillis()}"))
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { /* Diagnostics cannot turn an otherwise successful delivery into a failure. */ }
    }
    companion object { fun key(id: String) = "reminder_trace:$id" }
}
