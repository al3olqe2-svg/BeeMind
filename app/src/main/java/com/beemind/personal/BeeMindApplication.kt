package com.beemind.personal

import android.app.Application
import com.beemind.personal.notifications.*
import kotlinx.coroutines.*
import com.beemind.personal.data.RoomNotesRepository
import com.beemind.personal.data.local.BeeMindDatabase
import com.beemind.personal.domain.NotesRepository

class BeeMindApplication : Application() {
    val database: BeeMindDatabase by lazy { BeeMindDatabase.create(this) }
    val reminderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val reminderScheduler by lazy { AndroidReminderScheduler(this) }
    val reminderNotifier by lazy { AndroidReminderNotifier(this) }
    val reminders by lazy { ReminderCoordinator(database, reminderScheduler, reminderNotifier) }
    val notesRepository: NotesRepository by lazy { RoomNotesRepository(database, remindersChanged = { old ->
        try { reminders.changed(old) } catch (_: Exception) { /* Durable intent is recovered on resume/boot. */ }
    }) }
    override fun onCreate() {
        super.onCreate()
        reminderNotifier.channel()
    }
}
