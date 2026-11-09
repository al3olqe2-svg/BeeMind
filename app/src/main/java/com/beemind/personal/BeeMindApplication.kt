package com.beemind.personal

import android.app.Application
import com.beemind.personal.data.local.BeeMindDatabase

class BeeMindApplication : Application() {
    // Lazily opened; Stage 0 never inserts seed records or requests main-thread access.
    val database: BeeMindDatabase by lazy { BeeMindDatabase.create(this) }
}
