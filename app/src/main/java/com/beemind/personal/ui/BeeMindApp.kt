package com.beemind.personal.ui

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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
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
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val openEditor: (String?) -> Unit = { noteId ->
        scope.launch {
            try {
                val draft = noteId?.let { repository.latestDraftForNote(it) }
                val session = draft?.sessionId ?: UUID.randomUUID().toString()
                nav.navigate("editor/$session" + (noteId?.let { "?noteId=$it" } ?: "")) { launchSingleTop = true }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { snackbar.showSnackbar(context.getString(R.string.editor_open_error)) }
        }
    }
    Box(Modifier.fillMaxSize()) {
        NavHost(navController = nav, startDestination = "home") {
            composable("home") {
                val vm: HomeViewModel = viewModel(factory = viewModelFactory { initializer { HomeViewModel(repository) } })
                val state by vm.state.collectAsStateWithLifecycle()
                HomeScreen(state, onCreate = { openEditor(null) }, onOpen = { nav.navigate("note/$it") { launchSingleTop = true } },
                    onResume = { draft -> nav.navigate("editor/${draft.sessionId}" + (draft.noteId?.let { "?noteId=$it" } ?: "")) { launchSingleTop = true } },
                    onRetry = vm::retry)
            }
            composable("note/{noteId}") { entry ->
                val id = requireNotNull(entry.arguments?.getString("noteId"))
                val vm: DetailViewModel = viewModel(factory = viewModelFactory { initializer { DetailViewModel(repository, id) } })
                val content by vm.content.collectAsStateWithLifecycle()
                val deletion by vm.deletion.collectAsStateWithLifecycle()
                DetailScreen(vm, content, deletion, onBack = { nav.popBackStack() }, onEdit = { openEditor(id) })
            }
            composable("editor/{sessionId}?noteId={noteId}", arguments = listOf(navArgument("noteId") { type = NavType.StringType; nullable = true; defaultValue = null })) { entry ->
                val session = requireNotNull(entry.arguments?.getString("sessionId"))
                val id = entry.arguments?.getString("noteId")
                val vm: EditorViewModel = viewModel(factory = viewModelFactory { initializer { EditorViewModel(repository, session, id) } })
                val state by vm.state.collectAsStateWithLifecycle()
                EditorScreen(vm, state, onBack = { nav.popBackStack() }, onSaved = { savedId ->
                    nav.navigate("note/$savedId") { popUpTo("home"); launchSingleTop = true }
                    scope.launch { snackbar.showSnackbar(context.getString(R.string.note_saved)) }
                })
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}
