/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.ui.pages.todo

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.PlusSign
import me.rerere.hugeicons.stroke.Time02
import me.rerere.rikkahub.data.db.entity.TodoEntity
import me.rerere.rikkahub.ui.components.nav.BackButton
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoPage(vm: TodoVM = koinViewModel()) {
    val seanItems by vm.seanItems.collectAsStateWithLifecycle()
    val yuriItems by vm.yuriItems.collectAsStateWithLifecycle()
    val doneItems by vm.doneItems.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    // tab: 0=Sean给Yuri  1=Yuri给Sean  2=已完成
    var tab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("待办", style = MaterialTheme.typography.titleMedium)
                },
                navigationIcon = { BackButton() },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(HugeIcons.PlusSign, contentDescription = "新增待办")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // tab bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TabChip("Sean → Yuri", tab == 0) { tab = 0 }
                TabChip("Yuri → Sean", tab == 1) { tab = 1 }
                TabChip("已完成 (${doneItems.size})", tab == 2) { tab = 2 }
            }

            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            val displayItems = when (tab) {
                0 -> yuriItems  // Sean 写的，写给 Yuri 看
                1 -> seanItems  // Yuri 写的，写给 Sean 看
                else -> doneItems
            }

            if (displayItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (tab == 2) "还没有完成的待办" else "还没有待办，点 + 新增一项",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayItems, key = { it.id }) { item ->
                        TodoItemCard(
                            item = item,
                            onToggle = { vm.toggleDone(item) },
                            onDelete = { vm.deleteItem(item) }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showAddDialog) {
        AddTodoDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { content, author, target, reminder, repeat ->
                vm.addItem(content, author, target, reminder, repeat)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "tab_bg"
    )
    Surface(
        onClick = onClick,
        color = bg,
        shape = RoundedCornerShape(20.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected)
                MaterialTheme.colorScheme.onPrimaryContainer
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TodoItemCard(
    item: TodoEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = item.done,
                onCheckedChange = { onToggle() },
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.content,
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (item.done)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurface,
                )
                if (item.reminderTime.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            HugeIcons.Time02,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val repeatLabel = when (item.repeatMode) {
                            "daily" -> " 每天"
                            "weekly" -> " 每周"
                            else -> ""
                        }
                        Text(
                            text = "${item.reminderTime}$repeatLabel · ${item.author}写的",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = "${item.author}写的",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    HugeIcons.Delete01,
                    contentDescription = "删除",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AddTodoDialog(
    onDismiss: () -> Unit,
    onConfirm: (content: String, author: String, target: String, reminder: String, repeat: String) -> Unit,
) {
    var content by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("sean") }
    var target by remember { mutableStateOf("yuri") }
    var reminder by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("none") }
    var showRepeatMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增待办") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("内容") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                // 作者选择
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("谁写的：", style = MaterialTheme.typography.bodySmall)
                    TabChip("Sean", author == "sean") { author = "sean"; target = "yuri" }
                    TabChip("Yuri", author == "yuri") { author = "yuri"; target = "sean" }
                }
                // 提醒时间
                OutlinedTextField(
                    value = reminder,
                    onValueChange = { reminder = it },
                    label = { Text("提醒时间（HH:mm，可不填）") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("例：21:00") },
                )
                // 重复
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("重复：", style = MaterialTheme.typography.bodySmall)
                    Box {
                        Surface(
                            onClick = { showRepeatMenu = true },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Text(
                                text = when (repeat) {
                                    "daily" -> "每天"
                                    "weekly" -> "每周"
                                    else -> "不重复"
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        DropdownMenu(
                            expanded = showRepeatMenu,
                            onDismissRequest = { showRepeatMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("不重复") },
                                onClick = { repeat = "none"; showRepeatMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("每天") },
                                onClick = { repeat = "daily"; showRepeatMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("每周") },
                                onClick = { repeat = "weekly"; showRepeatMenu = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { onConfirm(content, author, target, reminder, repeat) },
                enabled = content.isNotBlank()
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
