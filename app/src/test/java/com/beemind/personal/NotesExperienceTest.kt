package com.beemind.personal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Parcel
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.beemind.personal.domain.NotesRepository
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w393dp-h851dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class NotesExperienceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val repository: NotesRepository
        get() = ApplicationProvider.getApplicationContext<BeeMindApplication>().notesRepository

    private fun waitForTag(tag: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun openNew() {
        compose.waitUntil(15_000) { compose.onAllNodesWithText("اكتب أول ملاحظة").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("اكتب أول ملاحظة").performClick()
        waitForTag("editor_body")
    }
    private fun screenshot(name: String) {
        val file = File("build/reports/screenshots/$name.png")
        requireNotNull(file.parentFile).mkdirs()
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun longEditorSurvivesActivityRecreationWithoutPuttingTextInBundle() {
        openNew()
        val longText = "يومياتي العربية الطويلة، مع رموز 🌈 وأسطر متعددة.\n".repeat(2_500)
        compose.onNodeWithTag("editor_title").performTextInput("عنوان يبقى")
        compose.onNodeWithTag("editor_body").performTextInput(longText)
        compose.activityRule.scenario.onActivity { activity ->
            val bundle = Bundle()
            InstrumentationRegistry.getInstrumentation().callActivityOnSaveInstanceState(activity, bundle)
            val parcel = Parcel.obtain()
            try {
                parcel.writeBundle(bundle)
                assertTrue("Diary text must not be serialized into saved instance state", parcel.dataSize() < 65_536)
            } finally { parcel.recycle() }
        }
        compose.activityRule.scenario.recreate()
        waitForTag("editor_body")
        compose.onNodeWithTag("editor_title").assertTextContains("عنوان يبقى")
        compose.onNodeWithTag("editor_body").assertTextContains(longText)
        compose.onNodeWithTag("save_note").performClick()
        compose.waitUntil(15_000) { runBlocking { repository.observeNotes().first().size == 1 } }
        runBlocking {
            val id = repository.observeNotes().first().single().id
            assertEquals(longText, repository.getNote(id)!!.body)
        }
    }

    @Test fun emptySaveBackProtectionAndConfirmedDeleteWorkThroughRealUi() {
        openNew()
        compose.onNodeWithTag("save_note").assertIsNotEnabled()
        compose.onNodeWithTag("editor_body").performTextInput("   \n  ")
        compose.onNodeWithTag("save_note").assertIsNotEnabled()
        compose.onNodeWithTag("editor_body").performTextReplacement("خاطرة عربية حقيقية")
        compose.onNodeWithContentDescription("رجوع").performClick()
        compose.onNodeWithText("قبل أن تغادر…").assertIsDisplayed()
        compose.onNodeWithText("متابعة الكتابة").performClick()
        compose.onNodeWithTag("editor_body").assertTextContains("خاطرة عربية حقيقية")
        compose.onNodeWithTag("save_note").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("حذف الملاحظة").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("حذف الملاحظة").performClick()
        compose.onNodeWithText("إلغاء").performClick()
        compose.onNodeWithText("خاطرة عربية حقيقية").assertIsDisplayed()
        compose.onNodeWithContentDescription("حذف الملاحظة").performClick()
        compose.onNodeWithText("حذف نهائي").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("اكتب أول ملاحظة").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(runBlocking { repository.observeNotes().first().isEmpty() })
    }

    @Test fun masonryUsesDifferentCardHeightsAndCanOpenAnExistingNote() {
        runBlocking {
            repository.save("short", "لحظة صغيرة", "قهوة وهدوء.", true, "s")
            repository.save("long", "ما تعلّمته اليوم", "هناك أيام نتعلّم فيها أن نبطئ قليلًا، لنمنح أفكارنا الوقت والمساحة. ".repeat(8), true, "l")
            repository.save("third", "", "فكرة بلا عنوان، لكنها تستحق أن تبقى.", true, "t")
        }
        waitForTag("note_short")
        waitForTag("note_long")
        val short = compose.onNodeWithTag("note_short").fetchSemanticsNode().boundsInRoot
        val long = compose.onNodeWithTag("note_long").fetchSemanticsNode().boundsInRoot
        assertTrue("Cards must use content height", long.height > short.height)
        assertTrue("Regular phone width must have two columns", long.left != short.left)
        screenshot("home-masonry")
        compose.onNodeWithTag("note_short").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("تعديل الملاحظة").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("تعديل الملاحظة").performClick()
        waitForTag("editor_body")
        compose.onNodeWithTag("editor_title").assertTextContains("لحظة صغيرة")
        screenshot("editor")
        val before = runBlocking { repository.getNote("short")!! }
        compose.onNodeWithTag("editor_body").performTextReplacement("خاطرة معدّلة من الواجهة")
        compose.onNodeWithTag("save_note").performClick()
        compose.waitUntil(15_000) { runBlocking { repository.getNote("short")?.body == "خاطرة معدّلة من الواجهة" } }
        runBlocking {
            val edited = repository.getNote("short")!!
            assertEquals(before.createdAt, edited.createdAt)
            assertTrue(edited.updatedAt > before.updatedAt)
            assertEquals(3, repository.observeNotes().first().size)
        }
    }
}
