package com.beemind.personal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.beemind.personal.wallpaper.*
import java.io.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses only synthetic images and an isolated private directory/preferences. */
@RunWith(AndroidJUnit4::class)
class WallpaperDeviceTest {
    @Test fun nativeImportProcessingPersistenceAndPinDeletion() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.filesDir, "r1-device-fixture")
        val prefs = context.getSharedPreferences("r1-device-fixture", Context.MODE_PRIVATE)
        directory.deleteRecursively(); prefs.edit().clear().commit()
        try {
            var repo = WallpaperRepository(directory, prefs)
            repo.newSession(); assertNull(repo.state.value.currentId)
            val bitmap = Bitmap.createBitmap(3000, 4000, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.BLUE)
            val bytes = ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG,90,out); bitmap.recycle(); out.toByteArray() }
            val a = repo.importImage { ByteArrayInputStream(bytes) }
            val b = repo.importImage { ByteArrayInputStream(bytes) }
            repo.newSession(); assertEquals(b, repo.state.value.currentId)
            repo.pin(a); repo.setBlur(80)
            repo = WallpaperRepository(directory, prefs); repo.newSession()
            assertEquals(a, repo.state.value.currentId); assertEquals(a, repo.state.value.pinnedId)
            assertEquals(80, repo.state.value.blur)
            val processed = WallpaperImages().load(repo.file(a), 80)!!
            assertTrue(maxOf(processed.width,processed.height) <= 1440)
            assertArrayEquals(bytes, repo.file(a).readBytes())
            repo.pin(null); repo.newSession(); assertEquals(b, repo.state.value.currentId)
            repo.pin(a); repo.remove(a); assertNull(repo.state.value.pinnedId)
            assertEquals(b, repo.state.value.currentId)
            try { repo.importImage { ByteArrayInputStream(byteArrayOf(1,2,3)) }; fail("Invalid accepted") } catch (_: IOException) { }
            assertEquals(listOf(b), repo.state.value.ids)
        } finally { directory.deleteRecursively(); prefs.edit().clear().commit() }
    }
}
