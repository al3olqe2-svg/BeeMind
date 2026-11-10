package com.beemind.personal.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import com.beemind.personal.ui.theme.BeeDesign
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.withResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.beemind.personal.BeeMindApplication
import com.beemind.personal.R
import com.beemind.personal.ui.notes.*
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun BeeMindApp() {
    val context = LocalContext.current
    val repository = (context.applicationContext as BeeMindApplication).notesRepository
    val nav = rememberNavController()
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, repository) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) repository.refreshTime()
        }
        lifecycle.addObserver(observer)
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, intent: android.content.Intent?) { repository.refreshTime() }
        }
        androidx.core.content.ContextCompat.registerReceiver(context, receiver, android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_TIME_CHANGED); addAction(android.content.Intent.ACTION_TIMEZONE_CHANGED)
        }, androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        onDispose { lifecycle.removeObserver(observer); context.unregisterReceiver(receiver) }
    }
    val activity = context as com.beemind.personal.MainActivity
    val reminderOpen by activity.reminderOpen.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(reminderOpen) {
        reminderOpen?.let { (id, generation) ->
            try {
                val reminder = (context.applicationContext as BeeMindApplication).database.reminderDao().get(id)
                if (reminder?.generation == generation) nav.navigate("note/${android.net.Uri.encode(id)}") { launchSingleTop = true }
                else snackbar.showSnackbar("تغيّر التذكير أو حُذفت الملاحظة. افتح الملاحظة من الصفحة الرئيسية.")
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { snackbar.showSnackbar("تعذر فتح الملاحظة. حاول من الصفحة الرئيسية.") }

            activity.reminderOpen.value = null
        }
    }
    val scope = rememberCoroutineScope()
    val goBack: (NavBackStackEntry) -> Unit = { source ->
        if (nav.currentBackStackEntry === source) scope.launch {
            source.lifecycle.withResumed { if (nav.currentBackStackEntry === source) nav.popBackStack() }
        }
    }
    val openEditor: (String?, NavBackStackEntry) -> Unit = { noteId, source ->
        scope.launch {
            if (nav.currentBackStackEntry !== source) return@launch
            try {
                // A click during an entering transition waits for its source entry to resume.
                source.lifecycle.withResumed { }
                if (nav.currentBackStackEntry !== source) return@launch
                val draft = noteId?.let { repository.latestDraftForNote(it) }
                val session = draft?.sessionId ?: UUID.randomUUID().toString()
                if (nav.currentBackStackEntry !== source) return@launch
                source.lifecycle.withResumed {
                    if (nav.currentBackStackEntry === source) {
                        nav.navigate("editor/$session" + (noteId?.let { "?noteId=$it" } ?: "")) { launchSingleTop = true }
                    }
                }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { snackbar.showSnackbar(context.getString(R.string.editor_open_error)) }
        }
    }
    Box(Modifier.fillMaxSize()) {
        NavHost(navController = nav, startDestination = "home",
            enterTransition = { fadeIn(tween(BeeDesign.pressMillis)) },
            exitTransition = { fadeOut(tween(BeeDesign.pressMillis)) }) {
            composable("home") { entry ->
                val vm: HomeViewModel = viewModel(factory = viewModelFactory { initializer { HomeViewModel(repository) } })
                val state by vm.state.collectAsStateWithLifecycle()
                HomeScreen(state, onCreate = { openEditor(null, entry) }, onOpen = { id -> scope.launch { if (nav.currentBackStackEntry === entry) entry.lifecycle.withResumed {
                        if (nav.currentBackStackEntry === entry) nav.navigate("note/$id") { launchSingleTop = true }
                    } } },
                    onResume = { draft -> scope.launch { if (nav.currentBackStackEntry === entry) entry.lifecycle.withResumed {
                        if (nav.currentBackStackEntry === entry) nav.navigate("editor/${draft.sessionId}" + (draft.noteId?.let { "?noteId=$it" } ?: "")) { launchSingleTop = true }
                    } } },
                    onRetry = vm::retry)
            }
            composable("note/{noteId}") { entry ->
                val id = requireNotNull(entry.arguments?.getString("noteId"))
                val vm: DetailViewModel = viewModel(factory = viewModelFactory { initializer { DetailViewModel(repository, id) } })
                val content by vm.content.collectAsStateWithLifecycle()
                val deletion by vm.deletion.collectAsStateWithLifecycle()
                DetailScreen(vm, content, deletion, onBack = { goBack(entry) }, onEdit = { openEditor(id, entry) }, onOpenLinked = { linkedId ->
                    scope.launch {
                        if (nav.currentBackStackEntry === entry) entry.lifecycle.withResumed {
                            if (nav.currentBackStackEntry === entry) nav.navigate("note/${android.net.Uri.encode(linkedId)}") {
                                launchSingleTop = true
                            }
                        }
                    }
                })
            }
            composable("editor/{sessionId}?noteId={noteId}", arguments = listOf(navArgument("noteId") { type = NavType.StringType; nullable = true; defaultValue = null })) { entry ->
                val session = requireNotNull(entry.arguments?.getString("sessionId"))
                val id = entry.arguments?.getString("noteId")
                val vm: EditorViewModel = viewModel(factory = viewModelFactory { initializer { EditorViewModel(repository, session, id) } })
                val state by vm.state.collectAsStateWithLifecycle()
                EditorScreen(vm, state, onBack = { goBack(entry) }, onSaved = { savedId ->
                    scope.launch {
                        entry.lifecycle.withResumed {
                            if (nav.currentBackStackEntry === entry) {
                                nav.navigate("note/$savedId") { popUpTo("home"); launchSingleTop = true }
                            }
                        }
                        snackbar.showSnackbar(context.getString(R.string.note_saved))
                    }
                })
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}
