/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.ui.pages.bulletin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.PlusSign
import me.rerere.rikkahub.data.db.entity.BulletinEntity
import me.rerere.rikkahub.ui.components.nav.BackButton
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

// ── 软木板配色 ──────────────────────────────────────────────
private val BoardBgTop = Color(0xFFF5F3EF)
private val BoardBgBottom = Color(0xFFEBE8E2)
private val WoodBarStart = Color(0xFF8B7355)
private val WoodBarEnd = Color(0xFFA08060)
private val InkMain = Color(0xFF3A3A2A)
private val InkSub = Color(0xFFA09080)
private val TapeColor = Color(0xB3FFFAF0)

private val noteColors = listOf(
    listOf(Color(0xFFFFE8E8), Color(0xFFFFD8D8)),   // pink
    listOf(Color(0xFFFFF9E0), Color(0xFFFFF0C0)),   // yellow
    listOf(Color(0xFFE8F5E9), Color(0xFFD8ECD8)),   // green
    listOf(Color(0xFFE3F2FD), Color(0xFFD0E8F8)),   // blue
    listOf(Color(0xFFF3E5F5), Color(0xFFE8D8F0)),   // purple
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulletinPage(vm: BulletinVM = koinViewModel()) {
    val seanNotes by vm.seanNotes.collectAsStateWithLifecycle()
    val yuriNotes by vm.yuriNotes.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    // 双方留言合并显示，最新的在最上面
    val allNotes = remember(seanNotes, yuriNotes) {
        (seanNotes + yuriNotes).sortedByDescending { it.createdAt }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("留言板") },
                navigationIcon = { BackButton() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BoardBgTop),
            )
        },
        containerColor = BoardBgTop,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Color(0xFFA8C4C4),
                contentColor = Color.White,
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(HugeIcons.PlusSign, contentDescription = "贴一张")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(listOf(BoardBgTop, BoardBgBottom))
                )
        ) {
            // 木板顶部装饰条
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(
                        Brush.horizontalGradient(listOf(WoodBarStart, WoodBarEnd, WoodBarStart))
                    )
            )

            if (allNotes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "这里还没有留言，点 + 贴一张",
                        fontSize = 14.sp,
                        color = InkSub,
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        "${allNotes.size} 张便签 · Sean ${seanNotes.size} 张 / Yuri ${yuriNotes.size} 张",
                        fontSize = 12.sp,
                        color = InkSub,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    )
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalItemSpacing = 20.dp,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 100.dp),
                    ) {
                        items(allNotes, key = { it.id }) { note ->
                            StickyNote(note = note, onDelete = { vm.delete(note) })
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddBulletinDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { content, author ->
                vm.post(content, author)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun StickyNote(note: BulletinEntity, onDelete: () -> Unit) {
    val colorIdx = remember(note.id) { Random(note.id).nextInt(noteColors.size) }
    val rotation = remember(note.id) { Random(note.id + 100).nextInt(-3, 4).toFloat() }
    val colors = noteColors[colorIdx]
    // 完整年月日时间，避免"今天"这种相对时间造成误解
    val timeStr = remember(note.createdAt) {
        SimpleDateFormat("yyyy/M/d HH:mm", Locale.CHINA).format(Date(note.createdAt))
    }
    val emoji = if (note.author == "sean") "\uD83D\uDC8C" else "\uD83C\uDF38"
    val fromLabel = if (note.author == "sean") "from Sean" else "from Yuri"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .rotate(rotation)
    ) {
        // 胶带
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-8).dp)
                .width(40.dp)
                .height(14.dp)
                .rotate(-2f)
                .background(TapeColor, RoundedCornerShape(1.dp))
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(colors),
                    RoundedCornerShape(1.dp)
                )
                .padding(12.dp, 16.dp, 12.dp, 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$emoji $fromLabel", fontSize = 10.sp, color = InkSub)
                androidx.compose.material3.IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(16.dp),
                ) {
                    Icon(
                        me.rerere.hugeicons.stroke.Delete01,
                        contentDescription = "删除",
                        modifier = Modifier.size(11.dp),
                        tint = InkSub,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                note.content,
                fontSize = 13.sp,
                color = InkMain,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                timeStr,
                fontSize = 9.sp,
                color = InkSub,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
    }
}

@Composable
private fun AddBulletinDialog(
    onDismiss: () -> Unit,
    onConfirm: (content: String, author: String) -> Unit,
) {
    var content by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("yuri") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("贴一张便签") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("说点什么...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("署名：", style = MaterialTheme.typography.bodySmall)
                    Surface(
                        onClick = { author = "sean" },
                        color = if (author == "sean") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text("Sean", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp)
                    }
                    Surface(
                        onClick = { author = "yuri" },
                        color = if (author == "yuri") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text("Yuri", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = { onConfirm(content, author) }, enabled = content.isNotBlank()) {
                Text("贴上去")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
