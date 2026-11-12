package com.beemind.personal

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.beemind.personal.domain.*
import com.beemind.personal.ui.notes.*
import com.beemind.personal.ui.theme.*
import java.io.File
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
class HomeR2ExperienceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val notes = listOf(
        NoteSummary("a", null, "أترك للأفكار مساحة كي تنضج.\nكل شيء يبدأ بلحظة انتباه.", 6, 6),
        NoteSummary("short", null, "ضوء الصباح على صفحة لم تُكتب بعد.", 5, 5, ExpressiveTrait.GLOW),
        NoteSummary("c", null, "أحيانًا نحتاج أن نقول ما نشعر به، ثم نمضي بهدوء.", 4, 4, ExpressiveTrait.ANGER),
        NoteSummary("d", null, "", 3, 3, locked = true, unlockAt = 1800000000000),
        NoteSummary("e", null, "فكرة قديمة تستحق نظرة جديدة.", 2, 2, resurfaced = true),
        NoteSummary("f", null, "تفاصيل يوم عادي. ".repeat(40), 1, 1))
    private fun screenshot(name: String) = compose.runOnIdle {
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File("build/reports/screenshots/r2-$name.png").apply { parentFile!!.mkdirs() }
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun mixedMaterialsPrivacySizingAndReorderAcrossAtmospheres() {
        var scene by mutableStateOf("sea")
        var data by mutableStateOf(notes)
        compose.setContent { BeeMindTheme { AtmosphericTheme {
            HomeScreen(HomeState.Ready(data, null), {}, {}, {}, {}, wallpaper = {
                val colors = when(scene) {
                    "sea" -> listOf(Color(0xFF7CBED1), Color(0xFF245781), Color(0xFF132841))
                    "bright" -> listOf(Color.White, Color(0xFFFFF4C8))
                    "dark" -> listOf(Color(0xFF060910), Color(0xFF161B29))
                    "portrait" -> listOf(Color(0xFFD9B697), Color(0xFF624B41), Color(0xFF2C2529))
                    "detail" -> BeeDesign.spectrum
                    else -> listOf(BeeDesign.darkSurface, BeeDesign.darkBackground)
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(colors)))
            })
        } } }
        compose.onNodeWithTag("home_capsule_cover", useUnmergedTree = true).assertExists()
        compose.onAllNodesWithTag("home_resurfaced", useUnmergedTree = true).assertCountEquals(1)
        val heights = listOf("a", "short", "c").associateWith { compose.onNodeWithTag("note_$it").fetchSemanticsNode().boundsInRoot.height }
        assertEquals(3, heights.values.toSet().size)
        for (name in listOf("sea", "bright", "dark", "portrait", "detail", "fallback")) {
            compose.runOnIdle { scene = name }; compose.waitForIdle()
            assertEquals(heights.getValue("a"), compose.onNodeWithTag("note_a").fetchSemanticsNode().boundsInRoot.height, .5f)
            screenshot(name)
        }
        compose.runOnIdle { data = notes.map { if (it.id == "a") it.copy(resurfaced = true, preview = "اختصار") else it }.reversed() }
        compose.onNodeWithTag("notes_grid").performScrollToIndex(0)
        for ((id, height) in heights) {
            compose.onNodeWithTag("notes_grid").performScrollToIndex(data.indexOfFirst { it.id == id })
            assertEquals(height, compose.onNodeWithTag("note_$id").fetchSemanticsNode().boundsInRoot.height, .5f)
        }
        // Defense in depth: even a malformed model must not leak text to merged/unmerged semantics.
        compose.runOnIdle { data = listOf(notes[3].copy(title = "PRIVATE_TITLE", preview = "PRIVATE_BODY")) }
        compose.onAllNodesWithText("PRIVATE", substring = true, useUnmergedTree = true).assertCountEquals(0)
        assertFalse(compose.onRoot(useUnmergedTree = true).printToString().contains("PRIVATE"))
        compose.onAllNodesWithTag("home_resurfaced", useUnmergedTree = true).assertCountEquals(0)
    }
    @Test fun manyEqualLengthNotesKeepRhythmAndLastCardClearOfAdd() {
        val data = (0..89).map { NoteSummary("batch-$it", null, "تفاصيل صغيرة تستحق الانتباه.", it.toLong(), 0) }
        compose.setContent { BeeMindTheme { AtmosphericTheme {
            HomeScreen(HomeState.Ready(data, null), {}, {}, {}, {})
        } } }
        val firstSizes = (0..5).map { compose.onNodeWithTag("note_batch-$it").fetchSemanticsNode().boundsInRoot.height }
        assertTrue(firstSizes.toSet().size >= 2)
        screenshot("many-short")
        compose.onNodeWithTag("notes_grid").performScrollToIndex(89)
        compose.onNodeWithTag("note_batch-89").assertIsDisplayed()
        val card = compose.onNodeWithTag("note_batch-89").fetchSemanticsNode().boundsInRoot
        val add = compose.onNodeWithTag("new_note").fetchSemanticsNode().boundsInRoot
        assertTrue(card.bottom < add.top)
        screenshot("last-card")
    }
    @Test @Config(qualifiers = "w320dp-h640dp") fun narrowLargeFontAndControlsRemainUsable() {
        var state by mutableStateOf<HomeState>(HomeState.Loading)
        var created = 0; var resumed: DraftSummary? = null; var opened = ""; var settings = 0
        compose.setContent { BeeMindTheme { AtmosphericTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.8f)) {
                Box(Modifier.width(320.dp)) { HomeScreen(state, { created++ }, { opened = it }, { resumed = it }, {}, { settings++ }) }
            }
        } } }
        compose.onNodeWithTag("new_note_loading").assertIsNotEnabled()
        compose.runOnIdle { state = HomeState.Ready(listOf(notes[0], notes[3]), DraftSummary("recovery", null)) }
        compose.onNodeWithTag("new_note").performClick(); assertEquals("recovery", resumed?.sessionId); assertEquals(0, created)
        compose.onNodeWithTag("note_a").performClick(); assertEquals("a", opened)
        compose.onNodeWithTag("wallpaper_settings_open").performClick(); assertEquals(1, settings)
        compose.onNodeWithTag("rainbow_line").assertExists()
        compose.onNodeWithTag("notes_grid").performScrollToIndex(1)
        val capsule = compose.onNodeWithTag("note_d").fetchSemanticsNode().boundsInRoot
        val fab = compose.onNodeWithTag("new_note").fetchSemanticsNode().boundsInRoot
        assertTrue(capsule.bottom < fab.top)
        screenshot("320-large-font")
        compose.runOnIdle { state = HomeState.Ready(emptyList(), null) }
        compose.onNodeWithTag("new_note").performClick(); assertEquals(1, created)
        screenshot("empty")
    }
}
