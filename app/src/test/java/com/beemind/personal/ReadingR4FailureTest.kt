package com.beemind.personal

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.beemind.personal.data.RoomNotesRepository
import com.beemind.personal.data.local.BeeMindDatabase
import com.beemind.personal.domain.*
import com.beemind.personal.ui.notes.*
import com.beemind.personal.ui.theme.BeeMindTheme
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class ReadingR4FailureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun failedUnlinkKeepsInlineCardAndFailedDeleteKeepsPageUntilRetrySucceeds() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context,BeeMindDatabase::class.java).build()
        val real = RoomNotesRepository(db)
        var fail = true
        val repository = object : NotesRepository by real {
            override suspend fun delete(id:String) { if (fail) throw IOException("Fixture"); real.delete(id) }
            override suspend fun setLinked(a:String,b:String,linked:Boolean) { if (fail) throw IOException("Fixture"); real.setLinked(a,b,linked) }
        }
        val store = ViewModelStore()
        val vm = DetailViewModel(repository,"a").also { store.put("detail",it) }
        var backs = 0
        try {
            runBlocking { real.save("a","","قابل للقراءة",true,"a"); real.save("b","","فكرة مرتبطة",true,"b"); real.setLinked("a","b",true) }
            compose.setContent { BeeMindTheme {
                val content by vm.content.collectAsState()
                val deletion by vm.deletion.collectAsState()
                DetailScreen(vm,content,deletion,{ backs++ },{}, {})
            } }
            compose.waitUntil(15000) { compose.onAllNodesWithTag("inline_thread_b").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("manage_threads").performClick()
            compose.onNodeWithTag("thread_remove_b").performClick()
            compose.waitUntil(15000) { vm.linkError.value }
            compose.onNodeWithTag("thread_open_b").assertExists()
            compose.onNodeWithText("إغلاق الخيوط").performClick()
            compose.onNodeWithTag("inline_thread_b").assertExists()
            compose.onNodeWithTag("reading_more").performClick(); compose.onNodeWithText("حذف الملاحظة").performClick()
            compose.onNodeWithText("حذف نهائي").performClick()
            compose.waitUntil(15000) { vm.deletion.value.error }
            assertEquals(0,backs); assertNotNull(runBlocking { real.getNote("a") })
            compose.waitForIdle()
            fail=false
            compose.onNodeWithText("حذف نهائي").performClick()
            compose.waitUntil(15000) { compose.waitForIdle(); backs == 1 }
            assertNull(runBlocking { real.getNote("a") }); assertNotNull(runBlocking { real.getNote("b") })
        } finally { compose.runOnIdle { store.clear() }; db.close() }
    }
}
