package com.beemind.personal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.beemind.personal.wallpaper.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WallpaperRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences("wallpaper-test", Context.MODE_PRIVATE)
    private val directory get() = File(context.filesDir, "wallpaper-test")
    private lateinit var repo: WallpaperRepository
    @Before fun setup() {
        directory.deleteRecursively(); prefs.edit().clear().commit()
        repo = WallpaperRepository(directory, prefs)
    }
    private fun image(width: Int = 40, height: Int = 60): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); bitmap.recycle(); out.toByteArray()
        }
    }
    private suspend fun add(bytes: ByteArray = image()) = repo.importImage { ByteArrayInputStream(bytes) }

    @Test fun emptyAndSingleImageHaveStableFallbackAndSelection() = runBlocking {
        repo.newSession(); assertNull(repo.state.value.currentId); assertTrue(repo.state.value.loaded)
        val id = add(); assertEquals(id, repo.state.value.currentId)
        repeat(3) { repo.newSession(); assertEquals(id, repo.state.value.currentId) }
        assertTrue(repo.file(id).isFile)
    }
    @Test fun multipleImagesCycleWithoutImmediateRepeatAndPersist() = runBlocking {
        val ids = listOf(add(), add(), add())
        assertEquals(ids.first(), repo.state.value.currentId)
        repeat(6) { index -> repo.newSession(); assertEquals(ids[(index + 1) % 3], repo.state.value.currentId) }
        val restarted = WallpaperRepository(directory, prefs); restarted.initialize()
        assertEquals(repo.state.value, restarted.state.value)
        restarted.newSession(); assertEquals(ids[1], restarted.state.value.currentId)
    }
    @Test fun pinSurvivesRestartAndUnpinResumesCycle() = runBlocking {
        val a = add(); val b = add(); repo.pin(b)
        repo = WallpaperRepository(directory, prefs); repo.newSession()
        assertEquals(b, repo.state.value.pinnedId); assertEquals(b, repo.state.value.currentId)
        repo.pin(null); repo.newSession()
        assertNull(repo.state.value.pinnedId); assertEquals(a, repo.state.value.currentId)
    }
    @Test fun removingCurrentPinnedAndFinalImagesLeavesValidState() = runBlocking {
        val a = add(); val b = add(); val c = add()
        repo.remove(c); assertFalse(repo.file(c).exists()); assertEquals(a, repo.state.value.currentId)
        repo.pin(b); repo.remove(b)
        assertNull(repo.state.value.pinnedId); assertEquals(a, repo.state.value.currentId)
        repo.remove(a); repo.newSession(); assertNull(repo.state.value.currentId)
        assertTrue(repo.state.value.ids.isEmpty())
    }
    @Test fun blurIsClampedAndDurable() = runBlocking {
        repo.setBlur(82); repo = WallpaperRepository(directory, prefs); repo.initialize()
        assertEquals(82, repo.state.value.blur)
        repo.setBlur(-1); assertEquals(0, repo.state.value.blur)
        repo.setBlur(999); assertEquals(100, repo.state.value.blur)
    }
    @Test fun corruptUnreadableAndPartialCopyNeverEnterCollection() = runBlocking {
        val id = add()
        for (open in listOf<() -> java.io.InputStream?>(
            { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }, { null },
            { object : java.io.InputStream() { override fun read(): Int = throw java.io.IOException("Read failed") } })) {
            try { repo.importImage(open); fail("Expected read failure") } catch (_: java.io.IOException) { }
        }
        assertEquals(listOf(id), repo.state.value.ids)
        assertEquals(listOf(repo.file(id)), directory.listFiles()!!.toList())
    }
    @Test fun largeImageIsSampledAndOriginalRemainsUnchanged() = runBlocking {
        val original = image(4096, 3072)
        val id = add(original)
        val decoded = WallpaperImages.decode(repo.file(id), 720)!!
        assertTrue(maxOf(decoded.width, decoded.height) <= 720); decoded.recycle()
        val processor = WallpaperImages()
        val rendered = processor.load(repo.file(id), 65)!!
        assertTrue(maxOf(rendered.width, rendered.height) <= 1440)
        assertSame(rendered, processor.load(repo.file(id), 65))
        val thumbnail = processor.load(repo.file(id))!!
        assertTrue(thumbnail.allocationByteCount <= 640 * 280 * 4)
        assertEquals(Color.BLUE,thumbnail.getPixel(thumbnail.width / 2,thumbnail.height / 2))
        assertArrayEquals(original, repo.file(id).readBytes())
    }
    @Test fun exifOrientationIsAppliedWithoutChangingStoredBytes() = runBlocking {
        val source = File(context.cacheDir,"oriented.jpg")
        val bitmap = Bitmap.createBitmap(300,100,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,90,it) }; bitmap.recycle()
        androidx.exifinterface.media.ExifInterface(source).apply {
            setAttribute(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,"6"); saveAttributes()
        }
        val bytes = source.readBytes(); source.delete()
        val id = add(bytes)
        val decoded = WallpaperImages.decode(repo.file(id),720)!!
        assertEquals(100,decoded.width); assertEquals(300,decoded.height); decoded.recycle()
        assertArrayEquals(bytes,repo.file(id).readBytes())
    }
    @Test fun strongerBlurSoftensEdgesAndChangesOnlyDerivative() = runBlocking {
        val bitmap = Bitmap.createBitmap(360,720,Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        canvas.drawRect(180f,0f,360f,720f,android.graphics.Paint().apply { color = Color.WHITE })
        val bytes = ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG,100,out); bitmap.recycle(); out.toByteArray() }
        val id = add(bytes); val images = WallpaperImages()
        val soft = images.load(repo.file(id),0, edge = 720)!!
        val strong = images.load(repo.file(id),100, edge = 720)!!
        assertTrue(Color.red(strong.getPixel(175,360)) > Color.red(soft.getPixel(175,360)))
        assertTrue(Color.red(strong.getPixel(185,360)) < Color.red(soft.getPixel(185,360)))
        assertArrayEquals(bytes,repo.file(id).readBytes())
    }
    @Test fun missingFilesAndInterruptedImportsRecoverWithoutDanglingPin() = runBlocking {
        val a = add(); repo.pin(a); repo.file(a).delete()
        File(directory, "unfinished.tmp").writeText("partial")
        repo = WallpaperRepository(directory, prefs); repo.initialize()
        assertTrue(repo.state.value.ids.isEmpty()); assertNull(repo.state.value.pinnedId)
        assertNull(repo.state.value.currentId); assertTrue(directory.listFiles()!!.isEmpty())
    }
    @Test fun sessionGateIgnoresNavigationRecreationAndPicker() {
        val sessions = WallpaperSessions()
        assertTrue(sessions.enter())
        repeat(4) { assertFalse(sessions.enter()) } // UI/navigation cannot trigger a new session.
        sessions.leave(changingConfiguration = true); assertFalse(sessions.enter())
        sessions.pickingImages = true
        sessions.leave(changingConfiguration = false); assertFalse(sessions.enter())
        sessions.pickingImages = false
        sessions.leave(changingConfiguration = false); assertTrue(sessions.enter()); assertFalse(sessions.enter())
        assertTrue(WallpaperSessions().enter()) // Fresh process.
    }
}
