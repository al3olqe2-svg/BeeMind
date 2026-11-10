package com.beemind.personal.notifications

import android.content.Context
import android.os.PowerManager

/** Keeps only the short asynchronous broadcast work awake, never a service/screen.
 * Notification action broadcasts do not carry AlarmManager's wake lock. The hard
 * OS timeout also bounds the lease if cancellation/process teardown interrupts us.
 */
internal class ReminderWakeLease(context: Context) : AutoCloseable {
    private val lock = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BeeMind:ReminderBroadcast").apply {
            setReferenceCounted(false)
            acquire(10_000)
        }
    override fun close() { if (lock.isHeld) lock.release() }
}
