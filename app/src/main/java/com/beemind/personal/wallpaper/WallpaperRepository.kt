package com.beemind.personal.wallpaper

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class WallpaperState(
    val ids: List<String> = emptyList(),
    val currentId: String? = null,
    val pinnedId: String? = null,
    val blur: Int = 65,
    val loaded: Boolean = false,
)

/** Private originals + one atomic preferences record. Never touches the notes database. */
class WallpaperRepository(private val directory: File, private val preferences: SharedPreferences) {
    constructor(context: Context) : this(File(context.filesDir, "wallpapers"),
        context.getSharedPreferences("wallpapers", Context.MODE_PRIVATE))
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(WallpaperState())
    val state = mutable.asStateFlow()
    fun file(id: String): File {
        require(id.matches(Regex("[a-f0-9-]{36}")))
        return File(directory, "$id.image")
    }
    private fun load() {
        if (mutable.value.loaded) return
        directory.mkdirs()
        val json = runCatching { JSONObject(preferences.getString("state", "{}")!!) }.getOrDefault(JSONObject())
        val array = json.optJSONArray("ids") ?: JSONArray()
        val ids = (0 until array.length()).map { array.optString(it) }.distinct().filter {
            it.matches(Regex("[a-f0-9-]{36}")) && file(it).isFile
        }
        val pinned = json.optString("pin").takeIf { it in ids }
        mutable.value = WallpaperState(ids, pinned ?: json.optString("current").takeIf { it in ids } ?: ids.firstOrNull(),
            pinned, json.optInt("blur", 65).coerceIn(20, 100), true)
        // Incomplete imports and files orphaned by a process death are safe to reclaim.
        directory.listFiles()?.filter { it.name !in ids.map { id -> "$id.image" }.toSet() }?.forEach { it.delete() }
    }
    // KTX edit(commit = true) discards commit's Boolean; publication must await disk success.
    @android.annotation.SuppressLint("UseKtx")
    private fun save(next: WallpaperState) {
        val json = JSONObject().put("ids", JSONArray(next.ids)).put("current", next.currentId)
            .put("pin", next.pinnedId).put("blur", next.blur)
        if (!preferences.edit().putString("state", json.toString()).commit()) throw IOException("Preferences write failed")
        mutable.value = next
    }
    suspend fun initialize() = withContext(Dispatchers.IO) { mutex.withLock { load() } }
    suspend fun newSession() = withContext(Dispatchers.IO) { mutex.withLock {
        load()
        val s = mutable.value
        val next = s.pinnedId ?: s.ids.takeIf { it.isNotEmpty() }?.let {
            it[(it.indexOf(s.currentId) + 1).mod(it.size)]
        }
        if (next != s.currentId) save(s.copy(currentId = next))
    } }
    suspend fun add(context: Context, uri: Uri) = importImage { context.contentResolver.openInputStream(uri) }
    suspend fun importImage(open: () -> InputStream?) = withContext(Dispatchers.IO) { mutex.withLock {
        load()
        val id = UUID.randomUUID().toString()
        val temporary = File(directory, "$id.tmp")
        val destination = file(id)
        try {
            (open() ?: throw IOException("Unreadable image")).use { input -> temporary.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > 40L * 1024 * 1024) throw IOException("Image exceeds 40 MiB")
                    output.write(buffer, 0, count)
                }
            } }
            // Validate with a bounded decode, not just the MIME type or extension.
            val bitmap = WallpaperImages.decode(temporary, 720) ?: throw IOException("Invalid image")
            bitmap.recycle()
            if (!temporary.renameTo(destination)) throw IOException("Image move failed")
            val s = mutable.value
            save(s.copy(ids = s.ids + id, currentId = s.currentId ?: id))
            id
        } catch (e: Exception) {
            temporary.delete(); destination.delete(); throw e
        }
    } }
    suspend fun pin(id: String?) = withContext(Dispatchers.IO) { mutex.withLock {
        load(); val s = mutable.value
        require(id == null || id in s.ids)
        save(s.copy(pinnedId = id, currentId = id ?: s.currentId))
    } }
    suspend fun remove(id: String) = withContext(Dispatchers.IO) { mutex.withLock {
        load(); val s = mutable.value
        val remaining = s.ids - id
        save(s.copy(ids = remaining, pinnedId = s.pinnedId?.takeUnless { it == id },
            currentId = s.currentId?.takeUnless { it == id } ?: remaining.firstOrNull()))
        file(id).delete()
    } }
    suspend fun setBlur(value: Int) = withContext(Dispatchers.IO) { mutex.withLock {
        load(); save(mutable.value.copy(blur = value.coerceIn(20, 100)))
    } }
}

/** Activity boundaries only. Navigation and recomposition never enter this gate. */
class WallpaperSessions {
    private var pending = true
    var pickingImages = false
    fun enter(): Boolean = pending.also { pending = false }
    fun leave(changingConfiguration: Boolean) {
        if (!changingConfiguration && !pickingImages) pending = true
    }
}
