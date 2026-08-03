package me.rerere.rikkahub.ui.pages.album

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.db.entity.AlbumEntity
import me.rerere.rikkahub.data.repository.AlbumRepository

class AlbumVM(private val repo: AlbumRepository) : ViewModel() {
    val allItems = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveImage(filePath: String, caption: String = "", savedBy: String = "sean", conversationId: String = "") {
        if (filePath.isBlank()) return
        viewModelScope.launch {
            repo.add(AlbumEntity(filePath = filePath, caption = caption, savedBy = savedBy, conversationId = conversationId))
        }
    }

    fun delete(item: AlbumEntity) { viewModelScope.launch { repo.delete(item) } }
}
