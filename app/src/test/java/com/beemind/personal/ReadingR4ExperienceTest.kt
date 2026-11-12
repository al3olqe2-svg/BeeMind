package com.beemind.personal

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.beemind.personal.domain.*
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
class ReadingR4ExperienceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<BeeMindApplication>()
    private val repo get() = app.notesRepository
    private fun waitTag(tag: String) {
        try { compose.waitUntil(15000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
        catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            println(compose.onRoot(useUnmergedTree=true).printToString()); capture("failure-$tag"); throw e
        }
    }
    private fun open(id: String) { waitTag("note_$id"); compose.onNodeWithTag("note_$id").performClick(); waitTag("reading_more") }
    private fun back() = compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
    private fun capture(name: String) = compose.runOnIdle {
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        val view = if (dialog?.isShowing == true) dialog.window!!.decorView else compose.activity.window.decorView
        val image = Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        File("build/reports/screenshots/r4-$name.png").apply { parentFile!!.mkdirs() }.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }; image.recycle()
    }
    @Test fun editorialAtmospheresInlineLinksAndReturnPosition() {
        val body = "أفكار تعبر الزمن. Arabic & English 123 🌿\n\nنحتفظ بكل سطر كما كتبناه.\n".repeat(180)
        runBlocking {
            repo.save("normal", "legacy title never rendered", body, true, "n")
            repo.save("glow", "", "ضوء الصباح\n\nصفحة جديدة، وفكرة تستحق الانتباه.", true, "g", ExpressiveTrait.GLOW)
            repo.save("anger", "", "أكتب كي تتضح الأشياء.\nA quiet page for a strong feeling.", true, "a", ExpressiveTrait.ANGER)
            repo.setLinked("normal","glow",true); repo.setLinked("normal","anger",true)
        }
        open("normal"); capture("reading-normal-long")
        compose.onAllNodesWithText("legacy title never rendered").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("حذف الملاحظة").assertCountEquals(0)
        val actions = compose.onNodeWithTag("reading_actions").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("reading_list").performScrollToNode(hasTestTag("inline_threads"))
        compose.onNodeWithTag("reading_list").performScrollToNode(hasTestTag("inline_thread_anger"))
        capture("reading-inline-two")
        assertEquals(actions,compose.onNodeWithTag("reading_actions").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("inline_thread_glow").performClick(); waitTag("reading_chunk_0"); capture("reading-glow")
        waitTag("inline_thread_normal"); compose.onNodeWithTag("reading_list").performScrollToNode(hasTestTag("inline_thread_normal"))
        back(); waitTag("inline_thread_glow") // lazy state retained on the previous navigation entry
        compose.onNodeWithTag("inline_thread_anger").performClick(); waitTag("reading_chunk_0"); capture("reading-anger")
        compose.onNodeWithTag("manage_threads").performClick(); waitTag("threads_section"); capture("threads-management")
        compose.onNodeWithTag("thread_remove_normal").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithTag("thread_open_normal").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("إغلاق الخيوط").performClick()
        compose.onAllNodesWithTag("inline_threads").assertCountEquals(0)
        back(); waitTag("inline_thread_glow")
        compose.onAllNodesWithTag("inline_thread_anger").assertCountEquals(0)
        assertEquals(body,runBlocking { repo.getNote("normal")!!.body })
    }
    @Test @Config(qualifiers = "w320dp-h720dp") fun largeFontLockedCoverAndSafeLinkedPreview() {
        org.robolectric.RuntimeEnvironment.setFontScale(1.6f); compose.activityRule.scenario.recreate()
        runBlocking {
            repo.save("open", "", "نص واضح في مساحة صغيرة.\nMixed text 🌿\n",true,"o",ExpressiveTrait.GLOW)
            repo.save("locked","SECRET TITLE","SECRET BODY",true,"l", temporalEdit = TemporalEdit(sealUntil=System.currentTimeMillis()+3600000))
            repo.setLinked("open","locked",true)
        }
        open("open"); waitTag("inline_thread_locked"); capture("reading-large-font")
        compose.onNodeWithTag("reading_list").performScrollToNode(hasTestTag("inline_thread_locked"))
        compose.onAllNodesWithText("SECRET",substring=true,useUnmergedTree=true).assertCountEquals(0)
        compose.onNodeWithTag("inline_thread_locked").performClick(); waitTag("reading_more")
        compose.onAllNodesWithTag("reading_chunk_0").assertCountEquals(0)
        compose.onAllNodesWithTag("inline_threads").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("تعديل الملاحظة").assertCountEquals(0)
        compose.onAllNodesWithText("SECRET",substring=true,useUnmergedTree=true).assertCountEquals(0)
        capture("reading-locked")
    }
    @Test fun emptyLinksStayAbsentAndDeleteRemainsConfirmed() {
        runBlocking { repo.save("only","","ملاحظة بلا روابط",true,"s") }
        open("only"); compose.onAllNodesWithTag("inline_threads").assertCountEquals(0)
        capture("reading-normal")
        compose.onNodeWithTag("reading_more").performClick(); compose.onNodeWithText("حذف الملاحظة").performClick()
        compose.onNodeWithText("إلغاء").performClick()
        assertNotNull(runBlocking { repo.getNote("only") })
        compose.onNodeWithTag("reading_more").performClick(); compose.onNodeWithText("حذف الملاحظة").performClick()
        compose.onNodeWithText("حذف نهائي").performClick(); waitTag("new_note")
        assertNull(runBlocking { repo.getNote("only") })
    }
}
