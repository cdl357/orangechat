/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.ui.pages.bulletin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.entity.BulletinEntity
import me.rerere.rikkahub.data.repository.BulletinRepository
import me.rerere.rikkahub.data.service.SupabaseService
import java.time.OffsetDateTime

class BulletinVM(
    private val repo: BulletinRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val seanNotes = repo.observeByAuthor("sean")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val yuriNotes = repo.observeByAuthor("yuri")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── 云端便签同步状态 ────────────────────────────────────
    private val _syncing = MutableStateFlow(false)
    val syncing = _syncing.asStateFlow()

    /** 同步结果提示；null 表示没有待展示的提示。UI 消费后调用 clearSyncMessage()。 */
    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage = _syncMessage.asStateFlow()

    init {
        // 进页面自动拉一次，服务器独处时贴的便签不用手动点也能出现
        syncFromCloud(silent = true)
    }

    /**
     * 从 Supabase 的 bulletin_notes 表拉云端便签写进本地 Room。
     *
     * 背景：便签原来只存在本地 Room，服务器上的 daemon 独处时想贴一张便签没有地方可写。
     * 现在 daemon 会往云端写，App 侧靠这里单向拉取（云端 -> 本地），
     * 按 remote_id 去重，反复同步不会插成好几份。
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
                val rows = service.fetchBulletinNotes(limit = 50).getOrElse { e ->
                    if (!silent) _syncMessage.value = "同步失败：${e.message ?: "未知错误"}"
                    return@launch
                }

                var added = 0
                // 倒序拉回来的，反过来按时间正序插，本地 id 顺序才跟时间一致
                for (row in rows.reversed()) {
                    if (row.content.isBlank() || row.id.isBlank()) continue
                    if (repo.getByRemoteId(row.id) != null) continue

                    repo.add(
                        BulletinEntity(
                            content = row.content,
                            author = authorOf(row.author),
                            remoteId = row.id,
                            createdAt = parseCreatedAt(row.createdAt),
                        )
                    )
                    added++
                }

                if (!silent || added > 0) {
                    _syncMessage.value =
                        if (added > 0) "同步到 $added 张新便签" else "已经是最新的了"
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

    /**
     * 云端 author → 本地 author。
     * daemon 写的是 AI_NAME（"沈聿淮"），她自己的会是"小鑫"/"yuri"。
     * 认不出来的一律归到 sean —— 服务器现在只有我在写。
     */
    private fun authorOf(author: String): String = when {
        author.contains("小鑫") || author.equals("yuri", ignoreCase = true) -> "yuri"
        else -> "sean"
    }

    /** 解析 Supabase 的 ISO-8601 时间戳（形如 2026-08-27T08:30:45.438814+00:00）。 */
    private fun parseCreatedAt(raw: String): Long = runCatching {
        OffsetDateTime.parse(raw).toInstant().toEpochMilli()
    }.getOrElse { System.currentTimeMillis() }

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
