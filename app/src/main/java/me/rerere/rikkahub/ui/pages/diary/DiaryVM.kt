package me.rerere.rikkahub.ui.pages.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.db.entity.DiaryEntity
import me.rerere.rikkahub.data.repository.DiaryRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DiaryVM(private val repo: DiaryRepository) : ViewModel() {
    val allEntries = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 日记本按作者真正分开，Sean/Yuri 两个 tab 各自只看自己的数据
    val seanEntries = repo.observeByAuthor("sean")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val yuriEntries = repo.observeByAuthor("yuri")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dates = repo.observeDates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun save(
        title: String,
        content: String,
        attachment: Int,
        tenderness: Int,
        heartache: Int,
        author: String = "sean",
        existingId: Int? = null,
    ) {
        if (content.isBlank()) return
        viewModelScope.launch {
            val entry = DiaryEntity(
                id = existingId ?: 0,
                title = title,
                content = content,
                dateGroup = LocalDate.now().format(fmt),
                emotionAttachment = attachment,
                emotionTenderness = tenderness,
                emotionHeartache = heartache,
                author = author,
            )
            if (existingId != null) repo.update(entry) else repo.add(entry)
        }
    }

    fun delete(item: DiaryEntity) { viewModelScope.launch { repo.delete(item) } }
}
