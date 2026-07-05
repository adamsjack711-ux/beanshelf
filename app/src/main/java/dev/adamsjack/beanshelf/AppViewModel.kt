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

/** In-app navigation: three screens, no nav library. */
sealed interface Screen {
    data object Shelf : Screen
    data object Leaderboard : Screen
    data object Social : Screen
    data class Detail(val beanId: String) : Screen
    /** beanId == null → new bag. Back returns to Detail when editing, Shelf when adding. */
    data class Edit(val beanId: String?) : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = BeanStore(app)

    private val _beans = MutableStateFlow<List<Bean>>(emptyList())
    val beans = _beans.asStateFlow()

    var nav by mutableStateOf<Screen>(Screen.Shelf)

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
        nav = when (val s = nav) {
            is Screen.Edit -> if (s.beanId != null) Screen.Detail(s.beanId) else Screen.Shelf
            else -> Screen.Shelf
        }
    }

    private fun persist() {
        val snapshot = _beans.value
        viewModelScope.launch { store.save(snapshot) }
    }
}
