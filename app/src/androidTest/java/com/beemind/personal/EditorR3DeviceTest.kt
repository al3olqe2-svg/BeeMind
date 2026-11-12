package com.beemind.personal

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.beemind.personal.data.RoomNotesRepository
import com.beemind.personal.data.local.BeeMindDatabase
import com.beemind.personal.ui.notes.*
import com.beemind.personal.ui.theme.BeeMindTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Actual Compose input, native IME/insets and HWUI captures; isolated ephemeral Room. */
@RunWith(AndroidJUnit4::class)
class EditorR3DeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun nativeEditorProgressionKeyboardAndCapsulePrivacy() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val db = Room.inMemoryDatabaseBuilder(context, BeeMindDatabase::class.java).build()
        val repo = RoomNotesRepository(db)
        val store = ViewModelStore()
        lateinit var vm: EditorViewModel
        compose.runOnUiThread { compose.activity.enableEdgeToEdge(); vm = EditorViewModel(repo, "r3-native", null); store.put("editor", vm) }
        fun capture(name: String) {
            compose.waitForIdle()
            instrumentation.waitForIdleSync()
            Thread.sleep(500)
            instrumentation.waitForIdleSync()
            val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            File(context.filesDir, "r3-native-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        try {
            compose.setContent { BeeMindTheme { val state by vm.state.collectAsState(); EditorScreen(vm, state, {}, {}) } }
            compose.waitUntil(15000) { !vm.state.value.loading }
            compose.onNodeWithTag("editor_body").assertExists()
            compose.onAllNodesWithTag("save_note").assertCountEquals(0)
            capture("normal-empty")
            val body = "هذه مساحة للأفكار، ولكل ما يستحق أن يُكتب.\n".repeat(30)
            compose.onNodeWithTag("editor_body").performTextInput(body)
            compose.waitUntil(10000) {
                var visible = false
                compose.runOnIdle {
                    visible = androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                        ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
                }
                visible
            }
            compose.onNodeWithTag("editor_tools").assertIsDisplayed()
            capture("normal-long-ime")
            compose.onNodeWithTag("editor_tools").performClick(); capture("primary-ime")
            compose.onNodeWithTag("tool_expressive").performClick()
            compose.onAllNodesWithTag("tool_reminder").assertCountEquals(0)
            capture("expressive")
            for (value in listOf("GLOW", "ANGER", "NONE")) {
                compose.onNodeWithTag("expressive_$value").performClick(); capture(value.lowercase())
                compose.onNodeWithTag("editor_body").assertTextContains(body)
            }
            compose.onNodeWithTag("tools_back").performClick()
            compose.onNodeWithTag("tool_reminder").performClick()
            repeat(2) { compose.onNodeWithTag("appointment_plus").performClick() }
            compose.onNodeWithText("10 د").assertExists(); capture("reminder")
            compose.onNodeWithTag("appointment_date").performClick(); capture("date")
            compose.onNodeWithTag("appointment_dialog_cancel").performClick()
            compose.onNodeWithTag("appointment_time").performClick(); capture("clock")
            compose.onNodeWithTag("appointment_dialog_cancel").performClick()
            compose.onNodeWithTag("tools_back").performClick()
            compose.onNodeWithTag("tool_capsule").performClick()
            compose.onNodeWithTag("appointment_plus").performClick()
            assertNull(vm.state.value.temporalEdit.sealUntil)
            compose.onNodeWithTag("appointment_confirm").performScrollTo().performClick(); capture("capsule-confirmation")
            assertNull(vm.state.value.temporalEdit.sealUntil)
            compose.onNodeWithText("متابعة الكتابة").performClick()
            assertNull(vm.state.value.temporalEdit.sealUntil)
            compose.onNodeWithTag("appointment_confirm").performScrollTo().performClick()
            compose.onNodeWithTag("capsule_seal_confirm").performClick()
            compose.waitUntil(10000) { vm.state.value.temporalEdit.sealUntil != null }
            assertNull(vm.state.value.lockedUntil)
            compose.onNodeWithTag("editor_body").assertTextContains(body)
            compose.onNodeWithContentDescription("رجوع").performClick()
            compose.waitUntil(15000) { vm.state.value.lockedUntil != null }
            compose.onAllNodesWithTag("editor_body").assertCountEquals(0)
            assertFalse(compose.onRoot(useUnmergedTree = true).printToString().contains("هذه مساحة"))
            assertEquals("", runBlocking { repo.getNote("r3-native")!!.body })
            capture("locked")
        } finally {
            compose.runOnUiThread { store.clear() }
            // ViewModel work is protected by NonCancellable; avoid closing Room mid-commit.
            runBlocking { vm.viewModelScope.coroutineContext[Job]?.join() }
            db.close()
        }
    }
}
