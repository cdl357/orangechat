/*
 * 橘瓣 OrangeChat - 待审动作
 */
package me.rerere.rikkahub.ui.pages.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

// ── 配色：跟日记本一套暖白纸感 ──────────────────────────────
private val PageBg = Color(0xFFF6F3EC)
private val CardBg = Color.White
private val InkMain = Color(0xFF33322E)
private val InkBody = Color(0xFF4A4844)
private val InkSub = Color(0xFF6E6A62)
private val InkMuted = Color(0xFF9A968C)
private val InkFaint = Color(0xFFB8B3A8)
private val LineColor = Color(0xFFE8E2D6)
private val OkGreen = Color(0xFF6E8F6B)
private val NoRed = Color(0xFFC0705F)
private val ReasonBg = Color(0xFFF3EFE6)

private val Serif = FontFamily.Serif

/** 待审动作。daemon 四小时唤醒时想对外说话，会先落到这里等小鑫点头。 */
data class PendingAction(
    val id: String,
    val kind: String,
    val target: String,
    val title: String,
    val body: String,
    val reason: String,
    val status: String,
    val createdAt: String,
    val result: String,
)

private data class KindSkin(val label: String, val hint: String, val a: Color, val b: Color)

private fun skinOf(kind: String): KindSkin = when (kind) {
    "forum_reply" -> KindSkin("回帖", "要发到花园的某个帖子下面", Color(0xFF7E96A8), Color(0xFFA6BAC8))
    "forum_thread" -> KindSkin("发帖", "要在花园发一个新帖子", Color(0xFF8B8FB0), Color(0xFFB2B5CE))
    "email_out" -> KindSkin("寄信", "要发给花园外面的人", Color(0xFFC49A6C), Color(0xFFDCBB94))
    else -> KindSkin(kind, "", Color(0xFF9A968C), Color(0xFFBDB8AE))
}

private fun conn(url: String, method: String = "GET"): HttpURLConnection =
    (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        setRequestProperty("apikey", SUPA_KEY)
        setRequestProperty("Authorization", "Bearer $SUPA_KEY")
        setRequestProperty("Accept", "application/json")
        connectTimeout = 20000
        readTimeout = 20000
    }

private fun fetchActions(): List<PendingAction> {
    val body = conn(
        "$SUPA_URL/rest/v1/pending_actions" +
            "?select=id,kind,target,title,body,reason,status,created_at,result" +
            "&order=created_at.desc&limit=60"
    ).inputStream.bufferedReader().use { it.readText() }
    val arr = JSONArray(body)
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        PendingAction(
            id = o.optString("id", ""),
            kind = o.optString("kind", ""),
            target = o.optString("target", ""),
            title = o.optString("title", ""),
            body = o.optString("body", ""),
            reason = o.optString("reason", ""),
            status = o.optString("status", "pending"),
            createdAt = o.optString("created_at", ""),
            result = if (o.isNull("result")) "" else o.optString("result", ""),
        )
    }
}

/** 改状态。approved 之后由服务器下一次唤醒（每4小时）真正发出去。 */
private fun setStatus(id: String, status: String, note: String = "") {
    val payload = JSONObject().apply {
        put("status", status)
        put("reviewed_at", isoNow())
        if (note.isNotBlank()) put("result", "小鑫的意见：$note")
    }.toString()
    val c = conn("$SUPA_URL/rest/v1/pending_actions?id=eq.$id", "PATCH")
    c.doOutput = true
    c.setRequestProperty("Content-Type", "application/json")
    c.setRequestProperty("Prefer", "return=minimal")
    OutputStreamWriter(c.outputStream, Charsets.UTF_8).use { it.write(payload) }
    c.responseCode
}

private fun isoNow(): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }.format(Date())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewPage() {
    var all by remember { mutableStateOf<List<PendingAction>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var showHistory by remember { mutableStateOf(false) }
    var rejecting by remember { mutableStateOf<PendingAction?>(null) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { all = fetchActions() }
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    val pending = all.filter { it.status == "pending" }
    val history = all.filter { it.status != "pending" }
    val shown = if (showHistory) history else pending

    Scaffold(
        containerColor = PageBg,
        topBar = {
            TopAppBar(
                title = { Text("他想说的话", color = InkMain, fontSize = 17.sp) },
                navigationIcon = { BackButton() },
                actions = {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 16.dp).size(16.dp),
                            strokeWidth = 2.dp, color = InkMuted,
                        )
                    } else {
                        TextButton(onClick = { loading = true; reload() }) {
                            Text("刷新", fontSize = 12.sp, color = InkSub)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PageBg),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 说明 + 切换
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(
                    "沈聿淮每四小时独处一次。想给你写信他直接发，" +
                        "但要发到花园、或者寄给别人的话，都会先放在这里等你点头。",
                    fontSize = 11.5.sp, color = InkMuted, lineHeight = 18.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tab("待你看 ${pending.size}", !showHistory) { showHistory = false }
                    Tab("看过的 ${history.size}", showHistory) { showHistory = true }
                }
                Spacer(Modifier.height(4.dp))
            }

            LazyColumn(Modifier.fillMaxSize()) {
                if (shown.isEmpty()) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 70.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (loading) ""
                                else if (showHistory) "还没有处理过的"
                                else "他现在没有话要跟外面说",
                                fontSize = 13.sp, color = InkMuted,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                } else {
                    items(shown, key = { it.id }) { a ->
                        ActionCard(
                            action = a,
                            onApprove = {
                                all = all.map {
                                    if (it.id == a.id) it.copy(status = "approved") else it
                                }
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        runCatching { setStatus(a.id, "approved") }
                                    }
                                }
                            },
                            onReject = { rejecting = a },
                        )
                    }
                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }

    rejecting?.let { a ->
        RejectDialog(
            action = a,
            onDismiss = { rejecting = null },
            onConfirm = { note ->
                rejecting = null
                all = all.map { if (it.id == a.id) it.copy(status = "rejected") else it }
                scope.launch {
                    withContext(Dispatchers.IO) {
                        runCatching { setStatus(a.id, "rejected", note) }
                    }
                }
            },
        )
    }
}

@Composable
private fun Tab(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) CardBg else Color.Transparent,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            fontSize = 12.5.sp,
            color = if (selected) InkMain else InkMuted,
        )
    }
}

@Composable
private fun ActionCard(
    action: PendingAction,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val skin = remember(action.kind) { skinOf(action.kind) }
    val timeStr = remember(action.createdAt) { formatTime(action.createdAt) }
    var expanded by remember { mutableStateOf(false) }
    val isPending = action.status == "pending"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(CardBg, RoundedCornerShape(14.dp))
    ) {
        // 左侧色条。LazyColumn item 的 maxHeight 无限，直接 fillMaxSize 会被测成 0 高度。
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .padding(vertical = 14.dp)
                    .background(
                        Brush.verticalGradient(listOf(skin.a, skin.b)),
                        RoundedCornerShape(3.dp),
                    )
            )
        }

        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(9.dp), color = ReasonBg) {
                    Text(
                        skin.label,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 10.5.sp, color = skin.a,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(timeStr, fontFamily = Serif, fontSize = 11.sp, color = InkFaint)
                Spacer(Modifier.weight(1f))
                if (!isPending) {
                    Text(
                        when (action.status) {
                            "approved" -> "已同意 · 等他发"
                            "executed" -> "已发出"
                            "rejected" -> "你否掉了"
                            else -> action.status
                        },
                        fontSize = 10.5.sp,
                        color = if (action.status == "rejected") NoRed else OkGreen,
                    )
                }
            }

            if (action.title.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    action.title,
                    fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    color = InkMain, lineHeight = 21.sp,
                )
            }
            if (skin.hint.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(skin.hint, fontSize = 10.5.sp, color = InkFaint)
            }

            // 他为什么想说这个。这条最该看——知道动机才好判断。
            if (action.reason.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Surface(shape = RoundedCornerShape(10.dp), color = ReasonBg) {
                    Column(Modifier.fillMaxWidth().padding(10.dp)) {
                        Text("他为什么想说", fontSize = 10.sp, color = InkMuted)
                        Spacer(Modifier.height(3.dp))
                        Text(action.reason, fontSize = 12.5.sp, color = InkSub, lineHeight = 20.sp)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(LineColor))
            Spacer(Modifier.height(10.dp))

            // 正文。长的先折起来，点开看全文。
            val long = action.body.length > 220
            Text(
                if (long && !expanded) action.body.take(220) + "…" else action.body,
                fontSize = 13.5.sp, color = InkBody, lineHeight = 23.sp,
            )
            if (long) {
                Spacer(Modifier.height(6.dp))
                Text(
                    if (expanded) "收起" else "展开全文（${action.body.length} 字）",
                    fontSize = 11.sp, color = skin.a,
                    modifier = Modifier.clickable { expanded = !expanded },
                )
            }

            if (action.result.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(action.result.take(220), fontSize = 10.5.sp, color = InkFaint, lineHeight = 17.sp)
            }

            if (isPending) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onReject) {
                        Text("不发", fontSize = 13.sp, color = NoRed)
                    }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = onApprove) {
                        Text("可以发", fontSize = 13.sp, color = OkGreen, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun RejectDialog(
    action: PendingAction,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var note by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = CardBg, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text("不发这条", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = InkMain)
                Spacer(Modifier.height(6.dp))
                Text(
                    "可以写一句为什么。他下次独处时会看到，知道你的顾虑在哪。不写也行。",
                    fontSize = 11.5.sp, color = InkMuted, lineHeight = 18.sp,
                )
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(10.dp), color = PageBg, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 70.dp).padding(12.dp)) {
                        if (note.isEmpty()) {
                            Text("比如：这个说得太满了 / 别提我的事", fontSize = 12.5.sp, color = InkFaint)
                        }
                        BasicTextField(
                            value = note,
                            onValueChange = { note = it },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.5.sp, color = InkMain),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("算了", fontSize = 13.sp, color = InkMuted) }
                    TextButton(onClick = { onConfirm(note.trim()) }) {
                        Text("确定不发", fontSize = 13.sp, color = NoRed)
                    }
                }
            }
        }
    }
}

/**
 * Supabase 返回带小数秒的 UTC 时间戳（2026-08-10T06:04:12.123456+00:00）。
 * "yyyy-MM-dd'T'HH:mm:ssXXX" 解析不了小数秒会抛异常，所以先剥掉再解析、按本地时区显示。
 */
private fun formatTime(raw: String): String = runCatching {
    val cleaned = raw.replace(Regex("""\.\d+"""), "")
    val d = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(cleaned)
        ?: return raw.take(16)
    val diffMin = (System.currentTimeMillis() - d.time) / 60000
    val local = { p: String ->
        SimpleDateFormat(p, Locale.CHINA).apply { timeZone = TimeZone.getDefault() }.format(d)
    }
    when {
        diffMin < 1 -> "刚刚"
        diffMin < 60 -> "$diffMin 分钟前"
        diffMin < 60 * 24 -> "${diffMin / 60} 小时前"
        else -> local("M月d日 HH:mm")
    }
}.getOrElse { raw.take(16) }
