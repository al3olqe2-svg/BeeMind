package com.beemind.personal.notifications

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.beemind.personal.MainActivity
import com.beemind.personal.R
import com.beemind.personal.data.local.ReminderEntity

class AndroidReminderScheduler(private val context: Context) : ReminderScheduler {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    fun exactAllowed() = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
    private fun pending(r: ReminderEntity) = PendingIntent.getBroadcast(context, 0,
        reminderIntent(context, ReminderAlarmReceiver::class.java, r, "ALARM"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    override fun schedule(reminder: ReminderEntity): String {
        val intent = pending(reminder)
        if (exactAllowed()) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.dueAt, intent)
                return "EXACT"
            } catch (_: SecurityException) { /* Permission may change between check and call. */ }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.dueAt, intent)
        return "INEXACT"
    }
    override fun cancel(reminder: ReminderEntity) { val p = pending(reminder); alarms.cancel(p); p.cancel() }
}

internal fun reminderIntent(context: Context, receiver: Class<*>, r: ReminderEntity, action: String) =
    Intent(context, receiver).setAction(action).setData(Uri.Builder().scheme("beemind").authority("reminder")
        .appendPath(r.noteId).appendPath(r.generation).appendPath(action).build())

class AndroidReminderNotifier(private val context: Context) : ReminderNotifier {
    private val manager = context.getSystemService(NotificationManager::class.java)
    fun channel() {
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "تذكيرات BeeMind", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "تذكيرات الملاحظات التي تختار موعدها"
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        })
    }
    override fun allowed(): Boolean {
        channel()
        return (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    override fun show(reminder: ReminderEntity) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) throw SecurityException("Notification permission unavailable")
        val open = Intent(context, MainActivity::class.java).setAction("OPEN_REMINDER")
            .setData(Uri.Builder().scheme("beemind").authority("note").appendPath(reminder.noteId).appendPath(reminder.generation).build())
            .putExtra("noteId", reminder.noteId).putExtra("generation", reminder.generation)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val content = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(name: String) = PendingIntent.getBroadcast(context, 0,
            reminderIntent(context, ReminderActionReceiver::class.java, reminder, name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // Generic content is private by default and ready for later capsule redaction policy.
        val public = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle("BeeMind").setContentText("لديك تذكير").build()
        val n = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle("BeeMind — حان موعد ملاحظتك")
            .setContentText("افتح الملاحظة، أو أنجز التذكير، أو أجّله عشر دقائق.")
            .setWhen(reminder.dueAt).setShowWhen(true).setContentIntent(content)
            .setCategory(NotificationCompat.CATEGORY_REMINDER).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addExtras(android.os.Bundle().apply { putString("reminderGeneration", reminder.generation) })
            .setPublicVersion(public).setOnlyAlertOnce(true).setAutoCancel(true)
            .addAction(0, "إنجاز", action("DONE"))
            .addAction(0, "تأجيل 10 دقائق", action("SNOOZE")).build()
        manager.notify(reminder.noteId, NOTIFICATION_ID, n)
    }
    override fun cleanup(valid: List<ReminderEntity>) {
        val current = valid.associateBy { it.noteId }
        manager.activeNotifications.filter { it.id == NOTIFICATION_ID }.forEach { notification ->
            val r = current[notification.tag]
            if (r == null || r.delivery !in setOf("DELIVERED", "CLAIMED") ||
                notification.notification.extras.getString("reminderGeneration") != r.generation) cancel(notification.tag)
        }
    }
    override fun cancel(noteId: String) { manager.cancel(noteId, NOTIFICATION_ID) }
    companion object { const val CHANNEL = "note_reminders"; const val NOTIFICATION_ID = 3 }
}
