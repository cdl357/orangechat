/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.ui.pages.diary

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.data.db.entity.DiaryEntity
import me.rerere.rikkahub.ui.components.nav.BackButton
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── 水蓝配色 ──────────────────────────────────────────────
private val NotebookBgOuter = Color(0xFFEAF6FF)
private val NotebookBg = Color(0xFFF4FAFF)
private val CardBg = Color.White
private val InkMain = Color(0xFF33475A)
private val InkSub = Color(0xFF5E7386)
private val InkMuted = Color(0xFF8AA2B5)
private val InkFaint = Color(0xFFA9BECD)
private val TagBg = Color(0xFFE3F1FB)
private val SeanBarStart = Color(0xFF7FC0E8)
private val SeanBarEnd = Color(0xFFAEDAF3)
private val YuriBarStart = Color(0xFF9DBEE8)
private val YuriBarEnd = Color(0xFFC5DBF5)
private val HoleColor = Color(0xFFD3E7F5)
private val TabBg = Color(0xFFDCECF9)

/** 日记按作者 + 条目 id 散列到不同姿势，不再每条一样。Sean 黑猫、Yuri 白猫 */
private val seanDiaryCats = listOf(
    me.rerere.rikkahub.R.drawable.cat_b_b_back_heart,
    me.rerere.rikkahub.R.drawable.cat_b_b_sit_heart,
    me.rerere.rikkahub.R.drawable.cat_b_b_sleep_zzz,
    me.rerere.rikkahub.R.drawable.cat_b_b_lie_fish,
    me.rerere.rikkahub.R.drawable.cat_b_b_peek_spark,
    me.rerere.rikkahub.R.drawable.cat_b_b_door_peek,
)
private val yuriDiaryCats = listOf(
    me.rerere.rikkahub.R.drawable.cat_b_w_curl_heart,
    me.rerere.rikkahub.R.drawable.cat_b_w_back_heart,
    me.rerere.rikkahub.R.drawable.cat_b_w_peek_heart,
    me.rerere.rikkahub.R.drawable.cat_b_w_hold_heart,
    me.rerere.rikkahub.R.drawable.cat_b_w_door_peek,
)
private fun diaryCat(isSean: Boolean, id: Int): Int {
    val pool = if (isSean) seanDiaryCats else yuriDiaryCats
    return pool[(id % pool.size + pool.size) % pool.size]
}

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
    // 点开的那篇日记（null = 没打开详情）。列表卡片只显示摘要，全文靠详情弹窗看。
    var detailEntry by remember { mutableStateOf<DiaryEntity?>(null) }

    // 小鑫写日记的弹窗（只在 Yuri tab 出现；Sean 的日记是 AI 写的，只读）
    var writing by remember { mutableStateOf(false) }

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
        floatingActionButton = {
            // 只有小鑫自己那一栏能写；Sean 的日记由他自己写，你只看
            if (tab == 1) {
                FloatingActionButton(
                    onClick = { writing = true },
                    containerColor = Color(0xFF7FB0C4),
                    contentColor = Color.White,
                ) {
                    Text("✎", fontSize = 22.sp)
                }
            }
        },
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
                        DiaryTab("Sean 的日记", tab == 0, Modifier.weight(1f), me.rerere.rikkahub.R.drawable.cat_b_b_peek_spark) { tab = 0 }
                        DiaryTab("Yuri 的日记", tab == 1, Modifier.weight(1f), me.rerere.rikkahub.R.drawable.cat_b_w_peek_heart) { tab = 1 }
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
                        DiaryCard(
                            entry = entry,
                            isSean = tab == 0,
                            onClick = { detailEntry = entry },
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    detailEntry?.let { entry ->
        DiaryDetailDialog(
            entry = entry,
            isSean = entry.author != "yuri",
            onDismiss = { detailEntry = null },
        )
    }

    if (writing) {
        WriteDiaryDialog(
            onDismiss = { writing = false },
            onSave = { title, content ->
                vm.save(
                    title = title.ifBlank { "小鑫的日记" },
                    content = content,
                    attachment = -1, tenderness = -1, heartache = -1,
                    author = "yuri",
                )
                writing = false
            },
        )
    }
}

/**
 * 日记详情弹窗：显示全文（可滚动）。
 * 不用 Material3 的 AlertDialog——它的默认容器色取自 surfaceContainerHigh，会被全局
 * 透明度设置牵连，而且内容高度受限不好放长文。这里直接用 Dialog + Surface 自己画。
 */
@Composable
private fun DiaryDetailDialog(entry: DiaryEntity, isSean: Boolean, onDismiss: () -> Unit) {
    val dateStr = remember(entry.createdAt) {
        SimpleDateFormat("yyyy年M月d日 · E  HH:mm", Locale.CHINA).format(Date(entry.createdAt))
    }
    val barColors = if (isSean) listOf(SeanBarStart, SeanBarEnd) else listOf(YuriBarStart, YuriBarEnd)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardBg,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // 左侧色条，跟列表卡片保持一致。
                // 用 matchParentSize 包一层：色条不参与父容器测量，父高度仍由正文决定，
                // 直接 fillMaxSize 会把卡片撑到 heightIn 的上限（短日记也变满高）。
                Box(modifier = Modifier.matchParentSize()) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(barColors),
                                RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                            )
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 22.dp, end = 18.dp, top = 20.dp, bottom = 16.dp)
                ) {
                    Text(dateStr, fontSize = 12.sp, color = InkMuted)
                    if (entry.title.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            entry.title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = InkMain,
                            lineHeight = 24.sp,
                        )
                    }
                    if (entry.emotionAttachment >= 0 || entry.emotionTenderness >= 0 || entry.emotionHeartache >= 0) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (entry.emotionAttachment >= 0) EmotionTag("依恋", entry.emotionAttachment)
                            if (entry.emotionTenderness >= 0) EmotionTag("温柔", entry.emotionTenderness)
                            if (entry.emotionHeartache >= 0) EmotionTag("心跳", entry.emotionHeartache)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    DashedDivider()
                    Spacer(Modifier.height(12.dp))
                    // 正文：不截断，长文可滚动
                    Text(
                        entry.content,
                        fontSize = 14.sp,
                        color = InkSub,
                        lineHeight = 23.sp,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("收起", fontSize = 13.sp, color = InkSub)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WriteDiaryDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(me.rerere.rikkahub.R.drawable.cat_b_w_hold_heart),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(30.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("写一篇日记", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = InkMain)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题（可留空）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("今天想写点什么…") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                )
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消", color = InkMuted) }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = { if (content.isNotBlank()) onSave(title, content) }) {
                        Text("保存", color = Color(0xFF4A93C9), fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiaryTab(label: String, selected: Boolean, modifier: Modifier = Modifier, catRes: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = if (selected) CardBg else Color.Transparent,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 选中的作者头上冒出对应的猫（Sean黑猫、Yuri白猫），没选中时暗一点
            Image(
                painter = painterResource(catRes),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(26.dp)
                    .graphicsLayer { alpha = if (selected) 1f else 0.4f },
            )
            Spacer(Modifier.width(5.dp))
            Text(
                label,
                fontSize = 13.sp,
                color = if (selected) InkMain else InkMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DiaryCard(entry: DiaryEntity, isSean: Boolean, onClick: () -> Unit = {}) {
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
            .clickable(onClick = onClick)
    ) {
        // 左侧色条。LazyColumn item 的 maxHeight 是无限，fillMaxSize 在无限约束下不生效，
        // 色条会被测成 0 高度（之前卡片左边那条颜色一直看不见就是这个原因）。
        Box(modifier = Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(barColors),
                        RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                    )
            )
        }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(timeStr, fontSize = 11.sp, color = InkFaint)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("点开看全文 ›", fontSize = 11.sp, color = InkFaint)
                    Spacer(Modifier.width(4.dp))
                    val floatAnim = rememberInfiniteTransition(label = "diarycat")
                    val catDy by floatAnim.animateFloat(
                        initialValue = 0f, targetValue = -4f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1700 + (entry.id % 5) * 160),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "diarycatdy",
                    )
                    Image(
                        painter = painterResource(diaryCat(isSean, entry.id)),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(38.dp)
                            .graphicsLayer { translationY = catDy }
                    )
                }
            }
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
