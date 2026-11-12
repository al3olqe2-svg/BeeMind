package com.beemind.personal

import android.content.Context
import android.graphics.*
import androidx.test.core.app.ApplicationProvider
import com.beemind.personal.wallpaper.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WallpaperR4Test {
    @Test fun legacyPreferenceMigrationIsAtomicIdempotentAndRetainsEveryImageSelection() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.cacheDir, "r4-migration").apply { deleteRecursively(); mkdirs() }
        val prefs = context.getSharedPreferences("r4-migration", 0)
        val ids = listOf("00000000-0000-0000-0000-000000000001", "00000000-0000-0000-0000-000000000002")
        ids.forEach { File(dir, "$it.image").writeText("immutable original") }
        val old = JSONObject().put("ids", JSONArray(ids)).put("current", ids[1]).put("pin", ids[1]).put("blur", 65)
        prefs.edit().putString("state", old.toString()).commit()
        val repo = WallpaperRepository(dir, prefs); repo.initialize()
        assertEquals(100, repo.state.value.blur)
        assertEquals(ids, repo.state.value.ids); assertEquals(ids[1], repo.state.value.currentId); assertEquals(ids[1], repo.state.value.pinnedId)
        assertEquals(2, JSONObject(prefs.getString("state", "")!!).getInt("blurVersion"))
        for (value in listOf(0, 25, 50, 75, 100)) {
            repo.setBlur(value)
            val restarted = WallpaperRepository(dir, prefs); restarted.initialize()
            assertEquals(value, restarted.state.value.blur)
            assertEquals(ids, restarted.state.value.ids); assertEquals(ids[1], restarted.state.value.pinnedId)
            ids.forEach { assertEquals("immutable original", File(dir, "$it.image").readText()) }
        }
        dir.deleteRecursively(); prefs.edit().clear().commit(); Unit
    }
    @Test fun sharpPathHasNoArtificialDiffusionAndNewMaximumMatchesOldMinimum() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = Bitmap.createBitmap(720, 1440, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(source); canvas.drawColor(Color.BLACK)
        canvas.drawRect(360f, 0f, 720f, 1440f, Paint().apply { color = Color.WHITE })
        val file = File(context.cacheDir, "r4-sharp.png")
        file.outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }; source.recycle()
        val images = WallpaperImages()
        val zero = images.load(file, 0, .5f)!!
        assertEquals(1440, zero.height); assertEquals(Color.BLACK, zero.getPixel(359,720)); assertEquals(Color.WHITE, zero.getPixel(360,720))
        val values = listOf(0,25,50,75,100).map { value ->
            val image = images.load(file, value, .5f, edge = 720)!!
            // Integrated light spread into the dark half grows at each perceptual stop.
            (150 until 180).sumOf { Color.red(image.getPixel(it,360)) }
        }
        assertTrue(values.zipWithNext().all { (a,b) -> b > a })
        val decoded = WallpaperImages.decode(file,720)!!
        val reference = decoded.copy(Bitmap.Config.ARGB_8888,true); decoded.recycle()
        WallpaperImages.blur(reference,6)
        val maximum = images.load(file,100,.5f,edge=720)!!
        for (x in 150..210) assertEquals(reference.getPixel(x,360), maximum.getPixel(x,360))
        val frost = images.load(file,0,.5f,edge=720,frost=true)!!
        assertTrue(Color.red(frost.getPixel(175,360)) > 0)
        assertEquals(Color.BLACK, zero.getPixel(350,720)) // shared frost never mutates clear derivative
        file.delete(); reference.recycle(); Unit
    }
}
