package com.beemind.personal

import android.app.Application
import com.beemind.personal.data.RoomNotesRepository
import com.beemind.personal.data.local.BeeMindDatabase
import com.beemind.personal.domain.NotesRepository

class BeeMindApplication : Application() {
    val database: BeeMindDatabase by lazy { BeeMindDatabase.create(this) }
    val notesRepository: NotesRepository by lazy { RoomNotesRepository(database) }
}
