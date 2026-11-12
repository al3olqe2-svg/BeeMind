package com.beemind.personal

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import com.beemind.personal.ui.theme.BeeDesign
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.beemind.personal.domain.ExpressiveTrait
import com.beemind.personal.domain.NoteSummary
import com.beemind.personal.ui.components.MiniGlassAdd
import com.beemind.personal.ui.components.RainbowLine
import com.beemind.personal.ui.notes.NoteCard
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import com.beemind.personal.ui.notes.HomeScreen
import com.beemind.personal.ui.notes.HomeState
import com.beemind.personal.ui.theme.BeeMindTheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real HWUI first frames; screenshots contain synthetic data only. No note writes. */
@RunWith(AndroidJUnit4::class)
class NativeVisualProbeTest {
    @Test(timeout = 45000) fun nativeFirstFrameRenders() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
        val component = InstrumentationRegistry.getArguments().getString("component") ?: "main"
        val activity = if (component == "main") MainActivity::class.java else ComponentActivity::class.java
        ActivityScenario.launch<ComponentActivity>(Intent(context, activity)).use { scenario ->
            if (component != "main") scenario.onActivity { current -> current.setContent {
                BeeMindTheme { Box(Modifier.fillMaxSize()) {
                    when (component) {
                        "empty" -> Unit
                        "background" -> Box(Modifier.fillMaxSize().background(BeeDesign.atmosphere))
                        "metrics" -> {
                            check(LocalWindowInfo.current.containerSize.width >= 0)
                            check(LocalDensity.current.density > 0)
                        }
                        "insets" -> Box(Modifier.fillMaxSize().systemBarsPadding())
                        "composition" -> Box(Modifier.fillMaxSize().background(BeeDesign.atmosphere)) {
                            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 48.dp)) { RainbowLine() }
                            Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) { MiniGlassAdd { } }
                        }
                        "rainbow" -> RainbowLine()
                        "fab" -> MiniGlassAdd { }
                        "card" -> NoteCard(NoteSummary("visual-a", null, "ملاحظة تجريبية Synthetic note", 1, 1), 0) {}
                        "grid" -> LazyVerticalStaggeredGrid(columns = StaggeredGridCells.Fixed(1), modifier = Modifier.fillMaxSize()) {
                            item { Text("ملاحظة تجريبية Synthetic note", style = MaterialTheme.typography.bodyMedium) }
                        }
                        "text" -> Text("ملاحظة تجريبية Synthetic note", style = MaterialTheme.typography.bodyMedium)
                        "home-loading" -> HomeScreen(HomeState.Loading, {}, {}, {}, {})
                        "home-empty", "home-plain", "home-glow", "home" -> {
                            val now = System.currentTimeMillis()
                            val notes = when (component) {
                                "home-empty" -> emptyList()
                                "home-plain" -> listOf(NoteSummary("visual-a", null, "ملاحظة تجريبية Synthetic note", now, now))
                                "home-glow" -> listOf(NoteSummary("visual-a", null, "ملاحظة تجريبية Synthetic note", now, now, ExpressiveTrait.GLOW))
                                else -> listOf(
                                    NoteSummary("visual-a", null, "ملاحظة تجريبية", now, now, ExpressiveTrait.GLOW, now + 3600000),
                                    NoteSummary("visual-b", null, "Synthetic note", now, now, ExpressiveTrait.GLOW),
                                    NoteSummary("visual-c", null, "ملاحظة أخرى", now, now, ExpressiveTrait.ANGER))
                            }
                            HomeScreen(HomeState.Ready(notes, null), {}, {}, {}, {})
                        }
                        else -> error("Unknown probe component")
                    }
                } }
            } }
            instrument.waitForIdleSync()
            Thread.sleep(1200)
            instrument.waitForIdleSync()
            scenario.onActivity { current -> assertTrue(current.window.decorView.width > 0) }
            println("RENDER_READY: $component")
            val bitmap = checkNotNull(instrument.uiAutomation.takeScreenshot())
            File(context.filesDir, "native-ui-$component.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            println("SCREENSHOT_WRITTEN: $component")
        }
    }
}
