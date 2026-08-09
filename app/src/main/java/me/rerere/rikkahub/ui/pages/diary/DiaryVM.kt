package me.rerere.rikkahub.ui.pages.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.entity.DiaryEntity
import me.rerere.rikkahub.data.repository.DiaryRepository
import me.rerere.rikkahub.data.service.SupabaseService
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class DiaryVM(
    private val repo: DiaryRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    val allEntries = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 日记本按作者真正分开，Sean/Yuri 两个 tab 各自只看自己的数据
    val seanEntries = repo.observeByAuthor("sean")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val yuriEntries = repo.observeByAuthor("yuri")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dates = repo.observeDates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── 云端日记同步状态 ────────────────────────────────────
    private val _syncing = MutableStateFlow(false)
    val syncing = _syncing.asStateFlow()

    /** 同步结果提示；null 表示没有待展示的提示。UI 消费后调用 clearSyncMessage()。 */
    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage = _syncMessage.asStateFlow()

    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val titleDateRegex = Regex("""(\d{4}-\d{2}-\d{2})""")

    init {
        // 进页面自动拉一次，网关凌晨写的日记不用手动点也能出现
        syncFromCloud(silent = true)
    }

    /**
     * 从 Supabase 的 diary_entries 表拉云端日记写进本地 Room。
     *
     * 背景：网关（服务器）每天凌晨 3 点生成日记，只写到 Supabase；App 侧的日记本读的是本地
     * Room 的 diary_entry 表，两边从来没打通，所以侧边栏日记页一直不更新。这里做单向拉取
     * （云端 -> 本地），按标题去重，不会重复插入。
     */
    fun syncFromCloud(silent: Boolean = false) {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            try {
                val s = settingsStore.settingsFlowRaw.first().systemToolsSetting
                if (s.supabaseUrl.isBlank() || s.supabaseApiKey.isBlank()) {
                    if (!silent) _syncMessage.value = "还没配置 Supabase（设置 → 系统工具）"
                    return@launch
                }

                val service = SupabaseService(
                    supabaseUrl = s.supabaseUrl,
                    supabaseApiKey = s.supabaseApiKey,
                    tableName = s.supabaseTableName,
                )
                val result = service.fetchDiaryEntries(limit = 100)
                val rows = result.getOrElse { e ->
                    if (!silent) _syncMessage.value = "同步失败：${e.message ?: "未知错误"}"
                    return@launch
                }

                var added = 0
                for (row in rows) {
                    if (row.content.isBlank()) continue
                    val title = row.title.ifBlank { "云端日记" }
                    // 按标题去重（网关写的标题形如 "📅 2026-08-08 日记"，每天唯一）
                    if (repo.getByTitle(title) != null) continue

                    val createdAtMs = parseCreatedAt(row.createdAt)
                    val dateGroup = titleDateRegex.find(row.title)?.groupValues?.get(1)
                        ?: java.time.Instant.ofEpochMilli(createdAtMs)
                            .atZone(ZoneId.systemDefault()).toLocalDate().format(fmt)

                    repo.add(
                        DiaryEntity(
                            title = title,
                            content = row.content,
                            dateGroup = dateGroup,
                            author = authorOf(row.userId),
                            createdAt = createdAtMs,
                        )
                    )
                    added++
                }

                if (!silent || added > 0) {
                    _syncMessage.value =
                        if (added > 0) "同步到 $added 篇新日记" else "已经是最新的了"
                }
            } catch (e: Exception) {
                if (!silent) _syncMessage.value = "同步失败：${e.message ?: "未知错误"}"
            } finally {
                _syncing.value = false
            }
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    /** 云端 user_id → 本地 author。网关写的是 "ai_哥哥"，小鑫自己的是 "user_小鑫"。 */
    private fun authorOf(userId: String): String =
        if (userId.startsWith("user_")) "yuri" else "sean"

    /** 解析 Supabase 的 ISO-8601 时间戳（形如 2026-08-08T19:00:45.438814+00:00）。 */
    private fun parseCreatedAt(raw: String): Long = runCatching {
        OffsetDateTime.parse(raw).toInstant().toEpochMilli()
    }.getOrElse { System.currentTimeMillis() }

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
