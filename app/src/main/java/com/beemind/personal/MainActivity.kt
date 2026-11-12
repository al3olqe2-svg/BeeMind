package com.beemind.personal

import android.graphics.Color
import android.os.Bundle
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.beemind.personal.ui.BeeMindApp
import com.beemind.personal.ui.theme.BeeMindTheme

class MainActivity : ComponentActivity() {
    val reminderOpen = kotlinx.coroutines.flow.MutableStateFlow<Pair<String, String>?>(null)
    override fun onStart() {
        super.onStart()
        val app = application as BeeMindApplication
        if (app.wallpaperSessions.enter()) app.wallpaperScope.launch {
            try { app.wallpapers.newSession() } catch (_: java.io.IOException) { /* Keep the last wallpaper; retry next session. */ }
        }
    }
    override fun onStop() {
        (application as BeeMindApplication).wallpaperSessions.leave(isChangingConfigurations)
        super.onStop()
    }
    override fun onResume() {
        super.onResume()
        val app = application as BeeMindApplication
        app.notesRepository.refreshTime()
        app.reminderScope.launch {
            try { app.reminders.reconcile() } catch (_: Exception) { /* Retry on next lifecycle event. */ }
        }
    }
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readReminderIntent()
    }
    private fun readReminderIntent() {
        if (intent.action == "OPEN_REMINDER") {
            val id = intent.getStringExtra("noteId")
            val generation = intent.getStringExtra("generation")
            if (id != null && generation != null) reminderOpen.value = id to generation
            intent.action = null
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        readReminderIntent()
        setContent { BeeMindTheme { BeeMindApp() } }
    }
}
