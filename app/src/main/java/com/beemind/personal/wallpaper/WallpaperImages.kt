package com.beemind.personal.wallpaper

import android.graphics.*
import androidx.exifinterface.media.ExifInterface
import androidx.core.graphics.createBitmap
import android.util.LruCache
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Bounded, single-worker processing for every API >= 26. No per-card effects. */
class WallpaperImages {
    private val worker = Dispatchers.IO
    private val processing = Mutex()
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private fun key(name: String, blur: Int?, aspect: Float) =
        "$name:$blur:${(aspect.coerceIn(.4f, 2.5f) * 100).toInt()}"
    /** Originals are immutable UUID files. This synchronized cache lookup performs no disk IO. */
    fun cachedWallpaper(id: String, blur: Int, aspect: Float): Bitmap? = cache.get(key("$id.image", blur, aspect))
    suspend fun load(file: File, blur: Int? = null, aspect: Float = .5f): Bitmap? = withContext(worker) { processing.withLock {
        val ratio = aspect.coerceIn(.4f, 2.5f)
        val key = key(file.name, blur, ratio)
        cache.get(key)?.let { return@withLock it }
        val source = decode(file, 1440) ?: return@withLock null
        val result = run {
            // Crop before blur; cap output at 720 on the long edge, then smoothly upscale.
            val width = if (blur == null) 640 else if (ratio < 1) (720 * ratio).toInt() else 720
            val height = if (blur == null) 280 else if (ratio < 1) 720 else (720 / ratio).toInt()
            val out = createBitmap(width, height)
            val scale = maxOf(width.toFloat() / source.width, height.toFloat() / source.height)
            val dx = (width - source.width * scale) / 2f
            val dy = (height - source.height * scale) / 2f
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            // Compress highlights and saturation consistently across very different originals.
            if (blur != null) {
                val saturation = ColorMatrix().apply { setSaturation(.72f) }
                saturation.postConcat(ColorMatrix(floatArrayOf(
                    .72f,0f,0f,0f,2f, 0f,.72f,0f,0f,3f, 0f,0f,.72f,0f,7f, 0f,0f,0f,1f,0f)))
                paint.colorFilter = ColorMatrixColorFilter(saturation)
            }
            Canvas(out).drawBitmap(source, null, RectF(dx, dy, dx + source.width * scale, dy + source.height * scale), paint)
            source.recycle()
            if (blur != null) blur(out, 3 + blur.coerceIn(20, 100) * 17 / 100)
            out
        }
        cache.put(key, result)
        result
    } }
    companion object {
        /** Decode dimensions first. Power-of-two sampling bounds both dimensions and memory. */
        fun decode(file: File, maxEdge: Int): Bitmap? {
            return try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
                    bounds.outWidth.toLong() * bounds.outHeight > 150_000_000L) return null
                var sample = 1
                while ((maxOf(bounds.outWidth, bounds.outHeight).toLong() + sample - 1) / sample > maxEdge) sample *= 2
                val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
                    inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888
                }) ?: return null
                val orientation = runCatching { ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
                val matrix = Matrix()
                when (orientation) {
                    2 -> matrix.setScale(-1f, 1f)
                    3 -> matrix.setRotate(180f)
                    4 -> matrix.setScale(1f, -1f)
                    5 -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
                    6 -> matrix.setRotate(90f)
                    7 -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
                    8 -> matrix.setRotate(-90f)
                }
                if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
                    if (it !== bitmap) bitmap.recycle()
                }
            } catch (_: java.io.IOException) { null } catch (_: IllegalArgumentException) { null }
        }
        /** Three separable box passes approximate a smooth Gaussian in O(pixels), at bounded size. */
        internal fun blur(bitmap: Bitmap, radius: Int) {
            val w = bitmap.width; val h = bitmap.height
            var input = IntArray(w * h); var output = IntArray(w * h)
            bitmap.getPixels(input, 0, w, 0, 0, w, h)
            repeat(3) {
                for (horizontal in listOf(true, false)) {
                    val rows = if (horizontal) h else w
                    val columns = if (horizontal) w else h
                    val count = radius * 2 + 1
                    fun at(row: Int, column: Int) = if (horizontal) row * w + column.coerceIn(0, w - 1) else column.coerceIn(0, h - 1) * w + row
                    for (row in 0 until rows) {
                        var r = 0; var g = 0; var b = 0
                        fun add(pixel: Int, sign: Int) {
                            r += ((pixel shr 16) and 255) * sign
                            g += ((pixel shr 8) and 255) * sign
                            b += (pixel and 255) * sign
                        }
                        for (i in -radius..radius) add(input[at(row, i)], 1)
                        for (column in 0 until columns) {
                            output[at(row, column)] = Color.rgb(r / count, g / count, b / count)
                            add(input[at(row, column - radius)], -1)
                            add(input[at(row, column + radius + 1)], 1)
                        }
                    }
                    val swap = input; input = output; output = swap
                }
            }
            bitmap.setPixels(input, 0, w, 0, 0, w, h)
        }
    }
}
