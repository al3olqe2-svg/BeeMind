package com.beemind.personal.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.beemind.personal.BeeMindApplication
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

abstract class ReminderReceiver : BroadcastReceiver() {
    final override fun onReceive(context: Context, intent: Intent) {
        // Acquire synchronously, before returning control to the Android broadcast dispatcher.
        val lease = ReminderWakeLease(context)
        val pending = goAsync()
        val app = context.applicationContext as BeeMindApplication
        app.reminderScope.launch {
            try { withTimeout(8_000) { receive(app, intent) } }
            catch (e: Exception) {
                // No note text/URI is logged. Preserve the error type instead of hiding the cause.
                Log.w("BeeMindReminder", "${intent.action}: ${e.javaClass.simpleName}; Room intent retained")
            } finally {
                try { lease.close() } finally { pending.finish() }
            }
        }
    }
    protected abstract suspend fun receive(app: BeeMindApplication, intent: Intent)
    protected fun identity(intent: Intent): Pair<String, String>? {
        val uri = intent.data ?: return null
        val p = uri.pathSegments
        if (uri.scheme != "beemind" || uri.authority != "reminder" || p.size != 3 || p[2] != intent.action) return null
        return p[0] to p[1]
    }
}
class ReminderAlarmReceiver : ReminderReceiver() {
    override suspend fun receive(app: BeeMindApplication, intent: Intent) {
        if (intent.action != "ALARM") return
        val (id, generation) = identity(intent) ?: return
        app.reminders.alarm(id, generation)
    }
}
class ReminderActionReceiver : ReminderReceiver() {
    override suspend fun receive(app: BeeMindApplication, intent: Intent) {
        val (id, generation) = identity(intent) ?: return
        val action = intent.action ?: return
        if (action !in setOf("DONE", "SNOOZE")) return
        app.reminders.action(id, generation, action, notification = true)
    }
}
class ReminderRecoveryReceiver : ReminderReceiver() {
    override suspend fun receive(app: BeeMindApplication, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED,
                "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED")) app.reminders.reconcile()
    }
}
