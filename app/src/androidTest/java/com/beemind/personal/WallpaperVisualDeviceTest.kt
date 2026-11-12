package com.beemind.personal

import android.content.Context
import android.content.Intent
import android.graphics.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.beemind.personal.domain.*
import com.beemind.personal.ui.notes.*
import com.beemind.personal.ui.theme.*
import com.beemind.personal.ui.wallpaper.WallpaperBackground
import com.beemind.personal.wallpaper.*
import java.io.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Android HWUI, synthetic images only. Fixtures never access notes or user wallpapers. */
@RunWith(AndroidJUnit4::class)
class WallpaperVisualDeviceTest {
    @Test fun atmosphericHomeAcrossSixLocalScenes() = runBlocking {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
        val directory = File(context.filesDir, "r1-visual-fixture")
        val prefs = context.getSharedPreferences("r1-visual-fixture", Context.MODE_PRIVATE)
        directory.deleteRecursively(); prefs.edit().clear().commit()
        val repo = WallpaperRepository(directory, prefs); val images = WallpaperImages()
        val notes = listOf(
            NoteSummary("short", null, "تفاصيل صغيرة تستحق أن نتذكرها.", 6, 6),
            NoteSummary("c", null, "ضوء الصباح على صفحة لم تكتب بعد.", 5, 5, ExpressiveTrait.GLOW),
            NoteSummary("a", null, "أحيانًا نحتاج أن نقول ما نشعر به ثم نمضي.", 4, 4, ExpressiveTrait.ANGER),
            NoteSummary("d", null, "", 3, 3, locked = true, unlockAt = 1800000000000),
            NoteSummary("e", null, "فكرة قديمة تستحق نظرة جديدة.", 2, 2, resurfaced = true),
            NoteSummary("long", null, "أفكار هادئة ومساحة للكتابة. ".repeat(20), 1, 1))
        try {
            repo.initialize()
            for (scene in listOf("fallback", "sea", "bright", "dark", "portrait", "detail")) {
                if (scene != "fallback") {
                    val bitmap = Bitmap.createBitmap(600,1000,Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap); val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                    canvas.drawColor(when(scene) { "sea" -> Color.rgb(40,150,220); "bright" -> Color.WHITE; "dark" -> Color.rgb(4,7,12); else -> Color.rgb(170,100,70) })
                    if (scene == "sea") { paint.color = Color.rgb(10,55,140); canvas.drawRect(0f,400f,600f,1000f,paint) }
                    if (scene == "portrait") {
                        paint.color = Color.rgb(235,205,180); canvas.drawOval(160f,120f,440f,500f,paint)
                        paint.color = Color.rgb(35,30,28); canvas.drawOval(60f,480f,540f,1200f,paint)
                    }
                    if (scene == "detail") for (y in 0..980 step 20) for (x in 0..580 step 20) {
                        paint.color = Color.rgb((x*17+y)%256,(x+y*13)%256,(x*7+y*3)%256)
                        canvas.drawRect(x.toFloat(),y.toFloat(),x+20f,y+20f,paint)
                    }
                    val bytes = ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG,100,out); bitmap.recycle(); out.toByteArray() }
                    val id = repo.importImage { ByteArrayInputStream(bytes) }; repo.pin(id)
                    images.load(repo.file(id),65)
                }
                val sceneState = repo.state.value
                ActivityScenario.launch<ComponentActivity>(Intent(context, ComponentActivity::class.java)).use { scenario ->
                    scenario.onActivity { activity -> activity.setContent {
                        BeeMindTheme { AtmosphericTheme {
                            HomeScreen(HomeState.Ready(notes,null), {}, {}, {}, {}, wallpaper = {
                                WallpaperBackground(sceneState, repo, images, Modifier.fillMaxSize())
                            })
                        } }
                    } }
                    instrument.waitForIdleSync(); Thread.sleep(1500); instrument.waitForIdleSync()
                    val screenshot = checkNotNull(instrument.uiAutomation.takeScreenshot())
                    assertTrue(screenshot.width > 0)
                    File(context.filesDir,"r2-native-$scene.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG,100,it) }
                    screenshot.recycle()
                }
            }
        } finally { directory.deleteRecursively(); prefs.edit().clear().commit() }
    }
}
