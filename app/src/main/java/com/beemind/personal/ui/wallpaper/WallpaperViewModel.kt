package com.beemind.personal.ui.wallpaper

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beemind.personal.BeeMindApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WallpaperViewModel(app: Application) : AndroidViewModel(app) {
    private val application = app as BeeMindApplication
    val repository = application.wallpapers
    val images = application.wallpaperImages
    val state = repository.state
    private val messageState = MutableStateFlow<String?>(null)
    val message = messageState.asStateFlow()
    private val importing = MutableStateFlow(false)
    val busy = importing.asStateFlow()
    init { perform { repository.initialize() } }
    private fun perform(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: CancellationException) { throw e }
        catch (_: Exception) { messageState.value = "تعذر حفظ التغيير. حاول مجددًا." }
    }
    fun import(uris: List<Uri>) {
        if (importing.value || uris.isEmpty()) return
        perform {
            importing.value = true
            var failed = 0
            try {
                for (uri in uris) {
                    try { repository.add(application, uri) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { failed++ }
                }
                messageState.value = if (failed == 0) "أُضيفت الصور إلى مساحة التطبيق الخاصة." else
                    "تعذر إضافة $failed من الصور. قد تكون تالفة أو تتجاوز 40 ميغابايت؛ أُضيفت الصور الصالحة."
            } finally { importing.value = false }
        }
    }
    fun pin(id: String?) { perform { repository.pin(id) } }
    fun remove(id: String) { perform { repository.remove(id) } }
    fun blur(value: Int) { perform { repository.setBlur(value) } }
    fun pickerFailed() { messageState.value = "تعذر فتح منتقي الصور في النظام." }
    fun dismissMessage() { messageState.value = null }
}
