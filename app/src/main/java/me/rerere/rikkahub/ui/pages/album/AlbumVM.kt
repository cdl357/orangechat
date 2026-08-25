package me.rerere.rikkahub.ui.pages.album

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.db.entity.AlbumEntity
import me.rerere.rikkahub.data.db.entity.AlbumFolderEntity
import me.rerere.rikkahub.data.repository.AlbumFolderRepository
import me.rerere.rikkahub.data.repository.AlbumRepository

class AlbumVM(
    private val repo: AlbumRepository,
    private val folderRepo: AlbumFolderRepository,
) : ViewModel() {
    val allItems = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allFolders = folderRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveImage(filePath: String, caption: String = "", savedBy: String = "sean", conversationId: String = "", folderId: Int = 0) {
        if (filePath.isBlank()) return
        viewModelScope.launch {
            repo.add(AlbumEntity(filePath = filePath, caption = caption, savedBy = savedBy, conversationId = conversationId, folderId = folderId))
        }
    }

    fun delete(item: AlbumEntity) { viewModelScope.launch { repo.delete(item) } }

    /**
     * 把还没上云的照片传上去，顺便给老照片补算内容哈希。
     *
     * 跑在 viewModelScope 里而不是 Composable 的 rememberCoroutineScope：
     * 后者在页面退出时会被取消，上传到一半断掉。表情包那边踩过这个坑。
     * 失败不管，下次进页面再试 —— 传不上去也不影响本地看图。
     */
    fun syncToCloud() {
        viewModelScope.launch {
            runCatching { repo.migrateLocalToCloud() }
            runCatching { repo.backfillHashes() }
        }
    }

    /** 给某张照片写/改备注。空字符串等于清掉备注。 */
    fun updateCaption(id: Int, caption: String) {
        if (id <= 0) return
        viewModelScope.launch { repo.updateCaption(id, caption.trim()) }
    }

    fun createFolder(name: String, createdBy: String = "sean") {
        if (name.isBlank()) return
        viewModelScope.launch {
            folderRepo.add(AlbumFolderEntity(name = name.trim(), createdBy = createdBy))
        }
    }

    fun deleteFolder(folder: AlbumFolderEntity) {
        viewModelScope.launch { folderRepo.delete(folder) }
    }

    fun itemsInFolder(folderId: Int) = allItems.value.filter { it.folderId == folderId }
}
