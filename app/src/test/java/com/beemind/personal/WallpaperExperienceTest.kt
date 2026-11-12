package com.beemind.personal

import android.graphics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w393dp-h851dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class WallpaperExperienceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<BeeMindApplication>()
    private fun waitTag(tag: String) {
        try { compose.waitUntil(15000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(100, java.util.concurrent.TimeUnit.MILLISECONDS)
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        } } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            println("WALLPAPER STATE: ${app.wallpapers.state.value}")
            println(compose.onRoot(useUnmergedTree = true).printToString())
            screenshot("failure-$tag")
            throw e
        }
    }
    private fun screenshot(name: String) {
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/screenshots/r1-$name.png"); file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
    private fun synthetic(name: String): ByteArray {
        val bitmap = Bitmap.createBitmap(600, 1000, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (name) {
            "sea" -> { canvas.drawColor(Color.rgb(90,175,225)); paint.color = Color.rgb(12,80,175); canvas.drawRect(0f,400f,600f,1000f,paint) }
            "bright" -> canvas.drawColor(Color.rgb(253,253,248))
            "dark" -> canvas.drawColor(Color.rgb(5,7,12))
            "portrait" -> {
                canvas.drawColor(Color.rgb(170,110,80)); paint.color = Color.rgb(228,195,164)
                canvas.drawOval(170f,120f,430f,500f,paint); paint.color = Color.rgb(45,36,30)
                canvas.drawOval(80f,470f,520f,1200f,paint)
            }
            "detail" -> for (y in 0 until 1000 step 20) for (x in 0 until 600 step 20) {
                paint.color = Color.rgb((x * 17 + y) % 256, (x + y * 13) % 256, (x * 7 + y * 3) % 256)
                canvas.drawRect(x.toFloat(),y.toFloat(),x+20f,y+20f,paint)
            }
        }
        return ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG,100,out); bitmap.recycle(); out.toByteArray() }
    }
    @Test fun sixAtmospheresAndSettingsRenderWithReadableNotes() {
        runBlocking {
            app.notesRepository.save("r1-a", "", "أفكار تحتاج إلى مساحة هادئة. ".repeat(9), true, "r1-a")
            app.notesRepository.save("r1-b", "", "تفاصيل صغيرة تستحق أن نتذكرها.", true, "r1-b")
        }
        waitTag("note_r1-a"); screenshot("fallback")
        for (name in listOf("sea", "bright", "dark", "portrait", "detail")) {
            val id = runBlocking { app.wallpapers.importImage { ByteArrayInputStream(synthetic(name)) }.also { app.wallpapers.pin(it) } }
            waitTag("wallpaper_ready_$id")
            compose.onNodeWithTag("note_r1-a").assertIsDisplayed()
            // Wait for this exact cached processing task before capturing the composed frame.
            runBlocking { app.wallpaperImages.load(app.wallpapers.file(id), 65, 393f / 851f) }
            compose.waitForIdle(); screenshot(name)
        }
        compose.onNodeWithTag("wallpaper_settings_open").performClick(); waitTag("wallpaper_settings")
        compose.onNodeWithText("شدة الضبابية").assertIsDisplayed()
        compose.onNodeWithTag("wallpaper_blur").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(80f) }
        compose.waitUntil(15000) { app.wallpapers.state.value.blur == 80 }
        val pinned = app.wallpapers.state.value.pinnedId!!
        waitTag("wallpaper_ready_$pinned")
        compose.waitForIdle(); screenshot("settings")
        compose.onNodeWithTag("wallpaper_settings").performScrollToNode(hasTestTag("wallpaper_$pinned"))
        compose.onNodeWithText("مثبتة · خلفيتك الدائمة").assertIsDisplayed()
        screenshot("settings-pinned")
        compose.onNodeWithText("إلغاء التثبيت").performClick()
        compose.waitUntil(15000) { app.wallpapers.state.value.pinnedId == null }

    }
    @Test fun navigationAndRecreationPreserveWallpaperAndNotes() {
        val ids = runBlocking { listOf(
            app.wallpapers.importImage { ByteArrayInputStream(synthetic("sea")) },
            app.wallpapers.importImage { ByteArrayInputStream(synthetic("bright")) }) }
        runBlocking { app.notesRepository.save("r1-existing", "", "ملاحظة باقية", true, "r1-existing") }
        waitTag("note_r1-existing")
        val current = app.wallpapers.state.value.currentId
        compose.onNodeWithTag("wallpaper_settings_open").performClick(); waitTag("wallpaper_settings")
        compose.activityRule.scenario.recreate(); waitTag("wallpaper_settings")
        assertEquals(current, app.wallpapers.state.value.currentId)
        compose.onNodeWithContentDescription("رجوع").performClick(); waitTag("note_r1-existing")
        compose.onNodeWithTag("note_r1-existing").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("تعديل الملاحظة").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(current, app.wallpapers.state.value.currentId)
        compose.onNodeWithText("ملاحظة باقية").assertIsDisplayed()
        assertTrue(current in ids)
        app.wallpaperSessions.pickingImages = true
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.waitForIdle(); assertEquals(current, app.wallpapers.state.value.currentId)
        app.wallpaperSessions.pickingImages = false
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.waitUntil(15000) { app.wallpapers.state.value.currentId != current }
        compose.waitUntil(15000) {
            runCatching { compose.onNodeWithText("ملاحظة باقية").assertIsDisplayed(); true }.getOrDefault(false)
        }
    }
}
