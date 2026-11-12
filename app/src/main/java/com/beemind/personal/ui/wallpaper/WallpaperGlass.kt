package com.beemind.personal.ui.wallpaper

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.beemind.personal.wallpaper.*

private data class GlassBackdrop(val image: ImageBitmap, val origin: Offset, val size: IntSize)
private val LocalGlassBackdrop = compositionLocalOf<GlassBackdrop?> { null }

/** One API-26-compatible diffuse derivative per viewport, shared by all visible cards.
 * Sampling uses window coordinates, so scrolling never restarts the image crop per card. */
@Composable
fun WallpaperGlassHost(state: WallpaperState, repository: WallpaperRepository, images: WallpaperImages,
    content: @Composable () -> Unit) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val id = state.currentId
    val bitmap by produceState<ImageBitmap?>(null, id, size) {
        value = null
        if (id != null && size.width > 0 && size.height > 0)
            value = images.load(repository.file(id), 0, size.width.toFloat() / size.height, edge = 720, frost = true)?.asImageBitmap()
    }
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow(); size = it.size }) {
        CompositionLocalProvider(LocalGlassBackdrop provides bitmap?.let { GlassBackdrop(it, origin, size) }) { content() }
    }
}

@Composable
fun Modifier.frostedWallpaper(): Modifier {
    val backdrop = LocalGlassBackdrop.current
    var position by remember { mutableStateOf(Offset.Zero) }
    return onGloballyPositioned { position = it.positionInWindow() }.drawWithCache {
        val path = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(14.dp.toPx()))) }
        onDrawBehind {
            backdrop?.let {
                clipPath(path) {
                    // Match Image's ContentScale.Crop even in unusually wide/narrow windows.
                    val scale = maxOf(it.size.width.toFloat() / it.image.width, it.size.height.toFloat() / it.image.height)
                    val scaled = IntSize((it.image.width * scale).roundToInt(), (it.image.height * scale).roundToInt())
                    val crop = Offset((it.size.width - scaled.width) / 2f, (it.size.height - scaled.height) / 2f)
                    val offset = it.origin - position + crop
                    translate(offset.x, offset.y) { drawImage(it.image, dstSize = scaled) }
                }
            }
        }
    }
}
