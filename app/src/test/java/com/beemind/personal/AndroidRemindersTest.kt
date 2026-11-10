package com.beemind.personal

import android.Manifest
import android.app.*
import android.content.*
import androidx.test.core.app.ApplicationProvider
import com.beemind.personal.data.local.ReminderEntity
import com.beemind.personal.notifications.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AndroidRemindersTest {
    private val app get() = ApplicationProvider.getApplicationContext<BeeMindApplication>()
    private fun reminder(gen: String = "g1") = ReminderEntity("note", System.currentTimeMillis() + 300_000,
        "SCHEDULED", gen, "PENDING", null, "PENDING")
    @Test fun restrictedBackgroundIsReportedSeparatelyFromExactPermission() {
        val activity = app.getSystemService(ActivityManager::class.java)
        shadowOf(activity).setBackgroundRestricted(true)
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val scheduler = AndroidReminderScheduler(app)
        assertTrue(scheduler.exactAllowed())
        assertTrue(scheduler.backgroundRestricted())
        shadowOf(activity).setBackgroundRestricted(false)
        assertFalse(scheduler.backgroundRestricted())
    }
    @Test fun exactDeniedUsesInexactAndGenerationIntentsDoNotCollide() {
        val manager = app.getSystemService(AlarmManager::class.java)
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val scheduler = AndroidReminderScheduler(app)
        val r = reminder()
        assertEquals("INEXACT", scheduler.schedule(r))
        assertNotEquals(org.robolectric.shadows.ShadowAlarmManager.WINDOW_EXACT, shadowOf(manager).peekNextScheduledAlarm()!!.windowLengthMs)
        scheduler.schedule(reminder("g2"))
        assertEquals(2, shadowOf(manager).scheduledAlarms.size)
        scheduler.cancel(r)
        assertEquals(1, shadowOf(manager).scheduledAlarms.size)
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        assertEquals("EXACT", scheduler.schedule(reminder("g2")))
        assertEquals(org.robolectric.shadows.ShadowAlarmManager.WINDOW_EXACT, shadowOf(manager).peekNextScheduledAlarm()!!.windowLengthMs)
    }
    @Test fun notificationPermissionChannelPrivacyAndExplicitActionIdentity() {
        val notifier = AndroidReminderNotifier(app)
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(notifier.allowed())
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(notifier.allowed())
        val r = reminder()
        notifier.show(r)
        val manager = app.getSystemService(NotificationManager::class.java)
        val n = manager.activeNotifications.single().notification
        assertEquals(Notification.VISIBILITY_PRIVATE, n.visibility)
        assertNotNull(n.publicVersion)
        assertEquals(2, n.actions.size)
        val intents = n.actions.map { shadowOf(it.actionIntent).savedIntent }
        assertNotEquals(intents[0].data, intents[1].data)
        assertTrue(intents.all { it.component!!.className.endsWith("ReminderActionReceiver") })
        assertTrue(n.actions.all { it.actionIntent.isImmutable })
        assertTrue(intents.all { it.flags and Intent.FLAG_RECEIVER_FOREGROUND != 0 })
        assertEquals(MainActivity::class.java.name, shadowOf(n.contentIntent).savedIntent.component!!.className)
        val channel = manager.getNotificationChannel(AndroidReminderNotifier.CHANNEL)
        channel.importance = NotificationManager.IMPORTANCE_NONE
        manager.createNotificationChannel(channel)
        assertFalse(notifier.allowed())
        notifier.cleanup(listOf(reminder("new-generation")))
        assertEquals(0, manager.activeNotifications.size)
        notifier.cancel(r.noteId)
    }
    @Test fun alarmAndActionBroadcastsUseDurableState() = runBlocking {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        app.notesRepository.save("broadcast", "بث", "نص", true, "s",
            reminderEdit = com.beemind.personal.domain.ReminderEdit(true, System.currentTimeMillis() + 300_000))
        val r = app.database.reminderDao().get("broadcast")!!
        app.database.reminderDao().put(r.copy(dueAt = System.currentTimeMillis() - 1000))
        val alarm = Intent(app, ReminderAlarmReceiver::class.java).setAction("ALARM")
            .setData(android.net.Uri.parse("beemind://reminder/broadcast/${r.generation}/ALARM"))
        app.sendBroadcast(alarm)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        withTimeout(5_000) { while (app.database.reminderDao().get("broadcast")!!.delivery != "DELIVERED") delay(10) }
        val manager = app.getSystemService(NotificationManager::class.java)
        val n = manager.activeNotifications.single().notification
        n.actions[0].actionIntent.send()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        withTimeout(5_000) { while (app.database.reminderDao().get("broadcast")!!.status != "DONE") delay(10) }
        assertNotNull(app.notesRepository.getNote("broadcast"))
        app.notesRepository.delete("broadcast")
    }
    @Test fun capsuleReminderNotificationAndPendingIntentsContainNoNoteText() = runBlocking {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val now = System.currentTimeMillis()
        val repo = app.notesRepository
        repo.save("capsule", "TOP_SECRET_TITLE", "TOP_SECRET_BODY", true, "s",
            reminderEdit = com.beemind.personal.domain.ReminderEdit(true, now + 1000),
            temporalEdit = com.beemind.personal.domain.TemporalEdit(sealUntil = now + 600000))
        val reminder = app.database.reminderDao().get("capsule")!!
        app.reminderNotifier.show(reminder)
        val n = app.getSystemService(NotificationManager::class.java).activeNotifications.single().notification
        assertFalse(n.extras.toString().contains("TOP_SECRET"))
        assertFalse(n.publicVersion.extras.toString().contains("TOP_SECRET"))
        val open = shadowOf(n.contentIntent).savedIntent
        assertFalse(open.toUri(0).contains("TOP_SECRET"))
        n.actions.forEach { assertFalse(shadowOf(it.actionIntent).savedIntent.toUri(0).contains("TOP_SECRET")) }
        assertTrue(repo.getNote(open.getStringExtra("noteId")!!)!!.locked)
        repo.scheduleReminder("capsule", now + 2000)
        assertEquals(0, app.getSystemService(NotificationManager::class.java).activeNotifications.size)
        assertTrue(repo.getNote("capsule")!!.locked)
        repo.delete("capsule")
    }
    @Test fun bootReceiverRestoresPersistedFutureAlarm() = runBlocking {
        val repo = app.notesRepository
        repo.save("boot", "إقلاع", "", true, "s", reminderEdit = com.beemind.personal.domain.ReminderEdit(true, System.currentTimeMillis() + 600_000))
        val r = app.database.reminderDao().get("boot")!!
        app.reminderScheduler.cancel(r)
        app.sendBroadcast(Intent(app, ReminderRecoveryReceiver::class.java).setAction(Intent.ACTION_BOOT_COMPLETED))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        withTimeout(5_000) {
            while (shadowOf(app.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty()) delay(10)
        }
        val manager = app.getSystemService(AlarmManager::class.java)
        assertEquals(1, shadowOf(manager).scheduledAlarms.size)
        assertEquals(r.generation, app.database.reminderDao().get("boot")!!.generation)
        repo.delete("boot")
    }
}
