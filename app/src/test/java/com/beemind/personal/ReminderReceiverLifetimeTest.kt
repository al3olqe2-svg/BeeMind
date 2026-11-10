package com.beemind.personal

import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.beemind.personal.notifications.ReminderReceiver
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 34])
class ReminderReceiverLifetimeTest {
    private val app get() = ApplicationProvider.getApplicationContext<BeeMindApplication>()
    @Test fun asynchronousBroadcastKeepsCpuAwakeUntilActualWorkFinishesWithoutActivity() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val receiver = object : ReminderReceiver() {
            override suspend fun receive(app: BeeMindApplication, intent: Intent) {
                started.complete(Unit)
                finish.await()
            }
        }
        shadowOf(app).grantPermissions(app.packageName + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        ContextCompat.registerReceiver(app, receiver, IntentFilter("com.beemind.TEST_LEASE"), ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            app.sendBroadcast(Intent("com.beemind.TEST_LEASE").setPackage(app.packageName))
            shadowOf(android.os.Looper.getMainLooper()).idle()
            withTimeout(5_000) { started.await() }
            val lock = ShadowPowerManager.getLatestWakeLock()
            assertNotNull(lock)
            assertTrue(lock.isHeld)
            finish.complete(Unit)
            withTimeout(5_000) { while (lock.isHeld) delay(10) }
            assertFalse(lock.isHeld)
        } finally { app.unregisterReceiver(receiver) }
    }
    @Test fun failingBroadcastReleasesCpuLeaseWithoutOpeningActivity() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val receiver = object : ReminderReceiver() {
            override suspend fun receive(app: BeeMindApplication, intent: Intent) {
                started.complete(Unit)
                error("Synthetic receiver failure")
            }
        }
        shadowOf(app).grantPermissions(app.packageName + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        ContextCompat.registerReceiver(app, receiver, IntentFilter("com.beemind.TEST_FAILURE"), ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            app.sendBroadcast(Intent("com.beemind.TEST_FAILURE").setPackage(app.packageName))
            shadowOf(android.os.Looper.getMainLooper()).idle()
            withTimeout(5_000) { started.await() }
            val lock = ShadowPowerManager.getLatestWakeLock()
            withTimeout(5_000) { while (lock.isHeld) delay(10) }
            assertFalse(lock.isHeld)
        } finally { app.unregisterReceiver(receiver) }
    }
}
