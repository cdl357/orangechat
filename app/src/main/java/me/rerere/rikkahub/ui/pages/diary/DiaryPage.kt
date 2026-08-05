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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.PlusSign
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryPage(vm: DiaryVM = koinViewModel()) {
    val entries by vm.allEntries.collectAsStateWithLifecycle()
    var showWriteDialog by remember { mutableStateOf(false) }
    // tab: 0 = Sean 的日记, 1 = Yuri 的日记（当前数据都是 Sean 写的，预留切换）
    var tab by remember { mutableStateOf(0) }

    Scaffold(
        containerColor = NotebookBgOuter,
        topBar = {
            TopAppBar(
                title = { Text("日记本", color = InkMain) },
                navigationIcon = { BackButton() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NotebookBgOuter),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showWriteDialog = true },
                containerColor = Color(0xFFA8C4C4),
                contentColor = Color.White,
            ) {
                Icon(HugeIcons.PlusSign, contentDescription = "写日记")
            }
        }
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
                                "还没有日记，点 + 写一篇",
                                fontSize = 14.sp,
                                color = InkMuted,
                            )
                        }
                    }
                } else {
                    items(entries, key = { it.id }) { entry ->
                        DiaryCard(
                            entry = entry,
                            onDelete = { vm.delete(entry) }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showWriteDialog) {
        WriteDiaryDialog(
            onDismiss = { showWriteDialog = false },
            onConfirm = { title, content, att, ten, hea ->
                vm.save(title, content, att, ten, hea)
                showWriteDialog = false
            }
        )
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
private fun DiaryCard(entry: DiaryEntity, onDelete: () -> Unit) {
    val dateStr = remember(entry.createdAt) {
        SimpleDateFormat("yyyy年M月d日 · E", Locale.CHINA).format(Date(entry.createdAt))
    }
    val timeStr = remember(entry.createdAt) {
        SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(entry.createdAt))
    }

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
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(SeanBarStart, SeanBarEnd)
                    ),
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
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (entry.emotionAttachment >= 0 || entry.emotionTenderness >= 0 || entry.emotionHeartache >= 0) {
                        Text("💭", fontSize = 13.sp)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
                        Icon(
                            HugeIcons.Delete01,
                            contentDescription = "删除",
                            modifier = Modifier.size(13.dp),
                            tint = InkFaint,
                        )
                    }
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

@Composable
private fun WriteDiaryDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, content: String, attachment: Int, tenderness: Int, heartache: Int) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var attachment by remember { mutableFloatStateOf(-1f) }
    var tenderness by remember { mutableFloatStateOf(-1f) }
    var heartache by remember { mutableFloatStateOf(-1f) }
    var useEmotion by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("写日记") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题（可不填）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("内容") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.Switch(
                        checked = useEmotion,
                        onCheckedChange = {
                            useEmotion = it
                            if (it) {
                                if (attachment < 0) attachment = 5f
                                if (tenderness < 0) tenderness = 5f
                                if (heartache < 0) heartache = 5f
                            } else {
                                attachment = -1f; tenderness = -1f; heartache = -1f
                            }
                        }
                    )
                    Text("填写情绪值", style = MaterialTheme.typography.bodySmall)
                }
                if (useEmotion) {
                    EmotionSlider("依恋", attachment) { attachment = it }
                    EmotionSlider("温柔", tenderness) { tenderness = it }
                    EmotionSlider("心跳", heartache) { heartache = it }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    onConfirm(title, content, attachment.toInt(), tenderness.toInt(), heartache.toInt())
                },
                enabled = content.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun EmotionSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(value.toInt().toString(), style = MaterialTheme.typography.labelMedium)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = 0f..10f, steps = 9)
    }
}
