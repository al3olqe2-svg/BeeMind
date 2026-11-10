package com.beemind.personal

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.beemind.personal.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*

/** Seed synthetic records only. Host checks delivery without restarting instrumentation,
 * because Android force-stops the target package when instrumentation finishes. */
@RunWith(AndroidJUnit4::class)
class ReminderDeviceFixture {
    @Test fun seed() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as BeeMindApplication
        val now = Instant.now()
        val dates = mapOf("device-relative" to ReminderTimes.relative(now, 2),
            "device-calendar" to ReminderTimes.local(LocalDateTime.ofInstant(now.plusSeconds(90),
                ZoneId.systemDefault()), ZoneId.systemDefault(), now))
        for ((id, due) in dates) {
            app.notesRepository.save(id, "Device fixture", "Synthetic note", true, id,
                ExpressiveTrait.GLOW, ReminderEdit(true, due))
            val r = app.database.reminderDao().get(id)!!
            assertEquals("EXACT", r.scheduling)
            assertEquals("PENDING", r.delivery)
        }
    }
}
