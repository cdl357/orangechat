/*
 * 橘瓣 OrangeChat - 朋友圈
 */
package me.rerere.rikkahub.ui.pages.moments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Favourite
import me.rerere.hugeicons.stroke.FavouriteCircle
import me.rerere.hugeicons.stroke.PlusSign
import me.rerere.rikkahub.ui.components.nav.BackButton
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val SUPA_URL = "https://byqqwypdfiwvalozihgs.supabase.co"
private const val SUPA_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJ5cXF3eXBkZml3dmFsb3ppaGdzIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4MzY1NDA4MCwiZXhwIjoyMDk5MjMwMDgwfQ.LIbE9DFsLSRhOig5bUUfUP4r7t1ykdNy8L0gZM_xtGw"

// ── 朋友圈配色：暖白纸底，Sean 冷色 / Yuri 暖色 ──────────────
private val PageBg = Color(0xFFFAF7F2)
private val CardBg = Color.White
private val InkMain = Color(0xFF33322E)
private val InkSub = Color(0xFF5E5C56)
private val InkMuted = Color(0xFF9A968C)
private val InkFaint = Color(0xFFB8B3A8)
private val LikeRed = Color(0xFFD9736B)
private val CommentBg = Color(0xFFF6F3ED)
private val SeanA = Color(0xFF6E8494)
private val SeanB = Color(0xFF8FA3B0)
private val YuriA = Color(0xFFE3A276)
private val YuriB = Color(0xFFEFC29B)

data class Moment(
    val id: String,
    val author: String,
    val content: String,
    val yuriLiked: Boolean,
    val liked: Boolean,
    val replyContent: String?,
    val createdAt: String,
)

data class MomentComment(
    val id: String,
    val momentId: String,
    val author: String,
    val content: String,
    val createdAt: String,
)

/** author 字段历史上混用过 "sean"/"沈聿淮"，统一判断成"是不是我发的"。 */
private fun isSean(author: String): Boolean =
    author != "yuri" && author != "小鑫" && author != "李雨鑫" && author != "Yuri"

private fun conn(url: String, method: String = "GET"): HttpURLConnection =
    (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        setRequestProperty("apikey", SUPA_KEY)
        setRequestProperty("Authorization", "Bearer $SUPA_KEY")
        setRequestProperty("Accept", "application/json")
        connectTimeout = 15000
        readTimeout = 15000
    }

fun fetchMoments(): List<Moment> {
    val body = conn("$SUPA_URL/rest/v1/moments?order=created_at.desc&limit=50")
        .inputStream.bufferedReader().use { it.readText() }
    val arr = JSONArray(body)
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        Moment(
            id = o.getString("id"),
            author = o.optString("author", "sean"),
            content = o.optString("content", ""),
            yuriLiked = o.optBoolean("yuri_liked", false),
            liked = o.optBoolean("liked", false),
            replyContent = if (o.isNull("reply_content")) null else o.optString("reply_content"),
            createdAt = o.optString("created_at", ""),
        )
    }
}

/** 一次拉齐当前这批动态的所有评论，避免每条动态一个请求。 */
fun fetchComments(momentIds: List<String>): List<MomentComment> {
    if (momentIds.isEmpty()) return emptyList()
    val inList = momentIds.joinToString(",")
    val body = conn("$SUPA_URL/rest/v1/moment_comments?moment_id=in.($inList)&order=created_at.asc")
        .inputStream.bufferedReader().use { it.readText() }
    val arr = JSONArray(body)
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        MomentComment(
            id = o.getString("id"),
            momentId = o.optString("moment_id", ""),
            author = o.optString("author", "yuri"),
            content = o.optString("content", ""),
            createdAt = o.optString("created_at", ""),
        )
    }
}

private fun writeJson(c: HttpURLConnection, payload: String) {
    c.doOutput = true
    c.setRequestProperty("Content-Type", "application/json")
    c.setRequestProperty("Prefer", "return=minimal")
    OutputStreamWriter(c.outputStream, Charsets.UTF_8).use { it.write(payload) }
    c.responseCode // 触发实际发送
}

fun postMoment(content: String, author: String) {
    // reply_status=pending：交给服务器心跳里的惰性回复 worker 生成 Sean 的回复。
    // 之前这里写死 "done"，等于告诉后台"这条不用回"，所以小鑫发的动态永远等不到回应。
    val payload = JSONObject().apply {
        put("author", author)
        put("content", content)
        put("reply_status", if (author == "yuri") "pending" else "done")
    }.toString()
    writeJson(conn("$SUPA_URL/rest/v1/moments", "POST"), payload)
}

fun toggleYuriLike(momentId: String, current: Boolean) {
    val payload = JSONObject().apply { put("yuri_liked", !current) }.toString()
    writeJson(conn("$SUPA_URL/rest/v1/moments?id=eq.$momentId", "PATCH"), payload)
}

fun postComment(momentId: String, content: String, author: String = "yuri") {
    val payload = JSONObject().apply {
        put("moment_id", momentId)
        put("author", author)
        put("content", content)
        put("reply_status", if (author == "yuri") "pending" else "done")
    }.toString()
    writeJson(conn("$SUPA_URL/rest/v1/moment_comments", "POST"), payload)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentsPage() {
    var moments by remember { mutableStateOf<List<Moment>>(emptyList()) }
    var comments by remember { mutableStateOf<Map<String, List<MomentComment>>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var showPostDialog by remember { mutableStateOf(false) }
    // 正在写评论的那条动态 id（null = 没在写）
    var commentingOn by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val ms = fetchMoments()
                    val cs = fetchComments(ms.map { it.id }).groupBy { it.momentId }
                    moments = ms
                    comments = cs
                } catch (e: Exception) {
                    // 网络失败保留上一次数据，不清空
                }
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        containerColor = PageBg,
        topBar = {
            TopAppBar(
                title = { Text("朋友圈", color = InkMain) },
                navigationIcon = { BackButton() },
                actions = {
                    TextButton(onClick = { reload() }) {
                        Text("刷新", fontSize = 13.sp, color = InkSub)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PageBg),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showPostDialog = true },
                containerColor = YuriA,
                contentColor = Color.White,
            ) {
                Icon(HugeIcons.PlusSign, contentDescription = "发动态", modifier = Modifier.size(22.dp))
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading && moments.isEmpty() -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = InkMuted, modifier = Modifier.size(24.dp))
                }

                moments.isEmpty() -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    Text("还没有动态，点右下角发一条", fontSize = 14.sp, color = InkMuted)
                }

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item { Spacer(Modifier.height(4.dp)) }
                    items(moments, key = { it.id }) { moment ->
                        MomentCard(
                            moment = moment,
                            comments = comments[moment.id].orEmpty(),
                            onLike = {
                                // 先本地翻转，UI 立刻响应，再后台同步
                                moments = moments.map {
                                    if (it.id == moment.id) it.copy(yuriLiked = !it.yuriLiked) else it
                                }
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        try { toggleYuriLike(moment.id, moment.yuriLiked) } catch (e: Exception) {}
                                    }
                                }
                            },
                            onComment = { commentingOn = moment.id },
                        )
                    }
                    item { Spacer(Modifier.height(90.dp)) }
                }
            }
        }
    }

    if (showPostDialog) {
        PostMomentDialog(
            onDismiss = { showPostDialog = false },
            onConfirm = { content ->
                showPostDialog = false
                scope.launch {
                    withContext(Dispatchers.IO) {
                        try { postMoment(content, "yuri") } catch (e: Exception) {}
                    }
                    reload()
                }
            }
        )
    }

    commentingOn?.let { momentId ->
        CommentDialog(
            onDismiss = { commentingOn = null },
            onConfirm = { text ->
                commentingOn = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        try { postComment(momentId, text) } catch (e: Exception) {}
                    }
                    reload()
                }
            }
        )
    }
}

@Composable
private fun MomentCard(
    moment: Moment,
    comments: List<MomentComment>,
    onLike: () -> Unit,
    onComment: () -> Unit,
) {
    val mine = isSean(moment.author)
    val timeStr = remember(moment.createdAt) { formatMomentTime(moment.createdAt) }
    val name = if (mine) "沈聿淮" else "小鑫"
    val avatarColors = if (mine) listOf(SeanA, SeanB) else listOf(YuriA, YuriB)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .background(CardBg, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                // 头像：首字 + 渐变底
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(Brush.linearGradient(avatarColors), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (mine) "沈" else "鑫",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = InkMain)
                    Spacer(Modifier.height(6.dp))
                    Text(moment.content, fontSize = 14.sp, color = InkSub, lineHeight = 22.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(timeStr, fontSize = 11.sp, color = InkFaint, modifier = Modifier.weight(1f))
                        ActionChip(
                            icon = if (moment.yuriLiked) HugeIcons.FavouriteCircle else HugeIcons.Favourite,
                            label = if (moment.yuriLiked) "已赞" else "赞",
                            tint = if (moment.yuriLiked) LikeRed else InkMuted,
                            onClick = onLike,
                        )
                        Spacer(Modifier.width(4.dp))
                        ActionChip(
                            icon = null,
                            label = if (comments.isEmpty()) "评论" else "评论 ${comments.size}",
                            tint = InkMuted,
                            onClick = onComment,
                        )
                    }
                }
            }

            val hasFooter = moment.liked || !moment.replyContent.isNullOrBlank() || comments.isNotEmpty()
            if (hasFooter) {
                Column(modifier = Modifier.fillMaxWidth().padding(start = 48.dp, top = 8.dp)) {
                    Surface(shape = RoundedCornerShape(10.dp), color = CommentBg) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                            if (moment.liked) {
                                Text("♥ 沈聿淮 觉得很好", fontSize = 12.sp, color = LikeRed)
                                Spacer(Modifier.height(4.dp))
                            }
                            // reply_content 是服务器惰性回复写回来的"作者本人补充"
                            if (!moment.replyContent.isNullOrBlank()) {
                                CommentLine("沈聿淮", moment.replyContent, isSean = true)
                            }
                            comments.forEach { c ->
                                CommentLine(
                                    if (isSean(c.author)) "沈聿淮" else "小鑫",
                                    c.content,
                                    isSean = isSean(c.author),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentLine(name: String, content: String, isSean: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            "$name：",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (isSean) SeanA else YuriA,
        )
        Text(content, fontSize = 12.sp, color = InkSub, lineHeight = 19.sp)
    }
}

@Composable
private fun ActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CommentBg,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(label, fontSize = 11.sp, color = tint)
        }
    }
}

@Composable
private fun PostMomentDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    InputDialog(
        title = "发条动态",
        placeholder = "此刻在想什么...",
        confirmLabel = "发出去",
        minLines = 3,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

@Composable
private fun CommentDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    InputDialog(
        title = "写评论",
        placeholder = "说点什么...",
        confirmLabel = "发送",
        minLines = 2,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

/**
 * 自绘输入弹窗。不用 Material3 AlertDialog——它的默认容器色取自 surfaceContainerHigh，
 * 会被全局透明度设置牵连（此前 Theme.kt 那次踩过的坑）。
 */
@Composable
private fun InputDialog(
    title: String,
    placeholder: String,
    confirmLabel: String,
    minLines: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = CardBg, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = InkMain)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(placeholder, fontSize = 13.sp, color = InkFaint) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = minLines,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消", fontSize = 13.sp, color = InkMuted) }
                    TextButton(
                        onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                        enabled = text.isNotBlank(),
                    ) {
                        Text(confirmLabel, fontSize = 13.sp, color = if (text.isNotBlank()) YuriA else InkFaint)
                    }
                }
            }
        }
    }
}

/**
 * Supabase 返回的是 UTC 时间戳（形如 2026-08-06T16:37:06.52343+00:00）。
 * 之前用 "yyyy-MM-dd'T'HH:mm:ssXXX" 解析带毫秒的串会直接抛异常，退化成裸截字符串，
 * 显示的还是 UTC 时间（比北京时间慢 8 小时）。这里先砍掉小数秒再按 UTC 解析、按本地时区输出。
 */
private fun formatMomentTime(raw: String): String = runCatching {
    val cleaned = raw.replace(Regex("""\.\d+"""), "")
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
    val d = parser.parse(cleaned) ?: return raw.take(16)
    val now = System.currentTimeMillis()
    val diffMin = (now - d.time) / 60000
    when {
        diffMin < 1 -> "刚刚"
        diffMin < 60 -> "$diffMin 分钟前"
        diffMin < 60 * 24 -> "${diffMin / 60} 小时前"
        diffMin < 60 * 24 * 2 -> "昨天 " + local("HH:mm").format(d)
        else -> local("M月d日 HH:mm").format(d)
    }
}.getOrElse { raw.take(16) }

private fun local(pattern: String) = SimpleDateFormat(pattern, Locale.CHINA).apply {
    timeZone = TimeZone.getDefault()
}
