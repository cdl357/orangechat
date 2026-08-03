/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.ui.pages.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.db.entity.TodoEntity
import me.rerere.rikkahub.data.repository.TodoRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class TodoVM(private val repo: TodoRepository) : ViewModel() {

    /** 小鑫写给 Sean 的（sean 看的那栏） */
    val seanItems = repo.observeActiveForTarget("sean")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Sean 写给小鑫的（小鑫看的那栏） */
    val yuriItems = repo.observeActiveForTarget("yuri")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 已完成 */
    val doneItems = repo.observeDone()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun addItem(
        content: String,
        author: String,
        target: String,
        reminderTime: String = "",
        repeatMode: String = "none",
    ) {
        if (content.isBlank()) return
        viewModelScope.launch {
            repo.add(
                TodoEntity(
                    content = content.trim(),
                    author = author,
                    target = target,
                    reminderTime = reminderTime,
                    repeatMode = repeatMode,
                    dateGroup = LocalDate.now().format(dateFormatter),
                )
            )
        }
    }

    fun toggleDone(item: TodoEntity) {
        viewModelScope.launch {
            repo.setDone(item.id, !item.done)
        }
    }

    fun deleteItem(item: TodoEntity) {
        viewModelScope.launch {
            repo.delete(item)
        }
    }

    fun updateItem(item: TodoEntity) {
        viewModelScope.launch {
            repo.update(item)
        }
    }
}
