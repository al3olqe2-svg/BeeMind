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
        try { compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
        catch (e: androidx.compose.ui.test.ComposeTimeoutException) { screenshot("failure-$tag"); throw e }
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

    private fun openNew() {
        waitForTag("new_note"); screenshot("home-empty")
        compose.onNodeWithTag("new_note").performClick(); waitForTag("editor_body")
        compose.onAllNodesWithTag("editor_title").assertCountEquals(0)
    }
    private fun home() { waitForTag("new_note") }
    private fun openNote(id: String) {
        waitForTag("note_$id"); compose.onNodeWithTag("note_$id").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("تعديل الملاحظة").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun traits() { compose.onNodeWithTag("editor_tools").performClick() }
    private fun closeTraits() {
        if (compose.onAllNodesWithContentDescription("إغلاق الأدوات").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithTag("editor_tools").performClick()
    }
    private fun chooseTrait(label: String) {
        traits(); compose.onNodeWithTag("tool_expressive").performClick()
        val value = when(label) { "Glow — الوهج" -> "GLOW"; "Anger — الغضب" -> "ANGER"; else -> "NONE" }
        compose.onNodeWithTag("expressive_$value").performClick(); closeTraits()
    }
    private fun exitEditorHome() {
        closeTraits(); compose.onNodeWithContentDescription("رجوع").performClick()
        compose.waitUntil(15000) {
            compose.onAllNodesWithTag("editor_body").fetchSemanticsNodes().isEmpty() &&
                (compose.onAllNodesWithTag("new_note").fetchSemanticsNodes().isNotEmpty() ||
                 compose.onAllNodesWithContentDescription("تعديل الملاحظة").fetchSemanticsNodes().isNotEmpty())
        }
        if (compose.onAllNodesWithContentDescription("تعديل الملاحظة").fetchSemanticsNodes().isNotEmpty()) back()
        home()
    }
    private fun temporal(tag: String) { traits(); compose.onNodeWithTag(tag).performClick() }
    private fun threadPanel() {
        waitForTag("manage_threads"); compose.onNodeWithTag("manage_threads").performClick()
        waitForTag("add_thread")
    }
    private fun back() { compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() } }

    @Test fun progressiveToolsKeepWritingAndRequireExplicitCapsuleConfirmation() {
        openNew(); screenshot("r3-normal-empty")
        compose.onAllNodesWithTag("save_note").assertCountEquals(0)
        compose.onAllNodesWithText("السمات").assertCountEquals(0)
        val body = "أمنح أفكاري مساحة هادئة للكتابة.\nالضوء يمرّ، وتبقى الكلمات واضحة.\n".repeat(4)
        compose.onNodeWithTag("editor_body").performTextInput(body)
        screenshot("r3-normal-arabic")
        traits(); screenshot("r3-primary")
        for (tag in listOf("tool_expressive", "tool_reminder", "tool_resurface", "tool_capsule")) compose.onNodeWithTag(tag).assertIsDisplayed()
        compose.onNodeWithTag("tool_expressive").performClick()
        for (tag in listOf("tool_reminder", "tool_resurface", "tool_capsule")) compose.onAllNodesWithTag(tag).assertCountEquals(0)
        screenshot("r3-expressive")
        for (value in listOf("GLOW", "ANGER", "NONE")) {
            compose.onNodeWithTag("expressive_$value").performClick()
            compose.onNodeWithTag("expressive_$value").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "محدد"))
            compose.onNodeWithTag("editor_body").assertTextContains(body)
            screenshot("r3-$value")
        }
        back(); compose.onNodeWithTag("tool_reminder").assertIsDisplayed()
        compose.onNodeWithTag("tool_reminder").performClick()
        compose.onNodeWithTag("appointment_plus").performClick(); screenshot("r3-reminder-pending")
        compose.onNodeWithTag("appointment_date").performClick(); screenshot("r3-date")
        compose.onNodeWithTag("appointment_dialog_cancel").performClick()
        compose.onNodeWithTag("appointment_time").performClick(); screenshot("r3-clock")
        compose.onNodeWithTag("appointment_dialog_cancel").performClick()
        back(); compose.onNodeWithTag("tool_capsule").performClick()
        compose.onNodeWithTag("appointment_plus").performClick()
        compose.onNodeWithTag("appointment_confirm").performScrollTo().performClick()
        screenshot("r3-capsule-confirmation")
        compose.onNodeWithText("متابعة الكتابة").performClick()
        compose.onNodeWithTag("editor_body").assertTextContains(body)
        assertTrue(runBlocking { repository.observeNotes().first().none { it.locked } })
        back(); back() // category -> primary -> closed, never exits the editor
        compose.onNodeWithTag("editor_body").assertExists()
        exitEditorHome()
        assertEquals(body, runBlocking { repository.getNote(repository.observeNotes().first().single().id)!!.body })
    }
    @Test fun temporalCategoriesRetainIndependentDeadlinesAndRemoveThroughSave() {
        openNew(); compose.onNodeWithTag("editor_body").performTextInput("مواعيد مستقلة")
        temporal("tool_reminder"); compose.onNodeWithTag("appointment_plus").performClick()
        val reminderSummary = compose.onNodeWithTag("appointment_summary").fetchSemanticsNode().config[SemanticsProperties.Text].toString()
        compose.onNodeWithTag("appointment_confirm").performScrollTo().performClick()
        temporal("tool_resurface"); repeat(4) { compose.onNodeWithTag("appointment_plus").performClick() }
        val resurfaceSummary = compose.onNodeWithTag("appointment_summary").fetchSemanticsNode().config[SemanticsProperties.Text].toString()
        assertNotEquals(reminderSummary, resurfaceSummary)
        compose.onNodeWithTag("appointment_confirm").performScrollTo().performClick()
        temporal("tool_reminder")
        assertEquals(reminderSummary, compose.onNodeWithTag("appointment_summary").fetchSemanticsNode().config[SemanticsProperties.Text].toString())
        compose.onNodeWithTag("appointment_confirm").performScrollTo().performClick()
        exitEditorHome()
        val saved = runBlocking { repository.getNote(repository.observeNotes().first().single().id)!! }
        assertTrue(saved.resurfaceAt!! > saved.reminder!!.dueAt)
        openNote(saved.id); compose.onNodeWithContentDescription("تعديل الملاحظة").performClick(); waitForTag("editor_body")
        temporal("tool_capsule"); compose.onNodeWithTag("appointment_plus").performClick()
        back(); compose.onNodeWithTag("tool_resurface").performClick()
        assertEquals(resurfaceSummary, compose.onNodeWithTag("appointment_summary").fetchSemanticsNode().config[SemanticsProperties.Text].toString())
        compose.onNodeWithTag("appointment_remove").performScrollTo().performClick()
        temporal("tool_reminder"); compose.onNodeWithTag("appointment_remove").performScrollTo().performClick()
        exitEditorHome()
        val removed = runBlocking { repository.getNote(saved.id)!! }
        assertEquals("CANCELLED", removed.reminder!!.status); assertNull(removed.resurfaceAt)
        assertNull(removed.unlockAt); assertEquals(saved.createdAt, removed.createdAt)
        assertEquals(saved.body, removed.body)
    }
    @Test fun emptyCompositionContainsOnlyIdentityAndAddAndEmptyBackCreatesNothing() {
        home(); compose.onNodeWithTag("rainbow_line").assertExists()
        compose.onAllNodesWithText("BeeMind").assertCountEquals(0)
        compose.onAllNodesWithText("اكتب أول ملاحظة").assertCountEquals(0)
        screenshot("home-minimal")
        openNew(); compose.onAllNodesWithTag("save_note").assertCountEquals(0)
        compose.onNodeWithTag("editor_body").performTextInput("  \n ")
        back(); home(); assertTrue(runBlocking { repository.observeNotes().first().isEmpty() })
    }
    @Test fun longTitlelessEditorSurvivesActivityRecreationWithoutBundlingText() {
        openNew()
        val text = "يومياتي 🌈 مع أسطر متعددة.\n".repeat(2500)
        compose.onNodeWithTag("editor_body").performTextInput(text)
        compose.activityRule.scenario.onActivity { activity ->
            val bundle = Bundle(); InstrumentationRegistry.getInstrumentation().callActivityOnSaveInstanceState(activity, bundle)
            val parcel = Parcel.obtain()
            try { parcel.writeBundle(bundle); assertTrue(parcel.dataSize() < 65536) } finally { parcel.recycle() }
        }
        compose.activityRule.scenario.recreate(); waitForTag("editor_body")
        compose.onNodeWithTag("editor_body").assertTextContains(text)
        exitEditorHome()
        val note = runBlocking { repository.getNote(repository.observeNotes().first().single().id)!! }
        assertEquals(text, note.body); assertNull(note.title)
    }
    @Test fun backAutosavesAndManualSaveReturnsHomeAndDeleteRequiresConfirmation() {
        openNew(); compose.onNodeWithTag("editor_body").performTextInput("آخر حرف ي")
        compose.onNodeWithContentDescription("رجوع").performClick(); home()
        compose.onAllNodesWithText("قبل أن تغادر…").assertCountEquals(0)
        val first = runBlocking { repository.getNote(repository.observeNotes().first().single().id)!! }
        openNote(first.id); screenshot("detail-minimal")
        compose.onAllNodesWithText("السمة:", substring = true).assertCountEquals(0)
        compose.onNodeWithContentDescription("تعديل الملاحظة").performClick(); waitForTag("editor_body")
        compose.onNodeWithTag("editor_body").performTextReplacement("تعديل محفوظ")
        exitEditorHome()
        assertEquals(1, runBlocking { repository.observeNotes().first().size })
        val edited = runBlocking { repository.getNote(first.id)!! }
        assertEquals(first.createdAt, edited.createdAt); assertEquals("تعديل محفوظ", edited.body)
        openNote(first.id); compose.onNodeWithContentDescription("حذف الملاحظة").performClick()
        compose.onNodeWithText("حذف هذه الملاحظة؟").assertIsDisplayed()
        compose.onNodeWithText("إلغاء").performClick()
        compose.onNodeWithText("تعديل محفوظ").assertIsDisplayed()
        compose.onNodeWithContentDescription("حذف الملاحظة").performClick()
        compose.onNodeWithText("حذف نهائي").performClick(); home()
        assertTrue(runBlocking { repository.observeNotes().first().isEmpty() })
    }
    @Test @Config(qualifiers = "w320dp-h640dp")
    fun smallPhoneLargeFontKeepsEditorAndTraitsAccessible() {
        org.robolectric.RuntimeEnvironment.setFontScale(1.5f); compose.activityRule.scenario.recreate()
        openNew(); compose.onNodeWithTag("editor_body").performTextInput("عربي English 123. ".repeat(50))
        chooseTrait("Glow — الوهج")
        compose.onNodeWithTag("editor_tools").assertIsDisplayed().assertIsEnabled()
        screenshot("editor-small-large-font")
        exitEditorHome()
        assertEquals(ExpressiveTrait.GLOW, runBlocking { repository.observeNotes().first().single().expressiveTrait })
    }
    @Test fun glowAngerAndRemovalRemainExclusiveAndPersistent() {
        openNew(); compose.onNodeWithTag("editor_body").performTextInput("شخصية مرئية")
        chooseTrait("Glow — الوهج"); exitEditorHome()
        val id = runBlocking { repository.observeNotes().first().single().id }
        for ((label, trait) in listOf("Anger — الغضب" to ExpressiveTrait.ANGER, "إزالة السمة" to ExpressiveTrait.NONE)) {
            openNote(id); compose.onNodeWithContentDescription("تعديل الملاحظة").performClick(); waitForTag("editor_body")
            chooseTrait(label); compose.onNodeWithContentDescription("رجوع").performClick()
            // Back returns to the existing reading screen; Save returns home.
            compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("تعديل الملاحظة").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(trait, runBlocking { repository.getNote(id)!!.expressiveTrait }); back(); home()
        }
        compose.onNodeWithTag("note_$id").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, ""))
        screenshot("home-expressive-traits")
    }
    @Test fun reminderPlusFiveAccumulatesAndExistingDeadlineIsPreserved() {
        openNew(); compose.onNodeWithTag("editor_body").performTextInput("لها موعد")
        temporal("tool_reminder")
        compose.onNodeWithText("+5").performClick(); compose.onNodeWithText("5 د").assertExists()
        compose.onNodeWithText("+5").performClick(); compose.onNodeWithText("10 د").assertExists()
        compose.onNodeWithText("+5").performClick(); compose.onNodeWithText("15 د").assertExists()
        compose.onNodeWithText("+5").performClick(); compose.onNodeWithText("20 د").assertExists()
        screenshot("time-minimal")
        compose.onNodeWithText("اعتماد").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("+5").fetchSemanticsNodes().isEmpty() }
        closeTraits(); exitEditorHome()
        val saved = runBlocking { repository.getNote(repository.observeNotes().first().single().id)!! }
        assertTrue(saved.reminder!!.dueAt > System.currentTimeMillis() + 1100000)
        openNote(saved.id); compose.onNodeWithContentDescription("تعديل الملاحظة").performClick(); waitForTag("editor_body")
        temporal("tool_reminder")
        compose.onAllNodesWithText("5 د").assertCountEquals(0)
        compose.onNodeWithText("اعتماد").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("+5").fetchSemanticsNodes().isEmpty() }
        closeTraits(); exitEditorHome()
        assertEquals(saved.reminder!!.dueAt, runBlocking { repository.getNote(saved.id)!!.reminder!!.dueAt })
        assertEquals(saved.reminder!!.generation, runBlocking { repository.getNote(saved.id)!!.reminder!!.generation })
        // Reconfirming the clock's unchanged visible hour/minute must not zero its hidden seconds.
        openNote(saved.id); compose.onNodeWithContentDescription("تعديل الملاحظة").performClick(); waitForTag("editor_body")
        temporal("tool_reminder")
        compose.onNodeWithTag("appointment_time").performClick()
        compose.onNodeWithText("اختيار").performClick()
        compose.onNodeWithText("اعتماد").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("+5").fetchSemanticsNodes().isEmpty() }
        closeTraits(); exitEditorHome()
        assertEquals(saved.reminder, runBlocking { repository.getNote(saved.id)!!.reminder })
    }
    @Test @Config(qualifiers = "w320dp-h640dp")
    fun calendarAndTimeAreAvailableOnSmallPhone() {
        openNew(); temporal("tool_reminder")
        compose.onNodeWithContentDescription("اختيار التاريخ").performClick(); screenshot("date-small")
        compose.onNodeWithText("اختيار").performClick()
        screenshot("r3-temporal-error")
        compose.onNodeWithContentDescription("اختيار الوقت").performClick()
        compose.onNodeWithText("الساعة والدقيقة").assertIsDisplayed(); screenshot("time-small")
        compose.onNodeWithText("اختيار").performClick()
        compose.onNodeWithText("+5").performClick()
        compose.onNodeWithText("اعتماد").performScrollTo().assertIsEnabled()
    }
    @Test fun capsuleRemainsPrivateInHomeReadingAndEditorUntilDeadline() {
        openNew(); compose.onNodeWithTag("editor_body").performTextInput("نص محجوب")
        temporal("tool_capsule")
        compose.onNodeWithText("+5").performClick(); compose.onNodeWithText("اعتماد").performScrollTo().performClick()
        compose.onNodeWithTag("capsule_seal_confirm").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("+5").fetchSemanticsNodes().isEmpty() }
        closeTraits(); exitEditorHome()
        compose.onAllNodesWithText("نص محجوب", substring = true, useUnmergedTree = true).assertCountEquals(0)
        val id = runBlocking { repository.observeNotes().first().single().id }
        openNote(id); compose.onNodeWithContentDescription("تعديل الملاحظة").assertIsNotEnabled()
        compose.onAllNodesWithText("نص محجوب", substring = true, useUnmergedTree = true).assertCountEquals(0)
        screenshot("capsule-locked")
        runBlocking { ApplicationProvider.getApplicationContext<BeeMindApplication>().database.notesDao().seal(id, System.currentTimeMillis() - 1); repository.refreshTime() }
        compose.waitUntil(15000) { compose.onAllNodesWithText("نص محجوب").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("تعديل الملاحظة").assertIsEnabled()
    }
    @Test fun resurfaceOrderingAndConsumptionSurviveQuietCards() {
        runBlocking {
            repository.save("older", "", "قديم", true, "s1", ExpressiveTrait.ANGER)
            repository.save("newer", "", "جديد", true, "s2")
            ApplicationProvider.getApplicationContext<BeeMindApplication>().database.notesDao().reschedule("older", System.currentTimeMillis() - 1)
            repository.refreshTime()
        }
        waitForTag("note_older"); assertEquals("older", runBlocking { repository.observeNotes().first().first().id })
        val before = runBlocking { repository.getNote("older")!!.createdAt }
        openNote("older")
        compose.waitUntil(15000) { runBlocking { repository.getNote("older")!!.resurfaceSeenAt != null } }
        back(); home()
        assertEquals("newer", runBlocking { repository.observeNotes().first().first().id })
        assertEquals(before, runBlocking { repository.getNote("older")!!.createdAt })
    }
    @Test fun threadsButtonOpensPickerAndMutualLinksNavigateAndUnlink() {
        runBlocking {
            repository.save("a", "", "نص أ", true, "a"); repository.save("b", "", "نص ب", true, "b")
        }
        openNote("a"); threadPanel(); compose.onNodeWithTag("add_thread").performClick()
        waitForTag("thread_candidate_b"); compose.onNodeWithTag("thread_candidate_b").performClick()
        compose.waitUntil(15000) { runBlocking { repository.observeThreadNotes("a", true).first().size == 1 } }
        compose.onNodeWithContentDescription("إغلاق اختيار الخيوط").performClick()
        waitForTag("thread_open_b"); compose.onNodeWithTag("thread_open_b").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("نص ب").fetchSemanticsNodes().isNotEmpty() }
        threadPanel(); waitForTag("thread_open_a")
        compose.onNodeWithTag("thread_remove_a").performClick()
        compose.waitUntil(15000) { runBlocking { repository.observeThreadNotes("a", true).first().isEmpty() } }
        assertEquals("نص أ", runBlocking { repository.getNote("a")!!.body })
    }
    @Test fun threadsNeverExposeLockedTextInSearchOrNavigation() {
        runBlocking {
            repository.save("plain", "", "متاح", true, "p")
            repository.save("capsule", "عنوان محجوب", "نص محجوب", true, "c", temporalEdit = com.beemind.personal.domain.TemporalEdit(sealUntil = System.currentTimeMillis() + 300000))
        }
        openNote("plain"); threadPanel(); compose.onNodeWithTag("add_thread").performClick()
        waitForTag("thread_candidate_capsule")
        compose.onAllNodesWithText("محجوب", substring = true, useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithTag("thread_search").performTextInput("محجوب")
        compose.onAllNodesWithTag("thread_candidate_capsule").assertCountEquals(0)
        compose.onNodeWithTag("thread_search").performTextClearance(); waitForTag("thread_candidate_capsule")
        compose.onNodeWithTag("thread_candidate_capsule").performClick()
        compose.waitUntil(15000) { runBlocking { repository.observeThreadNotes("plain", true).first().size == 1 } }
        compose.onNodeWithContentDescription("إغلاق اختيار الخيوط").performClick()
        waitForTag("thread_open_capsule"); compose.onNodeWithTag("thread_open_capsule").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("تعديل الملاحظة").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("تعديل الملاحظة").assertIsNotEnabled()
        compose.onAllNodesWithText("محجوب", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }


    @Test fun actualTypingDebounceCommitsAndManualSaveDoesNotDuplicate() {
        openNew(); compose.onNodeWithTag("editor_body").performTextInput("حفظ تلقائي أثناء الكتابة")
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(1, java.util.concurrent.TimeUnit.SECONDS)
        compose.waitUntil(15000) {
            // Robolectric's paused Android Handler clock is separate from Compose frames.
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(100, java.util.concurrent.TimeUnit.MILLISECONDS)
            compose.waitForIdle()
            runBlocking { repository.observeNotes().first().isNotEmpty() }
        }
        val id = runBlocking { repository.observeNotes().first().single().id }
        compose.onNodeWithTag("editor_body").performTextInput(" وآخر حرف")
        exitEditorHome()
        assertEquals(1, runBlocking { repository.observeNotes().first().size })
        assertEquals("حفظ تلقائي أثناء الكتابة وآخر حرف", runBlocking { repository.getNote(id)!!.body })
    }
    @Test fun reminderDoneAndSnoozeRemainReachableInEditorWithoutChangingTextOrCreation() {
        runBlocking { repository.save("reminder", "", "تذكير باقٍ", true, "r", ExpressiveTrait.GLOW,
            com.beemind.personal.domain.ReminderEdit(true, System.currentTimeMillis() + 3600000)) }
        val initial = runBlocking { repository.getNote("reminder")!! }
        openNote("reminder"); compose.onNodeWithContentDescription("تعديل الملاحظة").performClick(); waitForTag("editor_body")
        temporal("tool_reminder"); compose.onNodeWithTag("reminder_info").performClick(); compose.onNodeWithText("تأجيل 10 دقائق").performScrollTo().performClick()
        compose.waitUntil(15000) { runBlocking { repository.getNote("reminder")!!.reminder!!.status == "SNOOZED" } }
        val snoozed = runBlocking { repository.getNote("reminder")!!.reminder!! }
        // Wait for the UI to observe Snooze's new generation, not just the Room commit.
        compose.waitUntil(15000) {
            compose.onAllNodesWithText(com.beemind.personal.ui.components.reminderTimestamp(snoozed.dueAt)).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodes(hasText("إنجاز") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("إنجاز").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("تم إنجازه").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("تم").performClick(); closeTraits(); exitEditorHome()
        val note = runBlocking { repository.getNote("reminder")!! }
        assertEquals(initial.body, note.body); assertEquals(initial.createdAt, note.createdAt)
        assertEquals("DONE", note.reminder!!.status); assertEquals(ExpressiveTrait.GLOW, note.expressiveTrait)
    }
    @Test fun masonryHeightsAndLegacyTitleStorageRemainIntactWhileTitlesStayHidden() {
        runBlocking {
            repository.save("short", "عنوان داخلي", "قهوة وهدوء.", true, "s")
            repository.save("long", "عنوان آخر", "هناك أيام نمنح فيها أفكارنا الوقت والمساحة. ".repeat(30), true, "l", ExpressiveTrait.GLOW)
        }
        waitForTag("note_short"); waitForTag("note_long")
        val short = compose.onNodeWithTag("note_short").fetchSemanticsNode().boundsInRoot
        val long = compose.onNodeWithTag("note_long").fetchSemanticsNode().boundsInRoot
        assertEquals(com.beemind.personal.ui.notes.homeCardSize("long").heightDp.toFloat() / com.beemind.personal.ui.notes.homeCardSize("short").heightDp, long.height / short.height, .02f); assertTrue(short.left != long.left)
        compose.onAllNodesWithText("عنوان داخلي").assertCountEquals(0)
        screenshot("home-masonry")
        openNote("short"); compose.onAllNodesWithText("عنوان داخلي").assertCountEquals(0)
        compose.onNodeWithContentDescription("تعديل الملاحظة").performClick(); waitForTag("editor_body")
        compose.onAllNodesWithTag("editor_title").assertCountEquals(0)
        compose.onNodeWithTag("editor_body").performTextReplacement("تعديل المتن")
        exitEditorHome()
        assertEquals("عنوان داخلي", runBlocking { repository.getNote("short")!!.title })
        assertEquals("تعديل المتن", runBlocking { repository.getNote("short")!!.body })
    }
    @Test fun cyclicThreadNavigationAndAndroidBackUseTheCorrectDestination() {
        runBlocking {
            for (id in listOf("a", "b", "c")) repository.save(id, "", "محتوى $id", true, id)
            repository.setLinked("a", "b", true); repository.setLinked("b", "c", true); repository.setLinked("c", "a", true)
        }
        openNote("a")
        repeat(3) {
            threadPanel(); waitForTag("thread_open_b"); compose.onNodeWithTag("thread_open_b").performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithText("محتوى b").fetchSemanticsNodes().isNotEmpty() }
            threadPanel(); waitForTag("thread_open_a"); compose.onNodeWithTag("thread_open_a").performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithText("محتوى a").fetchSemanticsNodes().isNotEmpty() }
            back()
            compose.waitUntil(15000) { compose.onAllNodesWithText("محتوى b").fetchSemanticsNodes().isNotEmpty() }
            back()
            compose.waitUntil(15000) { compose.onAllNodesWithText("محتوى a").fetchSemanticsNodes().isNotEmpty() }
        }
        assertEquals(2, runBlocking { repository.observeThreadNotes("a", true).first().size })
    }
    @Test fun reminderIntentFromThreadDetailsUsesTargetViewModelAndPreservesGeneration() {
        runBlocking {
            repository.save("a", "", "البداية", true, "a")
            repository.save("b", "", "هدف الإشعار", true, "b", ExpressiveTrait.GLOW,
                com.beemind.personal.domain.ReminderEdit(true, System.currentTimeMillis() + 3600000))
            repository.setLinked("a", "b", true)
        }
        openNote("a")
        val generation = runBlocking { repository.getNote("b")!!.reminder!!.generation }
        compose.runOnIdle { compose.activity.reminderOpen.value = "b" to generation }
        compose.waitUntil(15000) { compose.onAllNodesWithText("هدف الإشعار").fetchSemanticsNodes().isNotEmpty() }
        threadPanel(); waitForTag("thread_open_a")
        compose.onAllNodesWithTag("thread_open_b").assertCountEquals(0)
        assertEquals(generation, runBlocking { repository.getNote("b")!!.reminder!!.generation })
    }
}
