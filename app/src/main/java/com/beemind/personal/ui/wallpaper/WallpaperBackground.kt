package com.beemind.personal.ui.wallpaper

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.beemind.personal.ui.theme.BeeDesign
import com.beemind.personal.wallpaper.*

@Composable
fun WallpaperBackground(state: WallpaperState, repository: WallpaperRepository, images: WallpaperImages,
    modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.background(BeeDesign.atmosphere)) {
        val aspect = (maxWidth.value / maxHeight.value).takeIf { it.isFinite() } ?: .5f
        val id = state.currentId
        val cached = id?.let { selected -> images.cachedWallpaper(selected, state.blur, aspect)?.let { selected to it } }
        val rendered by produceState(cached, id, state.blur, aspect) {
            kotlinx.coroutines.delay(100)
            value = if (id == null) null else images.load(repository.file(id), state.blur, aspect)?.let { id to it }
        }
        rendered?.let { (renderedId, bitmap) -> Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize().testTag("wallpaper_ready_$renderedId"),
            contentScale = ContentScale.Crop, filterQuality = FilterQuality.High) }
        Box(Modifier.fillMaxSize().background(BeeDesign.atmosphericOverlay))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Color.Black.copy(alpha = .22f), Color.Transparent, Color.Black.copy(alpha = .36f)))))
    }
}

@Composable
fun WallpaperThumbnail(id: String, repository: WallpaperRepository, images: WallpaperImages, modifier: Modifier) {
    val bitmap by produceState<Bitmap?>(null, id) { value = images.load(repository.file(id)) }
    Box(modifier.background(BeeDesign.surface)) {
        bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize().testTag("wallpaper_thumbnail_$id"), contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High) }
    }
}
