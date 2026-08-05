/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.ui.pages.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.PlusSign
import me.rerere.hugeicons.stroke.Time02
import me.rerere.rikkahub.data.db.entity.TodoEntity
import me.rerere.rikkahub.ui.components.nav.BackButton
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── 收据配色 ──────────────────────────────────────────────
private val PaperBg = Color(0xFFFAF9F6)
private val PaperBgOuter = Color(0xFFF5F5F0)
private val InkMain = Color(0xFF3A3A2A)
private val InkSub = Color(0xFF6A6A5A)
private val InkMuted = Color(0xFF8A8A7A)
private val InkFaint = Color(0xFFA0A090)
private val DashLine = Color(0xFFD0D0C0)
private val DashLineLight = Color(0xFFE0E0D8)
private val FabBg = Color(0xFFD8E8E8)
private val FabIcon = Color(0xFF5A7A7A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoPage(vm: TodoVM = koinViewModel()) {
    val seanItems by vm.seanItems.collectAsStateWithLifecycle()
    val yuriItems by vm.yuriItems.collectAsStateWithLifecycle()
    val doneItems by vm.doneItems.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    // tab: 0=Sean给Yuri  1=Yuri给Sean  2=已完成
    var tab by remember { mutableStateOf(0) }

    val today = remember { Date() }
    val dateStr = remember { SimpleDateFormat("yyyy / MM / dd · E", Locale.CHINA).format(today) }
    val nowStr = remember { SimpleDateFormat("HH:mm", Locale.CHINA).format(today) }
    val receiptNo = remember { SimpleDateFormat("yy-MM-dd", Locale.CHINA).format(today) + "-001" }

    Scaffold(
        containerColor = PaperBgOuter,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "CHECKLIST",
                        letterSpacing = 4.sp,
                        fontSize = 14.sp,
                        color = InkSub,
                    )
                },
                navigationIcon = { BackButton() },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = PaperBgOuter,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = FabBg,
                contentColor = FabIcon,
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(HugeIcons.PlusSign, contentDescription = "新增待办")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(PaperBg)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
            ) {
                // ── 收据抬头 ──
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 20.dp)
                            .border(width = 0.dp, color = Color.Transparent)
                            .padding(bottom = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "S & Y · GENERAL STORE",
                            fontSize = 13.sp,
                            letterSpacing = 6.sp,
                            color = InkSub,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            dateStr,
                            fontSize = 14.sp,
                            letterSpacing = 2.sp,
                            color = InkMain,
                        )
                        Spacer(Modifier.height(16.dp))
                        ReceiptInfoRow("开店", "00:00")
                        ReceiptInfoRow("柜员", "Sean · Yuri")
                        ReceiptInfoRow("此刻", nowStr)
                        ReceiptInfoRow("单据号", "#$receiptNo")
                        Spacer(Modifier.height(4.dp))
                        DashedDivider(DashLine)
                    }
                }

                // ── Tab 切换 ──
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ReceiptTab("Sean → Yuri", tab == 0) { tab = 0 }
                        ReceiptTab("Yuri → Sean", tab == 1) { tab = 1 }
                        ReceiptTab("已完成 (${doneItems.size})", tab == 2) { tab = 2 }
                    }
                    DashedDivider(DashLine)
                    Spacer(Modifier.height(16.dp))
                }

                val displayItems = when (tab) {
                    0 -> yuriItems  // Sean 写的，写给 Yuri 看
                    1 -> seanItems  // Yuri 写的，写给 Sean 看
                    else -> doneItems
                }

                if (displayItems.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (tab == 2) "还没有完成的待办" else "还没有待办，点 + 新增一项",
                                fontSize = 13.sp,
                                color = InkFaint,
                            )
                        }
                    }
                } else {
                    item {
                        val sectionLabel = when (tab) {
                            0 -> "今日订单 · Yuri · ${displayItems.size} 项"
                            1 -> "今日订单 · Sean · ${displayItems.size} 项"
                            else -> "已完成 · ${displayItems.size} 项"
                        }
                        Text(
                            sectionLabel,
                            fontSize = 12.sp,
                            color = InkMuted,
                            modifier = Modifier.padding(bottom = 14.dp),
                        )
                    }
                    items(displayItems, key = { it.id }) { item ->
                        ReceiptTodoRow(
                            item = item,
                            onToggle = { vm.toggleDone(item) },
                            onDelete = { vm.deleteItem(item) }
                        )
                    }
                    item { Spacer(Modifier.height(100.dp)) }
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
private fun ReceiptInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 12.sp, color = InkSub)
        Text(value, fontSize = 12.sp, color = InkSub)
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun DashedDivider(color: Color) {
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
    ) {
        val dashWidth = 6.dp.toPx()
        val gapWidth = 4.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(x, 0f),
                end = androidx.compose.ui.geometry.Offset(x + dashWidth, 0f),
                strokeWidth = 1.dp.toPx(),
            )
            x += dashWidth + gapWidth
        }
    }
}

@Composable
private fun ReceiptTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) Color(0xFFE8E8E0) else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) Color(0xFFB0B0A0) else DashLine,
        ),
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (selected) InkMain else InkMuted,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ReceiptTodoRow(
    item: TodoEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // 收据风格方框勾选
            Box(
                modifier = Modifier
                    .padding(top = 2.dp, end = 12.dp)
                    .size(18.dp)
                    .border(1.5.dp, InkFaint, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Checkbox(
                    checked = item.done,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.size(18.dp),
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color.Transparent,
                        uncheckedColor = Color.Transparent,
                        checkmarkColor = InkSub,
                    )
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.content,
                    fontSize = 14.sp,
                    color = if (item.done) InkFaint else InkMain,
                    textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (item.done) {
                        Text(
                            text = "完成 · ${item.author}写的",
                            fontSize = 11.sp,
                            color = InkFaint,
                        )
                    } else if (item.reminderTime.isNotBlank()) {
                        Icon(
                            HugeIcons.Time02,
                            contentDescription = null,
                            modifier = Modifier.size(11.dp),
                            tint = InkFaint,
                        )
                        val repeatLabel = when (item.repeatMode) {
                            "daily" -> " 每天"
                            "weekly" -> " 每周"
                            else -> ""
                        }
                        Text(
                            text = "${item.reminderTime}$repeatLabel · ${item.author}写的",
                            fontSize = 11.sp,
                            color = InkFaint,
                        )
                    } else {
                        Text(
                            text = "${item.author}写的",
                            fontSize = 11.sp,
                            color = InkFaint,
                        )
                    }
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(
                    HugeIcons.Delete01,
                    contentDescription = "删除",
                    modifier = Modifier.size(14.dp),
                    tint = Color(0xFFC0C0B0),
                )
            }
        }
        DashedDivider(DashLineLight)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("谁写的：", style = MaterialTheme.typography.bodySmall)
                    ReceiptTab("Sean", author == "sean") { author = "sean"; target = "yuri" }
                    ReceiptTab("Yuri", author == "yuri") { author = "yuri"; target = "sean" }
                }
                OutlinedTextField(
                    value = reminder,
                    onValueChange = { reminder = it },
                    label = { Text("提醒时间（HH:mm，可不填）") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("例：21:00") },
                )
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
