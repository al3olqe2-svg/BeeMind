package com.beemind.personal

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.beemind.personal.data.RoomNotesRepository
import com.beemind.personal.data.local.BeeMindDatabase
import com.beemind.personal.domain.*
import com.beemind.personal.ui.notes.EditorViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Native Main dispatcher + Room, isolated from the installed user's notes. */
@RunWith(AndroidJUnit4::class)
class EditorDeviceTest {
    @Test fun titlelessLifecycleSaveRetryReopenAndAllTraitMetadataRemainSafe() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "stage55-editor-isolated.db"
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context, BeeMindDatabase::class.java, name).build()
        val stores = mutableListOf<ViewModelStore>()
        val editorJobs = mutableListOf<Job>()
        fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
        suspend fun await(condition: () -> Boolean) { withTimeout(10000) { while (!condition()) delay(20) } }
        suspend fun clearEditors() {
            main { stores.forEach { it.clear() }; stores.clear() }
            // A completed navigation state can precede the final mutex release.
            // Await cancellation and NonCancellable Room work before closing/reopening.
            editorJobs.forEach { it.join() }
            editorJobs.clear()
        }
        try {
            var repo = RoomNotesRepository(db)
            lateinit var vm: EditorViewModel
            main { vm = EditorViewModel(repo, "session", null); ViewModelStore().also { stores += it; it.put("vm", vm) }; editorJobs += checkNotNull(vm.viewModelScope.coroutineContext[Job]) }
            await { !vm.state.value.loading }
            main { vm.body.edit { append("بداية 🌈") }; androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
            withTimeout(10000) { while (repo.getNote("session") == null) delay(20) }
            val original = repo.getNote("session")!!
            assertNull(original.title)
            main { vm.body.edit { append(" وآخر حرف ي") }; vm.save(); vm.save() }
            await { vm.state.value.savedId != null }
            assertEquals("بداية 🌈 وآخر حرف ي", repo.getNote("session")!!.body)
            assertEquals(1, repo.observeNotes().first().size)
            val due = System.currentTimeMillis() + 3600000
            repo.save("linked", "", "مرتبطة", true, "linked", ExpressiveTrait.ANGER)
            repo.setLinked("session", "linked", true)
            repo.scheduleReminder("session", due)
            repo.scheduleResurface("session", due)
            val reminder = db.reminderDao().get("session")!!
            lateinit var editing: EditorViewModel
            main { editing = EditorViewModel(repo, "editing", "session"); ViewModelStore().also { stores += it; it.put("vm", editing) }; editorJobs += checkNotNull(editing.viewModelScope.coroutineContext[Job]) }
            await { !editing.state.value.loading }
            main { editing.body.edit { replace(0, length, "آخر تعديل محفوظ") }; editing.selectTrait(ExpressiveTrait.GLOW); editing.requestExit() }
            await { editing.state.value.exited }
            assertEquals(original.createdAt, repo.getNote("session")!!.createdAt)
            assertEquals(reminder, db.reminderDao().get("session"))
            assertEquals(due, repo.getNote("session")!!.resurfaceAt)
            assertEquals(1, repo.observeThreadNotes("session", true).first().size)
            clearEditors()
            db.close()
            db = Room.databaseBuilder(context, BeeMindDatabase::class.java, name).build()
            repo = RoomNotesRepository(db)
            val reopened = repo.getNote("session")!!
            assertEquals("آخر تعديل محفوظ", reopened.body); assertEquals(ExpressiveTrait.GLOW, reopened.expressiveTrait)
            assertEquals(original.createdAt, reopened.createdAt); assertEquals(1, repo.observeThreadNotes("linked", true).first().size)
            db.openHelper.writableDatabase.query("PRAGMA integrity_check").use { assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0)) }
        } finally { clearEditors(); db.close(); context.deleteDatabase(name) }
    }
}
