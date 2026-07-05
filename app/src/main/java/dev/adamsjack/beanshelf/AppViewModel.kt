package dev.adamsjack.beanshelf

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.adamsjack.beanshelf.data.BeanPack
import dev.adamsjack.beanshelf.data.BeanStore
import dev.adamsjack.beanshelf.data.PhotoStore
import dev.adamsjack.beanshelf.model.Bean
import dev.adamsjack.beanshelf.model.Brew
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The four persistent bottom-bar destinations. */
enum class Tab { Feed, Shelf, Profile, Settings }

/** Full-screen screens that cover the bottom bar (pushed over a tab). */
sealed interface Overlay {
    data object Leaderboard : Overlay
    data class Detail(val beanId: String) : Overlay
    /** beanId == null → new bag. Back returns to Detail when editing, else the tab. */
    data class Edit(val beanId: String?) : Overlay
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = BeanStore(app)

    private val _beans = MutableStateFlow<List<Bean>>(emptyList())
    val beans = _beans.asStateFlow()

    var tab by mutableStateOf(Tab.Shelf)
    var overlay by mutableStateOf<Overlay?>(null)

    /** Set by a beanshelf://u/<username> deep link; consumed by the Feed tab. */
    var pendingProfile by mutableStateOf<String?>(null)

    fun openProfile(username: String) {
        pendingProfile = username
        overlay = null
        tab = Tab.Feed
    }

    fun openDetail(id: String) { overlay = Overlay.Detail(id) }
    fun openEdit(id: String?) { overlay = Overlay.Edit(id) }
    fun openLeaderboard() { overlay = Overlay.Leaderboard }
    fun closeOverlay() { overlay = null }

    init {
        viewModelScope.launch { _beans.value = store.load() }
    }

    fun bean(id: String): Bean? = _beans.value.firstOrNull { it.id == id }

    fun upsertBean(bean: Bean) {
        val old = bean(bean.id)
        if (old != null && old.photoPath != null && old.photoPath != bean.photoPath) {
            PhotoStore.deletePhoto(old.photoPath)
        }
        if (old != null && old.backPhotoPath != null && old.backPhotoPath != bean.backPhotoPath) {
            PhotoStore.deletePhoto(old.backPhotoPath)
        }
        _beans.value = (_beans.value.filterNot { it.id == bean.id } + bean)
            .sortedByDescending { it.createdAt }
        persist()
    }

    fun deleteBean(id: String) {
        bean(id)?.let {
            PhotoStore.deletePhoto(it.photoPath)
            PhotoStore.deletePhoto(it.backPhotoPath)
        }
        _beans.value = _beans.value.filterNot { it.id == id }
        persist()
    }

    /** Imports a friend's .beanshelf file onto the shelf. */
    fun importBean(uri: android.net.Uri) {
        viewModelScope.launch {
            val bean = BeanPack.import(getApplication(), uri)
            if (bean != null) {
                _beans.value = (listOf(bean) + _beans.value).sortedByDescending { it.createdAt }
                persist()
                android.widget.Toast.makeText(
                    getApplication(),
                    "\"${bean.name.ifBlank { "Bean" }}\" added to your shelf",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            } else {
                android.widget.Toast.makeText(
                    getApplication(), "That file isn't a bean pack", android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    fun addBrew(beanId: String, brew: Brew) {
        _beans.value = _beans.value.map {
            if (it.id == beanId) it.copy(brews = listOf(brew) + it.brews) else it
        }
        persist()
    }

    fun goBack() {
        when (val o = overlay) {
            is Overlay.Edit -> overlay = if (o.beanId != null) Overlay.Detail(o.beanId) else null
            null -> { // on a tab root — fall back to the Shelf tab
                pendingProfile = null
                tab = Tab.Shelf
            }
            else -> overlay = null // Detail or Leaderboard → back to the tab
        }
    }

    private fun persist() {
        val snapshot = _beans.value
        viewModelScope.launch { store.save(snapshot) }
    }
}
