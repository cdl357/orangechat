/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.ui.pages.bulletin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.db.entity.BulletinEntity
import me.rerere.rikkahub.data.repository.BulletinRepository

class BulletinVM(private val repo: BulletinRepository) : ViewModel() {

    val seanNotes = repo.observeByAuthor("sean")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val yuriNotes = repo.observeByAuthor("yuri")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 贴一张便签。replyTo 传被回复便签的 id，0 表示独立新贴。
     */
    fun post(content: String, author: String, replyTo: Int = 0) {
        if (content.isBlank()) return
        viewModelScope.launch {
            repo.add(
                BulletinEntity(
                    content = content.trim(),
                    author = author,
                    replyTo = replyTo,
                )
            )
        }
    }

    fun delete(item: BulletinEntity) {
        viewModelScope.launch { repo.delete(item) }
    }

    fun toggleCollapse(item: BulletinEntity) {
        viewModelScope.launch { repo.setCollapsed(item.id, !item.collapsed) }
    }
}
