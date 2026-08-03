/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.ui.pages.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryPage(vm: DiaryVM = koinViewModel()) {
    val entries by vm.allEntries.collectAsStateWithLifecycle()
    var showWriteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日记") },
                navigationIcon = { BackButton() },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showWriteDialog = true }) {
                Icon(HugeIcons.PlusSign, contentDescription = "写日记")
            }
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "还没有日记，点 + 写一篇",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { Spacer(Modifier.height(8.dp)) }
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
private fun DiaryCard(entry: DiaryEntity, onDelete: () -> Unit) {
    val dateStr = remember(entry.createdAt) {
        SimpleDateFormat("MM/dd HH:mm", Locale.CHINA).format(Date(entry.createdAt))
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (entry.title.isNotBlank()) {
                        Text(
                            entry.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        entry.content,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        HugeIcons.Delete01, "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (entry.emotionAttachment >= 0 || entry.emotionTenderness >= 0 || entry.emotionHeartache >= 0) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (entry.emotionAttachment >= 0)
                        EmotionChip("依恋", entry.emotionAttachment)
                    if (entry.emotionTenderness >= 0)
                        EmotionChip("温柔", entry.emotionTenderness)
                    if (entry.emotionHeartache >= 0)
                        EmotionChip("心跳", entry.emotionHeartache)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${entry.dateGroup}  $dateStr",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmotionChip(label: String, value: Int) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = "$label $value",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
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
    // -1 = 不填；0-10 = 有值
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
