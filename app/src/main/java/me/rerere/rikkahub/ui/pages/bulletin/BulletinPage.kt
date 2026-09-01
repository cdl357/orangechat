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
import androidx.compose.material3.TextButton
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

// ── 软木板配色 ──────────────────────────────────────────────
private val BoardBgTop = Color(0xFFF5F3EF)
private val BoardBgBottom = Color(0xFFEBE8E2)
private val WoodBarStart = Color(0xFF8B7355)
private val WoodBarEnd = Color(0xFFA08060)
private val InkMain = Color(0xFF3A3A2A)
private val InkSub = Color(0xFFA09080)
private val TapeColor = Color(0xB3FFFAF0)
private val ThreadLine = Color(0x33A09080)

private val noteColors = listOf(
    listOf(Color(0xFFFFE8E8), Color(0xFFFFD8D8)),   // pink
    listOf(Color(0xFFFFF9E0), Color(0xFFFFF0C0)),   // yellow
    listOf(Color(0xFFE8F5E9), Color(0xFFD8ECD8)),   // green
    listOf(Color(0xFFE3F2FD), Color(0xFFD0E8F8)),   // blue
    listOf(Color(0xFFF3E5F5), Color(0xFFE8D8F0)),   // purple
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
                            NoteThreadColumn(
                                thread = thread,
                                onDelete = { vm.delete(it) },
                                onReply = { replyTarget = it },
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
}

/** 一整串（原贴 + 回复）占瀑布流里的一格，这样两列布局不会把一串拆散 */
@Composable
private fun NoteThreadColumn(
    thread: NoteThread,
    onDelete: (BulletinEntity) -> Unit,
    onReply: (BulletinEntity) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        StickyNote(
            note = thread.root,
            isReply = false,
            onDelete = { onDelete(thread.root) },
            onReply = { onReply(thread.root) },
        )
        thread.replies.forEach { reply ->
            Box(modifier = Modifier.fillMaxWidth()) {
                // 左侧一条竖线，视觉上把回复串起来
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .width(1.dp)
                        .height(28.dp)
                        .background(ThreadLine)
                )
            }
            Box(modifier = Modifier.padding(start = 14.dp)) {
                StickyNote(
                    note = reply,
                    isReply = true,
                    onDelete = { onDelete(reply) },
                    onReply = { onReply(reply) },
                )
            }
        }
    }
}

@Composable
private fun StickyNote(
    note: BulletinEntity,
    isReply: Boolean,
    onDelete: () -> Unit,
    onReply: () -> Unit,
) {
    val colorIdx = remember(note.id) { Random(note.id).nextInt(noteColors.size) }
    // 回复的便签摆得更正一点，视觉上从属于上面那张
    val rotation = remember(note.id, isReply) {
        if (isReply) Random(note.id + 100).nextInt(-1, 2).toFloat()
        else Random(note.id + 100).nextInt(-3, 4).toFloat()
    }
    val colors = noteColors[colorIdx]
    // 完整年月日时间，避免"今天"这种相对时间造成误解
    val timeStr = remember(note.createdAt) {
        SimpleDateFormat("yyyy/M/d HH:mm", Locale.CHINA).format(Date(note.createdAt))
    }
    val emoji = if (note.author == "sean") "\uD83D\uDC8C" else "\uD83C\uDF38"
    val fromLabel = if (note.author == "sean") "from Sean" else "from Yuri"

    // 心情：优先从内容里解析 [mood:xxx]，解析不到就按 id 稳定地随机给一个（老便签也有猫）
    val mood = remember(note.id, note.content) {
        parseMood(note.content) ?: bulletinMoods[Random(note.id + 7).nextInt(bulletinMoods.size)]
    }
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
    ) {
        // 胶带（回复不贴胶带，它是挂在上面那张下面的）
        if (!isReply) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-8).dp)
                    .width(40.dp)
                    .height(14.dp)
                    .rotate(-2f)
                    .background(TapeColor, RoundedCornerShape(1.dp))
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(colors),
                    RoundedCornerShape(1.dp)
                )
                .padding(
                    start = 12.dp,
                    top = if (isReply) 10.dp else 16.dp,
                    end = 12.dp,
                    bottom = 10.dp,
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isReply) "↳ $emoji $fromLabel" else "$emoji $fromLabel",
                        fontSize = 10.sp,
                        color = InkSub,
                    )
                    Spacer(Modifier.width(6.dp))
                    // 心情小胶囊
                    Box(
                        modifier = Modifier
                            .background(Color(0x40FFFFFF), RoundedCornerShape(10.dp))
                            .padding(horizontal = 7.dp, vertical = 1.dp)
                    ) {
                        Text(mood, fontSize = 9.sp, color = InkMain)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    androidx.compose.material3.IconButton(
                        onClick = onReply,
                        modifier = Modifier.size(16.dp),
                    ) {
                        Text("↩", fontSize = 11.sp, color = InkSub)
                    }
                    androidx.compose.material3.IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(16.dp),
                    ) {
                        Icon(
                            HugeIcons.Delete01,
                            contentDescription = "删除",
                            modifier = Modifier.size(11.dp),
                            tint = InkSub,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                displayContent,
                fontSize = 13.sp,
                color = InkMain,
                lineHeight = 19.sp,
                modifier = Modifier.padding(end = 44.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                timeStr,
                fontSize = 9.sp,
                color = InkSub,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
            )
        }
        // 会动的猫，蹲在便签右下角
        Image(
            painter = painterResource(catRes),
            contentDescription = mood,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 4.dp, bottom = 2.dp)
                .size(if (isReply) 40.dp else 52.dp)
                .graphicsLayer { translationY = catDy }
        )
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
