package com.beemind.personal

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.beemind.personal.data.RoomNotesRepository
import com.beemind.personal.data.local.BeeMindDatabase
import com.beemind.personal.domain.*
import com.beemind.personal.ui.notes.*
import com.beemind.personal.ui.theme.BeeMindTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReadingR4DeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun nativeReadingInlineRelationshipsLiveRemovalAndLockedPrivacy() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
        val db = Room.inMemoryDatabaseBuilder(context,BeeMindDatabase::class.java).build()
        val repo = RoomNotesRepository(db)
        val store = ViewModelStore()
        lateinit var vm: DetailViewModel
        var id by mutableStateOf("normal")
        var opened: String? = null
        val long = "أفكار تعبر الزمن. Mixed English 123 🌿\n\nسطر محفوظ كما كُتب.\n".repeat(120)
        fun capture(name: String) {
            compose.waitForIdle()
            val bitmap = checkNotNull(instrument.uiAutomation.takeScreenshot())
            File(context.filesDir,"r4-native-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
        }
        try {
            runBlocking {
                repo.save("normal","",long,true,"n")
                repo.save("glow","","ضوء الصباح\n\nصفحة جديدة.",true,"g",ExpressiveTrait.GLOW)
                repo.save("anger","","أكتب كي أمضي.\nA quiet page.",true,"a",ExpressiveTrait.ANGER)
                repo.save("locked","PRIVATE TITLE","PRIVATE BODY",true,"l",temporalEdit=TemporalEdit(sealUntil=System.currentTimeMillis()+3600000))
                repo.setLinked("normal","glow",true); repo.setLinked("normal","locked",true)
            }
            compose.setContent { BeeMindTheme {
                key(id) {
                    vm = remember(id) { DetailViewModel(repo,id).also { store.put(id,it) } }
                    val content by vm.content.collectAsState()
                    val deletion by vm.deletion.collectAsState()
                    DetailScreen(vm,content,deletion,{}, {}, { opened=it })
                }
            } }
            compose.waitUntil(15000) { compose.onAllNodesWithTag("reading_chunk_0").fetchSemanticsNodes().isNotEmpty() }
            capture("reading-long")
            val actions = compose.onNodeWithTag("reading_actions").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithTag("reading_list").performScrollToNode(hasTestTag("inline_thread_locked"))
            capture("inline-two")
            assertEquals(actions,compose.onNodeWithTag("reading_actions").fetchSemanticsNode().boundsInRoot)
            compose.onAllNodesWithText("PRIVATE",substring=true,useUnmergedTree=true).assertCountEquals(0)
            compose.onNodeWithTag("inline_thread_glow").performClick(); assertEquals("glow",opened)
            compose.onNodeWithTag("manage_threads").performClick(); capture("management")
            compose.onNodeWithTag("thread_remove_glow").performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithTag("thread_open_glow").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("إغلاق الخيوط").performClick()
            compose.onAllNodesWithTag("inline_thread_glow").assertCountEquals(0)
            for (target in listOf("glow","anger","locked")) {
                compose.runOnIdle { id=target }
                compose.waitUntil(15000) { compose.onAllNodesWithTag("reading_more").fetchSemanticsNodes().isNotEmpty() }
                capture("reading-$target")
            }
            compose.onAllNodesWithContentDescription("تعديل الملاحظة").assertCountEquals(0)
            compose.onAllNodesWithTag("reading_chunk_0").assertCountEquals(0)
            compose.onAllNodesWithTag("inline_threads").assertCountEquals(0)
            compose.onAllNodesWithText("PRIVATE",substring=true,useUnmergedTree=true).assertCountEquals(0)
            assertEquals(long,runBlocking { repo.getNote("normal")!!.body })
        } finally { compose.runOnIdle { store.clear() }; db.close() }
    }
}
