/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.ui.pages.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.data.db.entity.DiaryEntity
import me.rerere.rikkahub.ui.components.nav.BackButton
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── 日记本配色 ──────────────────────────────────────────────
private val NotebookBgOuter = Color(0xFFF0EBE3)
private val NotebookBg = Color(0xFFFAF8F5)
private val CardBg = Color.White
private val InkMain = Color(0xFF3A3A2A)
private val InkSub = Color(0xFF7A7A6A)
private val InkMuted = Color(0xFFA09080)
private val InkFaint = Color(0xFFB0A090)
private val TagBg = Color(0xFFF5F0E8)
private val SeanBarStart = Color(0xFFA8C4C4)
private val SeanBarEnd = Color(0xFFC4D4D4)
private val YuriBarStart = Color(0xFFD4A5A0)
private val YuriBarEnd = Color(0xFFE8C4C0)
private val HoleColor = Color(0xFFE8E0D8)
private val TabBg = Color(0xFFEBE5DB)

/**
 * 日记本页面：只读。
 * 日记只能通过 write_diary 工具（AI）写入，用户（小鑫）在这里只能翻看，
 * 没有新增（FAB "+"）或删除入口——权限分离，避免误删 Sean 写的记录。
 * Sean/Yuri 两个 tab 现在真正按 author 字段过滤（此前 tab 只是 UI 状态，没有实际筛选数据）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryPage(vm: DiaryVM = koinViewModel()) {
    val seanEntries by vm.seanEntries.collectAsStateWithLifecycle()
    val yuriEntries by vm.yuriEntries.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val syncMessage by vm.syncMessage.collectAsStateWithLifecycle()
    // tab: 0 = Sean 的日记, 1 = Yuri 的日记
    var tab by remember { mutableStateOf(0) }
    val entries = if (tab == 0) seanEntries else yuriEntries

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(syncMessage) {
        val msg = syncMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            vm.clearSyncMessage()
        }
    }

    Scaffold(
        containerColor = NotebookBgOuter,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("日记本", color = InkMain) },
                navigationIcon = { BackButton() },
                actions = {
                    // 手动从云端拉一次（网关每天凌晨写在 Supabase 上，本地要拉才能看到）
                    if (syncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 16.dp).size(18.dp),
                            strokeWidth = 2.dp,
                            color = InkMuted,
                        )
                    } else {
                        TextButton(onClick = { vm.syncFromCloud() }) {
                            Text("同步", fontSize = 13.sp, color = InkSub)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NotebookBgOuter),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(NotebookBg)
        ) {
            // 装订孔装饰
            Column(
                modifier = Modifier
                    .padding(start = 8.dp, top = 90.dp),
                verticalArrangement = Arrangement.spacedBy(60.dp),
            ) {
                repeat(6) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(HoleColor, CircleShape)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp, 12.dp, 20.dp, 20.dp)
                            .background(TabBg, RoundedCornerShape(20.dp))
                            .padding(4.dp),
                    ) {
                        DiaryTab("Sean 的日记", tab == 0, Modifier.weight(1f)) { tab = 0 }
                        DiaryTab("Yuri 的日记", tab == 1, Modifier.weight(1f)) { tab = 1 }
                    }
                }

                if (entries.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (tab == 0) "Sean 还没写日记\n（点右上角「同步」拉一次云端日记）"
                                else "Yuri 还没写日记",
                                fontSize = 14.sp,
                                color = InkMuted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 20.sp,
                            )
                        }
                    }
                } else {
                    items(entries, key = { it.id }) { entry ->
                        DiaryCard(entry = entry, isSean = tab == 0)
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun DiaryTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = if (selected) CardBg else Color.Transparent,
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = if (selected) InkMain else InkMuted,
            modifier = Modifier.padding(vertical = 10.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun DiaryCard(entry: DiaryEntity, isSean: Boolean) {
    val dateStr = remember(entry.createdAt) {
        SimpleDateFormat("yyyy年M月d日 · E", Locale.CHINA).format(Date(entry.createdAt))
    }
    val timeStr = remember(entry.createdAt) {
        SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(entry.createdAt))
    }
    val barColors = if (isSean) listOf(SeanBarStart, SeanBarEnd) else listOf(YuriBarStart, YuriBarEnd)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(CardBg, RoundedCornerShape(12.dp))
    ) {
        // 左侧色条
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(barColors),
                    RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                )
        )
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(dateStr, fontSize = 12.sp, color = InkMuted)
                if (entry.emotionAttachment >= 0 || entry.emotionTenderness >= 0 || entry.emotionHeartache >= 0) {
                    Text("💭", fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            if (entry.title.isNotBlank()) {
                Text(
                    entry.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = InkMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                entry.content,
                fontSize = 13.sp,
                color = InkSub,
                lineHeight = 20.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.emotionAttachment >= 0 || entry.emotionTenderness >= 0 || entry.emotionHeartache >= 0) {
                Spacer(Modifier.height(10.dp))
                DashedDivider()
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (entry.emotionAttachment >= 0) EmotionTag("依恋", entry.emotionAttachment)
                    if (entry.emotionTenderness >= 0) EmotionTag("温柔", entry.emotionTenderness)
                    if (entry.emotionHeartache >= 0) EmotionTag("心跳", entry.emotionHeartache)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(timeStr, fontSize = 11.sp, color = InkFaint)
        }
    }
}

@Composable
private fun DashedDivider() {
    androidx.compose.foundation.Canvas(
        modifier = Modifier.fillMaxWidth().height(1.dp)
    ) {
        val dashWidth = 4.dp.toPx()
        val gapWidth = 3.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(
                color = Color(0xFFE8E0D8),
                start = androidx.compose.ui.geometry.Offset(x, 0f),
                end = androidx.compose.ui.geometry.Offset(x + dashWidth, 0f),
                strokeWidth = 1.dp.toPx(),
            )
            x += dashWidth + gapWidth
        }
    }
}

@Composable
private fun EmotionTag(label: String, value: Int) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = TagBg,
    ) {
        Text(
            text = "$label $value",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            fontSize = 10.sp,
            color = InkMuted,
        )
    }
}
