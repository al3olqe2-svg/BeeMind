package com.beemind.personal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Parcel
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.beemind.personal.domain.ExpressiveTrait
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
        screenshot("home-empty")
        compose.onNodeWithText("اكتب أول ملاحظة").performClick()
        waitForTag("editor_body")
    }
    private fun screenshot(name: String) {
        val file = File("build/reports/screenshots/$name.png")
        requireNotNull(file.parentFile).mkdirs()
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            val view = if (dialog?.isShowing == true) requireNotNull(dialog.window).decorView
                else compose.activity.window.decorView
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
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("تعديل الملاحظة").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, runBlocking { repository.observeNotes().first().size })
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
        screenshot("unsaved-dialog")
        compose.onNodeWithText("متابعة الكتابة").performClick()
        compose.onNodeWithTag("editor_body").assertTextContains("خاطرة عربية حقيقية")
        compose.onNodeWithTag("save_note").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("حذف الملاحظة").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("حذف الملاحظة").performClick()
        compose.onNodeWithText("حذف هذه الملاحظة؟").assertIsDisplayed()
        screenshot("delete-dialog")
        compose.onNodeWithText("إلغاء").performClick()
        compose.onNodeWithText("خاطرة عربية حقيقية").assertIsDisplayed()
        compose.onNodeWithContentDescription("حذف الملاحظة").performClick()
        compose.onNodeWithText("حذف نهائي").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("اكتب أول ملاحظة").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(runBlocking { repository.observeNotes().first().isEmpty() })
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp")
    fun smallPhoneWithLargeFontKeepsWritingControlsAccessible() {
        org.robolectric.RuntimeEnvironment.setFontScale(1.3f)
        compose.activityRule.scenario.recreate()
        openNew()
        compose.onNodeWithTag("editor_body").performTextInput("نص عربي طويل، مع English 123. ".repeat(50))
        compose.onNodeWithTag("save_note").assertIsDisplayed().assertIsEnabled()
        screenshot("editor-small-large-font")
        chooseTrait("Glow — الوهج")
        compose.onNodeWithTag("save_note").assertIsDisplayed().assertIsEnabled()
    }

    private fun chooseTrait(label: String) {
        compose.onNodeWithText("السمة:", substring = true).performClick()
        compose.onNodeWithText("شخصية الملاحظة").assertIsDisplayed()
        screenshot("trait-picker")
        compose.onNodeWithText(label, useUnmergedTree = true).performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("شخصية الملاحظة").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun expressiveChoiceSurvivesRecreationAndDiscardDoesNotChangeSavedTrait() {
        openNew()
        compose.onNodeWithTag("editor_body").performTextInput("ملاحظة ذات شخصية 🌈")
        chooseTrait("Glow — الوهج")
        compose.activityRule.scenario.recreate()
        waitForTag("editor_body")
        compose.onNodeWithText("السمة: Glow — الوهج").assertIsDisplayed()
        compose.onNodeWithTag("save_note").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("تعديل الملاحظة").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("السمة: Glow — الوهج").assertIsDisplayed()
        compose.onNodeWithContentDescription("تعديل الملاحظة").performClick()
        waitForTag("editor_body")
        chooseTrait("Anger — الغضب")
        compose.onNodeWithContentDescription("رجوع").performClick()
        compose.onNodeWithText("قبل أن تغادر…").assertIsDisplayed()
        compose.onNodeWithText("تجاهل التعديلات").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("تعديل الملاحظة").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(ExpressiveTrait.GLOW, runBlocking { repository.getNote(repository.observeNotes().first().single().id)!!.expressiveTrait })
        compose.onNodeWithContentDescription("تعديل الملاحظة").performClick()
        waitForTag("editor_body")
        chooseTrait("Anger — الغضب")
        compose.onNodeWithTag("save_note").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("تعديل الملاحظة").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("السمة: Anger — الغضب").assertIsDisplayed()
        compose.onNodeWithContentDescription("تعديل الملاحظة").performClick()
        waitForTag("editor_body")
        chooseTrait("بدون سمة")
        compose.onNodeWithTag("save_note").performClick()
        // Drain the paused Android main looper via UI synchronization before reading Room.
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("تعديل الملاحظة").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(ExpressiveTrait.NONE, runBlocking { repository.observeNotes().first().single().expressiveTrait })
        compose.onNodeWithContentDescription("رجوع").performClick()
        val id = runBlocking { repository.observeNotes().first().single().id }
        waitForTag("note_$id")
        compose.onNodeWithTag("note_$id").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "بدون سمة"))
    }

    @Test fun expressiveCardsKeepIdenticalBoundsAndReadableRtlContent() {
        runBlocking {
            for (trait in ExpressiveTrait.entries) repository.save(trait.name, "فكرة عربية", "محتوى مقروء", true, trait.name, trait)
        }
        waitForTag("note_NONE")
        val bounds = ExpressiveTrait.entries.map { trait ->
            compose.onNodeWithTag("note_" + trait.name).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        assertEquals(bounds[0].height, bounds[1].height)
        assertEquals(bounds[0].height, bounds[2].height)
        screenshot("home-expressive-traits")
        compose.onNodeWithTag("note_GLOW").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("السمة: Glow — الوهج").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun reminderSheetSavesOnlyWithNoteAndCoexistsWithGlow() {
        openNew()
        compose.onNodeWithTag("editor_body").performTextInput("فكرة لها موعد")
        chooseTrait("Glow — الوهج")
        compose.onNodeWithText("＋ تذكير").performClick()
        compose.onNodeWithText("5 د").performClick()
        compose.onNodeWithText("+5 دقائق").performClick()
        screenshot("reminder-sheet")
        assertTrue(runBlocking { repository.observeNotes().first().isEmpty() })
        compose.onNodeWithText("اعتماد الموعد في المسودة").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("اعتماد الموعد في المسودة").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("◷ تذكير مفعّل").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        waitForTag("editor_body")
        compose.onNodeWithText("◷ تذكير مفعّل").assertIsDisplayed()
        compose.onNodeWithTag("save_note").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("◷ Reminder — التذكير").fetchSemanticsNodes().isNotEmpty() }
        screenshot("reminder-details")
        val note = runBlocking { repository.getNote(repository.observeNotes().first().single().id)!! }
        assertEquals(ExpressiveTrait.GLOW, note.expressiveTrait)
        assertNotNull(note.reminder)
        assertTrue(note.reminder!!.dueAt > System.currentTimeMillis() + 500_000)
        compose.onNodeWithText("إنجاز").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("تم إنجازه").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(note.createdAt, runBlocking { repository.getNote(note.id)!!.createdAt })
    }

    @Test
    @Config(sdk = [28], qualifiers = "w320dp-h640dp")
    fun calendarAndTimePickersFitSmallPhone() {
        openNew()
        compose.onNodeWithTag("editor_body").performTextInput("موعد بالتاريخ والوقت")
        compose.onNodeWithText("＋ تذكير").performClick()
        compose.onNodeWithText("غدًا").performScrollTo().performClick()
        compose.onNodeWithText("التاريخ:", substring = true).performScrollTo().performClick()
        screenshot("reminder-date-small")
        compose.onNodeWithText("اختيار").performClick()
        compose.onNodeWithText("الوقت:", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("الساعة والدقيقة").assertIsDisplayed()
        screenshot("reminder-time-small")
        compose.onNodeWithText("اختيار").performClick()
        compose.onNodeWithText("اعتماد الموعد في المسودة").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("اعتماد الموعد في المسودة").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("◷ تذكير مفعّل").assertIsDisplayed()
    }

    @Test fun launcherWingRendersWithAndroidDrawable() {
        val context = compose.activity
        for ((resource, name) in listOf(R.mipmap.ic_launcher to "launcher", R.drawable.ic_wing_mono to "wing-monochrome")) {
            for (size in listOf(48, 192)) {
                val drawable = requireNotNull(context.getDrawable(resource))
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(Canvas(bitmap))
                val file = File("build/reports/screenshots/$name-$size.png")
                requireNotNull(file.parentFile).mkdirs()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }

    @Test fun masonryUsesDifferentCardHeightsAndCanOpenAnExistingNote() {
        runBlocking {
            repository.save("short", "لحظة صغيرة", "قهوة وهدوء.", true, "s")
            repository.save("long", "ما تعلّمته اليوم", "هناك أيام نتعلّم فيها أن نبطئ قليلًا، لنمنح أفكارنا الوقت والمساحة. ".repeat(8), true, "l", ExpressiveTrait.GLOW)
            repository.save("third", "", "فكرة بلا عنوان، لكنها تستحق أن تبقى.", true, "t", ExpressiveTrait.ANGER)
        }
        waitForTag("note_short")
        waitForTag("note_long")
        val short = compose.onNodeWithTag("note_short").fetchSemanticsNode().boundsInRoot
        val long = compose.onNodeWithTag("note_long").fetchSemanticsNode().boundsInRoot
        assertTrue("Cards must use content height", long.height > short.height)
        assertTrue("Regular phone width must have two columns", long.left != short.left)
        screenshot("home-masonry")
        compose.onNodeWithTag("note_short").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(220)
        screenshot("home-card-pressed")
        compose.onNodeWithTag("note_short").performTouchInput { cancel() }
        compose.onNodeWithTag("note_short").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("تعديل الملاحظة").fetchSemanticsNodes().isNotEmpty() }
        screenshot("detail")
        compose.onNodeWithContentDescription("تعديل الملاحظة").performClick()
        waitForTag("editor_body")
        compose.onNodeWithTag("editor_title").assertTextContains("لحظة صغيرة")
        screenshot("editor")
        val before = runBlocking { repository.getNote("short")!! }
        compose.onNodeWithTag("editor_body").performTextReplacement("خاطرة معدّلة من الواجهة")
        compose.onNodeWithTag("save_note").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("تعديل الملاحظة").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("خاطرة معدّلة من الواجهة", runBlocking { repository.getNote("short")!!.body })
        runBlocking {
            val edited = repository.getNote("short")!!
            assertEquals(before.createdAt, edited.createdAt)
            assertTrue(edited.updatedAt > before.updatedAt)
            assertEquals(3, repository.observeNotes().first().size)
        }
    }
    @Test fun capsuleReviewLocksEditorDetailsGridAndAccessibilityThenOpensWithoutRestart() {
        openNew()
        compose.onNodeWithTag("editor_title").performTextInput("عنوان كبسولتي السري")
        compose.onNodeWithTag("editor_body").performTextInput("محتوى لا يظهر قبل الموعد")
        compose.onNodeWithText("◈ كبسولة زمنية").performClick()
        compose.onNodeWithText("2 د").performClick()
        compose.onNodeWithText("اعتماد الموعد في المسودة").performScrollTo().performClick()
        compose.onNodeWithTag("save_note").performClick()
        compose.onNodeWithText("إغلاق رسالتك إلى المستقبل؟").assertIsDisplayed()
        screenshot("stage4-capsule-review")
        compose.onNodeWithText("تأكيد الإغلاق والحفظ").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("كبسولة مغلقة").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("عنوان كبسولتي السري", substring = true, useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithText("محتوى لا يظهر قبل الموعد", substring = true, useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithContentDescription("تعديل الملاحظة").assertIsNotEnabled()
        screenshot("stage4-capsule-locked")
        compose.onNodeWithContentDescription("رجوع").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithTag("new_note").fetchSemanticsNodes().isNotEmpty() }
        screenshot("stage4-capsule-home")
        val id = runBlocking { repository.observeNotes().first().single().id }
        compose.onNodeWithTag("note_$id").performClick()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15000) { compose.onAllNodesWithText("كبسولة مغلقة").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("عنوان كبسولتي السري", substring = true, useUnmergedTree = true).assertCountEquals(0)
        // Simulate the deadline using synthetic fixture metadata, preserving all stored text.
        runBlocking {
            val app = ApplicationProvider.getApplicationContext<BeeMindApplication>()
            app.database.notesDao().seal(id, System.currentTimeMillis() - 1)
            repository.refreshTime()
        }
        compose.waitUntil(15000) { compose.onAllNodesWithText("عنوان كبسولتي السري").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("محتوى لا يظهر قبل الموعد").assertIsDisplayed()
        compose.onNodeWithContentDescription("تعديل الملاحظة").assertIsEnabled()
        screenshot("stage4-capsule-unlocked")
    }

    @Test fun resurfaceBadgePersistsOnGridUntilReadableDetailsAreOpened() {
        runBlocking {
            repository.save("older", "فكرة تعود", "محتوى الفكرة", true, "s1", ExpressiveTrait.ANGER)
            repository.save("newer", "فكرة جديدة", "الأحدث", true, "s2")
            val app = ApplicationProvider.getApplicationContext<BeeMindApplication>()
            app.database.notesDao().reschedule("older", System.currentTimeMillis() - 1)
            repository.refreshTime()
        }
        waitForTag("note_older")
        compose.waitUntil(15000) { compose.onAllNodesWithText("↗ عادت للواجهة").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("older", runBlocking { repository.observeNotes().first().first().id })
        assertNull(runBlocking { repository.getNote("older")!!.resurfaceSeenAt })
        screenshot("stage4-resurface-due")
        compose.onNodeWithTag("note_older").performClick()
        compose.waitUntil(15000) { runBlocking { repository.getNote("older")!!.resurfaceSeenAt != null } }
        compose.onNodeWithContentDescription("رجوع").performClick()
        waitForTag("note_newer")
        compose.onAllNodesWithText("↗ عادت للواجهة").assertCountEquals(0)
        assertEquals("newer", runBlocking { repository.observeNotes().first().first().id })
        assertEquals(ExpressiveTrait.ANGER, runBlocking { repository.getNote("older")!!.expressiveTrait })
    }

}
