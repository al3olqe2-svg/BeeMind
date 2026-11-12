package com.beemind.personal.ui.notes

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import com.beemind.personal.ui.theme.HomeMaterials
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.beemind.personal.R
import com.beemind.personal.domain.DraftSummary
import com.beemind.personal.ui.components.*
import com.beemind.personal.ui.theme.BeeDesign

@Composable
fun HomeScreen(state: HomeState, onCreate: () -> Unit, onOpen: (String) -> Unit,
    onResume: (DraftSummary) -> Unit, onRetry: () -> Unit,
    onSettings: () -> Unit = {}, wallpaper: @Composable () -> Unit = {}) {
    Box(Modifier.fillMaxSize().background(BeeDesign.atmosphere)) {
        wallpaper()
        Box(Modifier.fillMaxSize().systemBarsPadding()) {
            IconButton(onClick = onSettings, modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd)
                .padding(horizontal = 12.dp).size(BeeDesign.touchTarget).testTag("wallpaper_settings_open")) {
                Box(Modifier.size(36.dp).background(HomeMaterials.control, CircleShape)
                    .border(.6.dp, HomeMaterials.edge, CircleShape), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Icon(Icons.Outlined.Settings, "إعدادات الخلفيات", tint = BeeDesign.darkForeground, modifier = Modifier.size(21.dp))
                }
            }
            Box(Modifier.fillMaxWidth().padding(start = BeeDesign.page, end = BeeDesign.page, top = 48.dp)) {
                RainbowLine()
            }
            Box(Modifier.fillMaxSize().padding(top = 88.dp, bottom = 72.dp)) {
                when (state) {
                    HomeState.Loading -> {
                        val loadingLabel = stringResource(R.string.loading_notes)
                        Spacer(Modifier.semantics { stateDescription = loadingLabel })
                    }
                    HomeState.Error -> Box(Modifier.padding(BeeDesign.page)) {
                        ErrorPanel(stringResource(R.string.notes_error), onRetry = onRetry)
                    }
                    is HomeState.Ready -> if (state.notes.isNotEmpty()) {
                        val density = LocalDensity.current
                        val widthPx = LocalWindowInfo.current.containerSize.width
                        val columns = masonryColumns((widthPx / density.density).toInt(), density.fontScale)
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(columns),
                            modifier = Modifier.fillMaxSize().testTag("notes_grid").semantics { isTraversalGroup = true },
                            contentPadding = PaddingValues(start = BeeDesign.page, end = BeeDesign.page, top = 12.dp, bottom = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(BeeDesign.gap),
                            verticalItemSpacing = BeeDesign.gap,
                        ) {
                            itemsIndexed(state.notes, key = { _, note -> note.id }, contentType = { _, _ -> "note" }) { index, note ->
                                NoteCard(note, index, onClick = { onOpen(note.id) })
                            }
                        }
                    }
                }
            }
            Box(Modifier.align(androidx.compose.ui.Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = androidx.compose.ui.Alignment.CenterEnd) {
                MiniGlassAdd(enabled = state is HomeState.Ready, onClick = {
                    if (state is HomeState.Ready) state.draft?.takeIf { it.noteId == null }?.let(onResume) ?: onCreate()
                })
            }
        }
    }
}
