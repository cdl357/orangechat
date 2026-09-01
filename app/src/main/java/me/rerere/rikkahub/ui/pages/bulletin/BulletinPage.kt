/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.ui.pages.bulletin

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import me.rerere.rikkahub.R
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlin.random.Random

// ── 水蓝配色 ──────────────────────────────────────────────
private val BoardBgTop = Color(0xFFEAF6FF)
private val BoardBgBottom = Color(0xFFD3EAFB)
private val WoodBarStart = Color(0xFF9AD0F0)
private val WoodBarEnd = Color(0xFFC3E5F8)
private val InkMain = Color(0xFF33475A)
private val InkSub = Color(0xFF8AA2B5)
private val TapeColor = Color(0xB3EAF6FF)
private val ThreadLine = Color(0x338AA2B5)
private val AccentBlue = Color(0xFF7FC0E8)

// 便签底色统一走水蓝系，深浅错开，整版是一片海的感觉
private val noteColors = listOf(
    listOf(Color(0xFFEAF6FF), Color(0xFFD6ECFB)),   // 浅蓝
    listOf(Color(0xFFE3F1FF), Color(0xFFCBE4F9)),   // 天蓝
    listOf(Color(0xFFEAF3FB), Color(0xFFD2E7F5)),   // 雾蓝
    listOf(Color(0xFFE6F4F7), Color(0xFFCDE8ED)),   // 青蓝
    listOf(Color(0xFFEFF3FF), Color(0xFFDAE2F8)),   // 蓝紫
)


// ── 心情 → 猫 drawable。author=sean 用黑猫，author=yuri 用白猫，姿势按心情 ──
// 单猫姿势缺失的（生气/委屈/撒娇）退化用双猫图，寓意"闹别扭还是黏一起"
val bulletinMoods = listOf("开心", "想你", "害羞", "馋", "困", "偷看", "撒娇", "平静", "生气", "委屈")

private fun moodCat(author: String, mood: String): Int {
    val yuri = author == "yuri"
    return when (mood) {
        "开心" -> if (yuri) R.drawable.cat_b_w_run_heart else R.drawable.cat_b_b_run
        "想你" -> if (yuri) R.drawable.cat_b_w_hold_heart else R.drawable.cat_b_b_sit_heart
        "害羞" -> if (yuri) R.drawable.cat_b_w_peek_heart else R.drawable.cat_b_b_peek_spark
        "馋" -> if (yuri) R.drawable.cat_a_pounce_white else R.drawable.cat_b_b_lie_fish
        "困" -> if (yuri) R.drawable.cat_b_w_curl_heart else R.drawable.cat_b_b_sleep_zzz
        "偷看" -> if (yuri) R.drawable.cat_b_w_door_peek else R.drawable.cat_b_b_door_peek
        "撒娇" -> if (yuri) R.drawable.cat_a_kiss else R.drawable.cat_b_b_hug_fish
        "平静" -> if (yuri) R.drawable.cat_b_w_back_heart else R.drawable.cat_b_b_back_heart
        "生气" -> if (yuri) R.drawable.cat_a_chase else R.drawable.cat_a_back2back_tail
        "委屈" -> if (yuri) R.drawable.cat_a_hug_sleep else R.drawable.cat_a_hug_stand
        else -> if (yuri) R.drawable.cat_b_w_peek_heart else R.drawable.cat_b_b_peek_spark
    }
}

/** 从便签内容里解析心情标记 [mood:xxx]，解析不到返回 null */
private fun parseMood(content: String): String? {
    val m = Regex("\\[mood:(.+?)]").find(content) ?: return null
    val v = m.groupValues[1].trim()
    return if (v in bulletinMoods) v else null
}

/** 去掉正文里的心情标记，用于展示 */
private fun stripMood(content: String): String =
    content.replace(Regex("\\s*\\[mood:.+?]\\s*"), " ").trim()

/** 一串：原贴 + 挂在它下面的回复（按时间正序，先回的在上） */
private data class NoteThread(
    val root: BulletinEntity,
    val replies: List<BulletinEntity>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulletinPage(vm: BulletinVM = koinViewModel()) {
    val seanNotes by vm.seanNotes.collectAsStateWithLifecycle()
    val yuriNotes by vm.yuriNotes.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val syncMessage by vm.syncMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(syncMessage) {
        val msg = syncMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            vm.clearSyncMessage()
        }
    }
    var showAddDialog by remember { mutableStateOf(false) }
    // 不为 null 时表示正在回复这张便签
    var replyTarget by remember { mutableStateOf<BulletinEntity?>(null) }
    // 不为 null 时表示点开了某张便签看详情（回复藏在里面）
    var openTarget by remember { mutableStateOf<NoteThread?>(null) }

    val allNotes = remember(seanNotes, yuriNotes) {
        (seanNotes + yuriNotes).sortedByDescending { it.createdAt }
    }

    // 组装成"串"：原贴在上，回复挂下面。
    // reply_to 指向已被删掉的便签时（孤儿回复），当成独立原贴显示，
    // 不然它会从页面上凭空消失，看起来像留言丢了。
    val threads = remember(allNotes) {
        val idSet = allNotes.map { it.id }.toSet()
        val repliesByParent = allNotes
            .filter { it.replyTo != 0 && it.replyTo in idSet }
            .groupBy { it.replyTo }
        allNotes
            .filter { it.replyTo == 0 || it.replyTo !in idSet }
            .map { root ->
                NoteThread(
                    root = root,
                    replies = repliesByParent[root.id]?.sortedBy { it.createdAt } ?: emptyList(),
                )
            }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("留言板") },
                navigationIcon = { BackButton() },
                actions = {
                    // 手动拉一次云端便签（服务器独处时贴的，本地要拉才看得到）
                    if (syncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 16.dp).size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF7A8C8C),
                        )
                    } else {
                        TextButton(onClick = { vm.syncFromCloud() }) {
                            Text("同步", fontSize = 13.sp, color = Color(0xFF5C6E6E))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BoardBgTop),
            )
        },
        containerColor = BoardBgTop,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = AccentBlue,
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
                    val replyCount = allNotes.size - threads.size
                    Text(
                        "${allNotes.size} 张便签 · Sean ${seanNotes.size} 张 / Yuri ${yuriNotes.size} 张" +
                            if (replyCount > 0) " · 其中 $replyCount 条回复" else "",
                        fontSize = 12.sp,
                        color = InkSub,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    )
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalItemSpacing = 20.dp,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 100.dp),
                    ) {
                        items(threads, key = { it.root.id }) { thread ->
                            StickyNote(
                                note = thread.root,
                                isReply = false,
                                replyCount = thread.replies.size,
                                onDelete = { vm.delete(thread.root) },
                                onReply = { replyTarget = thread.root },
                                onOpen = { openTarget = thread },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        BulletinComposeDialog(
            replyTo = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { content, author, mood ->
                vm.post("$content [mood:$mood]", author)
                showAddDialog = false
            }
        )
    }

    replyTarget?.let { target ->
        BulletinComposeDialog(
            replyTo = target,
            onDismiss = { replyTarget = null },
            onConfirm = { content, author, mood ->
                // 回复的回复也挂在同一个原贴下面，只做一层，不无限嵌套
                val parentId = if (target.replyTo != 0) target.replyTo else target.id
                vm.post("$content [mood:$mood]", author, replyTo = parentId)
                replyTarget = null
            }
        )
    }

    // 点开某张便签看详情：正文 + 藏在里面的回复
    openTarget?.let { thread ->
        // 数据变了要跟着刷新，从最新 threads 里重新取一次
        val fresh = threads.firstOrNull { it.root.id == thread.root.id } ?: thread
        NoteDetailDialog(
            thread = fresh,
            onDismiss = { openTarget = null },
            onReply = { replyTarget = it },
            onDelete = {
                vm.delete(it)
                // 删掉的是原贴，详情就没内容了，关掉
                if (it.id == fresh.root.id) openTarget = null
            },
        )
    }
}

/** 点开便签后的详情弹窗：原贴正文 + 它下面的回复，都在这里才看得到 */
@Composable
private fun NoteDetailDialog(
    thread: NoteThread,
    onDismiss: () -> Unit,
    onReply: (BulletinEntity) -> Unit,
    onDelete: (BulletinEntity) -> Unit,
) {
    val root = thread.root
    val rootMoodOrNull = remember(root.content) { parseMood(root.content) }
    val rootMood = rootMoodOrNull ?: "平静"
    val rootFrom = if (root.author == "sean") "\uD83D\uDC8C from Sean" else "\uD83C\uDF38 from Yuri"
    val rootTime = remember(root.createdAt) {
        SimpleDateFormat("yyyy/M/d HH:mm", Locale.CHINA).format(Date(root.createdAt))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rootFrom, fontSize = 13.sp, color = InkMain, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                if (rootMoodOrNull != null) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(BoardBgTop, RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) { Text(rootMood, fontSize = 10.sp, color = InkMain) }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(moodCat(root.author, rootMood)),
                        contentDescription = rootMood,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(stripMood(root.content), fontSize = 14.sp, color = InkMain, lineHeight = 20.sp)
                }
                Text(rootTime, fontSize = 10.sp, color = InkSub)

                if (thread.replies.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ThreadLine))
                    Text("${thread.replies.size} 条留言", fontSize = 11.sp, color = InkSub)
                    thread.replies.forEach { reply ->
                        val rMood = remember(reply.content) { parseMood(reply.content) ?: "平静" }
                        val rFrom = if (reply.author == "sean") "Sean" else "Yuri"
                        val rTime = remember(reply.createdAt) {
                            SimpleDateFormat("M/d HH:mm", Locale.CHINA).format(Date(reply.createdAt))
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BoardBgTop, RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                painter = painterResource(moodCat(reply.author, rMood)),
                                contentDescription = rMood,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(38.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("$rFrom · $rTime", fontSize = 9.sp, color = InkSub)
                                Spacer(Modifier.height(2.dp))
                                Text(stripMood(reply.content), fontSize = 13.sp, color = InkMain, lineHeight = 18.sp)
                            }
                            androidx.compose.material3.IconButton(
                                onClick = { onDelete(reply) },
                                modifier = Modifier.size(20.dp),
                            ) {
                                Icon(HugeIcons.Delete01, contentDescription = "删除", modifier = Modifier.size(12.dp), tint = InkSub)
                            }
                        }
                    }
                } else {
                    Text("还没有留言，点下面「留言」写一句", fontSize = 11.sp, color = InkSub)
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = { onReply(root) }) { Text("留言") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onDelete(root) }) { Text("删除", color = Color(0xFFD08A8A)) }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}

@Composable
private fun StickyNote(
    note: BulletinEntity,
    isReply: Boolean,
    replyCount: Int = 0,
    onDelete: () -> Unit,
    onReply: () -> Unit,
    onOpen: () -> Unit = {},
) {
    val colorIdx = remember(note.id) { Random(note.id).nextInt(noteColors.size) }
    val rotation = remember(note.id, isReply) {
        if (isReply) Random(note.id + 100).nextInt(-1, 2).toFloat()
        else Random(note.id + 100).nextInt(-2, 3).toFloat()
    }
    val colors = noteColors[colorIdx]
    val timeStr = remember(note.createdAt) {
        SimpleDateFormat("yyyy/M/d HH:mm", Locale.CHINA).format(Date(note.createdAt))
    }
    val emoji = if (note.author == "sean") "\uD83D\uDC8C" else "\uD83C\uDF38"
    val fromLabel = if (note.author == "sean") "from Sean" else "from Yuri"

    // 老便签没存过心情，就别瞎标，猫用中性的"平静"
    val moodOrNull = remember(note.content) { parseMood(note.content) }
    val mood = moodOrNull ?: "平静"
    val displayContent = remember(note.content) { stripMood(note.content) }
    val catRes = remember(note.author, mood) { moodCat(note.author, mood) }

    // 猫轻轻上下浮动
    val floatAnim = rememberInfiniteTransition(label = "catfloat")
    val catDy by floatAnim.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600 + (note.id % 5) * 180),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "catdy",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .rotate(rotation)
            .clickable { onOpen() }
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
                .background(Brush.linearGradient(colors), RoundedCornerShape(6.dp))
                .padding(start = 12.dp, top = 16.dp, end = 12.dp, bottom = 10.dp)
        ) {
            // 头部：署名 + 心情
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$emoji $fromLabel", fontSize = 10.sp, color = InkSub)
                if (moodOrNull != null) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0x55FFFFFF), RoundedCornerShape(10.dp))
                            .padding(horizontal = 7.dp, vertical = 1.dp)
                    ) { Text(mood, fontSize = 9.sp, color = InkMain) }
                }
            }
            Spacer(Modifier.height(8.dp))
            // 正文预览（最多 3 行），右下留给猫，不压到文字
            Text(
                displayContent,
                fontSize = 13.sp,
                color = InkMain,
                lineHeight = 19.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            // 底部一行：时间在左，猫在右，互不遮挡
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(timeStr, fontSize = 9.sp, color = InkSub)
                    if (replyCount > 0) {
                        Spacer(Modifier.height(3.dp))
                        Text("\uD83D\uDCAC $replyCount 条留言 · 点开看", fontSize = 9.sp, color = AccentBlue)
                    }
                }
                Image(
                    painter = painterResource(catRes),
                    contentDescription = mood,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(46.dp)
                        .graphicsLayer { translationY = catDy }
                )
            }
        }
    }
}

/**
 * 贴便签 / 回复便签共用一个对话框。
 * replyTo 非空时顶部显示被回复的原文，标题变成"回复"。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BulletinComposeDialog(
    replyTo: BulletinEntity?,
    onDismiss: () -> Unit,
    onConfirm: (content: String, author: String, mood: String) -> Unit,
) {
    var content by remember { mutableStateOf("") }
    // 回复时默认署名切到"另一个人"，省一次手动点
    var author by remember {
        mutableStateOf(
            if (replyTo != null && replyTo.author == "yuri") "sean" else "yuri"
        )
    }
    var mood by remember { mutableStateOf("平静") }
    var submitted by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(if (replyTo != null) "回复这张便签" else "贴一张便签") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (replyTo != null) {
                    Surface(
                        color = Color(0xFFF3F1EC),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                if (replyTo.author == "sean") "Sean 说" else "Yuri 说",
                                fontSize = 10.sp,
                                color = InkSub,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                replyTo.content,
                                fontSize = 12.sp,
                                color = InkMain,
                                lineHeight = 17.sp,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(if (replyTo != null) "回他/她一句..." else "说点什么...") },
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
                // 心情选择 + 实时预览的猫
                Text("心情：", style = MaterialTheme.typography.bodySmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    bulletinMoods.forEach { m ->
                        Surface(
                            onClick = { mood = m },
                            color = if (mood == m) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text(m, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 11.sp)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("这条会配这只猫：", fontSize = 11.sp, color = InkSub)
                    Spacer(Modifier.width(6.dp))
                    Image(
                        painter = painterResource(moodCat(author, mood)),
                        contentDescription = mood,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(46.dp),
                    )
                }
            }
        },
        confirmButton = {
            FilledTonalButton(
                // 先上锁再回调，防连点插两条一样的（表情包那边踩过这个坑）
                onClick = {
                    if (!submitted) {
                        submitted = true
                        onConfirm(content, author, mood)
                    }
                },
                enabled = content.isNotBlank() && !submitted,
            ) {
                Text(if (replyTo != null) "回复" else "贴上去")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
