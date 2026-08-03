/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.ui.pages.bulletin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.PlusSign
import me.rerere.rikkahub.data.db.entity.BulletinEntity
import me.rerere.rikkahub.ui.components.nav.BackButton
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulletinPage(vm: BulletinVM = koinViewModel()) {
    val seanNotes by vm.seanNotes.collectAsStateWithLifecycle()
    val yuriNotes by vm.yuriNotes.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showPostDialog by remember { mutableStateOf(false) }

    // tab 0 = Sean 贴的  tab 1 = Yuri 贴的
    val tabs = listOf("Sean 贴的 (${seanNotes.size})", "Yuri 贴的 (${yuriNotes.size})")
    val displayNotes = if (selectedTab == 0) seanNotes else yuriNotes
    // 贴留言时 author 对应当前 tab 的作者
    val currentAuthor = if (selectedTab == 0) "sean" else "yuri"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("留言板") },
                navigationIcon = { BackButton() },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showPostDialog = true }) {
                Icon(HugeIcons.PlusSign, contentDescription = "贴留言")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }

            if (displayNotes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "这里还没有留言，点 + 贴一张",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item { Spacer(Modifier.height(8.dp)) }
                    items(displayNotes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            onToggleCollapse = { vm.toggleCollapse(note) },
                            onDelete = { vm.delete(note) }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showPostDialog) {
        PostNoteDialog(
            author = currentAuthor,
            onDismiss = { showPostDialog = false },
            onConfirm = { content ->
                vm.post(content, currentAuthor)
                showPostDialog = false
            }
        )
    }
}

@Composable
private fun NoteCard(
    note: BulletinEntity,
    onToggleCollapse: () -> Unit,
    onDelete: () -> Unit,
) {
    val dateStr = remember(note.createdAt) {
        SimpleDateFormat("今天 HH:mm", Locale.CHINA).format(Date(note.createdAt))
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // 正文（折叠时只显示1行）
            Text(
                text = note.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (note.collapsed) 1 else Int.MAX_VALUE,
                overflow = if (note.collapsed) TextOverflow.Ellipsis else TextOverflow.Clip,
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onToggleCollapse,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            if (note.collapsed) "展开" else "收起来",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                    ) {
                        Icon(
                            HugeIcons.Delete01,
                            contentDescription = "删除",
                            modifier = Modifier.padding(4.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PostNoteDialog(
    author: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var content by remember { mutableStateOf("") }
    val authorLabel = if (author == "sean") "Sean" else "Yuri"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$authorLabel 贴留言") },
        text = {
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("写点什么...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
            )
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { onConfirm(content) },
                enabled = content.isNotBlank()
            ) {
                Text("贴上去")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
