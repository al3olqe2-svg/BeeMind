package com.beemind.personal

import android.app.NotificationManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.beemind.personal.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*

/** Host orchestrates process death between these phases. Never packaged in the shipped APK. */
@RunWith(AndroidJUnit4::class)
class ReminderDeviceFixture {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as BeeMindApplication
    private val args get() = InstrumentationRegistry.getArguments()
    private val id get() = args.getString("noteId") ?: "device-relative"

    @Test fun seed() = runBlocking {
        val now = Instant.now()
        val due = if (id.contains("calendar")) ReminderTimes.local(
            LocalDateTime.ofInstant(now.plusSeconds(20), ZoneId.systemDefault()), ZoneId.systemDefault(), now)
            else ReminderTimes.relative(now, 1)
        app.notesRepository.save(id, "Device fixture", "Synthetic note", true, id,
            ExpressiveTrait.GLOW, ReminderEdit(true, due))
        val r = app.database.reminderDao().get(id)!!
        assertEquals("EXACT", r.scheduling)
        assertEquals("PENDING", r.delivery)
    }

    @Test fun verifyDeliveryAndSnooze() = runBlocking {
        val r = app.database.reminderDao().get(id)!!
        // Application.onCreate creates only the channel; no Activity or reconciliation is run.
        assertEquals("DELIVERED", r.delivery)
        val n = app.getSystemService(NotificationManager::class.java).activeNotifications.single { it.tag == id }
        val before = System.currentTimeMillis()
        n.notification.actions[1].actionIntent.send()
        kotlinx.coroutines.withTimeout(5_000) {
            while (app.database.reminderDao().get(id)!!.generation == r.generation) kotlinx.coroutines.delay(10)
        }
        val snoozed = app.database.reminderDao().get(id)!!
        assertEquals("SNOOZED", snoozed.status)
        assertTrue(snoozed.dueAt >= before + 600_000)
        assertTrue(snoozed.dueAt <= System.currentTimeMillis() + 600_000)
        assertNotEquals(r.generation, snoozed.generation)
        // Wait for the Android scheduling call, not merely the earlier Room commit.
        kotlinx.coroutines.withTimeout(5_000) {
            while (app.database.reminderDao().get(id)!!.scheduling == "PENDING") kotlinx.coroutines.delay(10)
        }
        assertEquals("EXACT", app.database.reminderDao().get(id)!!.scheduling)
    }

    @Test fun verifySnoozedDeliveryAndDone() = runBlocking {
        val r = app.database.reminderDao().get(id)!!
        assertEquals("SNOOZED", r.status)
        assertEquals("DELIVERED", r.delivery)
        val n = app.getSystemService(NotificationManager::class.java).activeNotifications.single { it.tag == id }
        n.notification.actions[0].actionIntent.send()
        kotlinx.coroutines.withTimeout(5_000) {
            while (app.database.reminderDao().get(id)!!.status != "DONE") kotlinx.coroutines.delay(10)
        }
        assertNotNull(app.notesRepository.getNote(id))
        assertEquals(ExpressiveTrait.GLOW, app.notesRepository.getNote(id)!!.expressiveTrait)
    }
}
