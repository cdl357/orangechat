/*
 * 橘瓣 OrangeChat - 梦境
 */
package me.rerere.rikkahub.ui.pages.dream

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.ui.components.nav.BackButton
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

private const val SUPA_URL = "https://byqqwypdfiwvalozihgs.supabase.co"
private const val SUPA_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJ5cXF3eXBkZml3dmFsb3ppaGdzIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4MzY1NDA4MCwiZXhwIjoyMDk5MjMwMDgwfQ.LIbE9DFsLSRhOig5bUUfUP4r7t1ykdNy8L0gZM_xtGw"

// ── 夜色配色 ────────────────────────────────────────────────
// 橘瓣其他页面都是暖白纸感（日记本 / 便利贴 / 朋友圈）。梦这页故意做成反的：
// 它是她睡着以后的事，不该跟白天的东西长一个样。
private val NightBg = Color(0xFF0E1420)
private val CardBg = Color(0xFF151D2B)
private val CardBorder = Color(0xFF1E2836)
private val HeroTop = Color(0xFF1B2A44)
private val HeroMid = Color(0xFF141C2C)
private val HeroBottom = Color(0xFF111825)
private val InkBright = Color(0xFFE8EDF5)
private val InkMain = Color(0xFFC9D2E0)
private val InkBody = Color(0xFFC4CDDB)
private val InkSub = Color(0xFF8B9AB4)
private val InkMuted = Color(0xFF6B7A94)
private val InkFaint = Color(0xFF59677E)
private val InkDim = Color(0xFF4E5B70)
private val LineColor = Color(0xFF1E2836)
private val BarEmpty = Color(0xFF26303F)
private val MoonColor = Color(0xFFE4E9F2)

private val Serif = FontFamily.Serif

/** 四种梦各自的色条、标签底色、标签字色。 */
private data class DreamSkin(
    val label: String,
    val barStart: Color,
    val barEnd: Color,
    val tagBg: Color,
    val tagInk: Color,
)

private fun skinOf(type: String): DreamSkin = when (type) {
    "sweet" -> DreamSkin("好梦", Color(0xFF7FA9C9), Color(0xFFA9C6D9), Color(0xFF1D3346), Color(0xFFA9C6D9))
    "nightmare" -> DreamSkin("噩梦", Color(0xFF8E6070), Color(0xFFB08592), Color(0xFF33202A), Color(0xFFC79BA8))
    "lucid" -> DreamSkin("清醒梦", Color(0xFF8E7FC0), Color(0xFFB0A4D6), Color(0xFF2A2440), Color(0xFFBDB2DE))
    else -> DreamSkin("碎片", Color(0xFF5D6B82), Color(0xFF7B889C), Color(0xFF212936), Color(0xFF8E9BAF))
}

data class Dream(
    val id: Int,
    val dreamDate: String,
    val type: String,
    val content: String,
    val emotionTags: List<String>,
    val intensity: Double,
    val recalled: Boolean,
    val createdAt: String,
)

private fun fetchDreams(): List<Dream> {
    val url = URL(
        "$SUPA_URL/rest/v1/dream_events" +
            "?select=id,dream_date,type,content,emotion_tags,intensity,recalled,created_at" +
            "&order=dream_date.desc,id.desc&limit=120"
    )
    val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        setRequestProperty("apikey", SUPA_KEY)
        setRequestProperty("Authorization", "Bearer $SUPA_KEY")
        setRequestProperty("Accept", "application/json")
        connectTimeout = 20000
        readTimeout = 20000
    }
    val body = conn.inputStream.bufferedReader().use { it.readText() }
    val arr = JSONArray(body)
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val tags = mutableListOf<String>()
        o.optJSONArray("emotion_tags")?.let { ta ->
            for (j in 0 until ta.length()) {
                ta.optString(j, "").takeIf { it.isNotBlank() }?.let(tags::add)
            }
        }
        Dream(
            id = o.optInt("id", 0),
            dreamDate = o.optString("dream_date", ""),
            type = o.optString("type", "fragment"),
            content = o.optString("content", ""),
            emotionTags = tags,
            intensity = o.optDouble("intensity", 0.0),
            recalled = o.optBoolean("recalled", true),
            createdAt = o.optString("created_at", ""),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DreamPage() {
    var dreams by remember { mutableStateOf<List<Dream>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    // 点开过的"忘了的梦"，点一下才看清（像回想）
    var revealed by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    dreams = fetchDreams()
                    failed = false
                } catch (e: Exception) {
                    failed = dreams.isEmpty()
                }
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        containerColor = NightBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Dreams",
                        fontFamily = Serif,
                        fontStyle = FontStyle.Italic,
                        fontSize = 19.sp,
                        color = InkMain,
                    )
                },
                navigationIcon = { BackButton() },
                actions = {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 16.dp).size(16.dp),
                            strokeWidth = 2.dp,
                            color = InkMuted,
                        )
                    } else {
                        TextButton(onClick = { loading = true; reload() }) {
                            Text("同步", fontSize = 12.sp, color = InkFaint)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NightBg),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item { DreamHero(dreams) }

            if (dreams.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 70.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (loading) ""
                            else if (failed) "读不到梦（网络？）"
                            else "他还没做过梦\n（每晚凌晨两点会做一个）",
                            fontSize = 13.sp,
                            color = InkMuted,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                        )
                    }
                }
            } else {
                // 按 dream_date 分组，同一晚的梦排在一起
                val grouped = dreams.groupBy { it.dreamDate }
                grouped.forEach { (date, list) ->
                    item(key = "d_$date") { DayDivider(date) }
                    items(list, key = { it.id }) { dream ->
                        DreamCard(
                            dream = dream,
                            revealed = dream.recalled || dream.id in revealed,
                            onReveal = { revealed = revealed + dream.id },
                        )
                    }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

@Composable
private fun DreamHero(dreams: List<Dream>) {
    val total = dreams.size
    val remembered = dreams.count { it.recalled }
    val forgotten = total - remembered

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .padding(bottom = 22.dp)
            .aspectRatio(2.1f)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(listOf(HeroTop, HeroMid, HeroBottom))
            )
    ) {
        StarField()
        Moon(modifier = Modifier.align(Alignment.TopEnd).padding(end = 24.dp, top = 20.dp))

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 18.dp, end = 20.dp)
        ) {
            Text(
                "他睡着的时候",
                fontFamily = Serif,
                fontStyle = FontStyle.Italic,
                fontSize = 21.sp,
                color = InkBright,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "WHAT HE DREAMS WHEN YOU SLEEP",
                fontFamily = Serif,
                fontSize = 9.5.sp,
                letterSpacing = 2.2.sp,
                color = Color(0xFF7C8CA6),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                if (total == 0) "还没有梦"
                else "已经做过 $total 个梦 · 记得 $remembered 个" +
                    if (forgotten > 0) " · 忘了 $forgotten 个" else "",
                fontSize = 11.5.sp,
                color = InkSub,
            )
        }
    }
}

/** 固定种子的星点，每次重组位置不变，不会闪。 */
@Composable
private fun StarField() {
    val stars = remember {
        val rnd = Random(20260809)
        List(22) {
            Triple(rnd.nextFloat(), rnd.nextFloat(), 0.7f + rnd.nextFloat() * 1.1f)
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        stars.forEach { (fx, fy, r) ->
            drawCircle(
                color = Color(0xFF8FA6C8).copy(alpha = 0.35f + r * 0.3f),
                radius = r * density,
                center = Offset(fx * size.width, fy * size.height * 0.82f),
            )
        }
    }
}

@Composable
private fun Moon(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(34.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MoonColor, CircleShape)
        )
        // 用一块背景色的圆盖住右上角，切出弯月
        Box(
            Modifier
                .size(34.dp)
                .offset(x = 9.dp, y = (-4).dp)
                .background(Color(0xFF182337), CircleShape)
        )
    }
}

@Composable
private fun DayDivider(date: String) {
    val label = remember(date) { formatDayLabel(date) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .padding(top = 16.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontFamily = Serif,
            fontSize = 11.5.sp,
            letterSpacing = 1.sp,
            color = Color(0xFF63728C),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(LineColor))
    }
}

@Composable
private fun DreamCard(dream: Dream, revealed: Boolean, onReveal: () -> Unit) {
    val skin = remember(dream.type) { skinOf(dream.type) }
    val timeStr = remember(dream.createdAt) { formatClock(dream.createdAt) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .clickable(enabled = !revealed, onClick = onReveal)
    ) {
        // 左侧色条。LazyColumn item 的 maxHeight 是无限，fillMaxSize 在无限约束下测不出高度，
        // 所以外层用 matchParentSize 包一层，内层再 fillMaxHeight。
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .padding(vertical = 14.dp)
                    .background(
                        Brush.verticalGradient(listOf(skin.barStart, skin.barEnd)),
                        RoundedCornerShape(3.dp)
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 16.dp, top = 16.dp, bottom = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(9.dp), color = skin.tagBg) {
                    Text(
                        skin.label,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 10.sp,
                        color = skin.tagInk,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(timeStr, fontFamily = Serif, fontSize = 11.sp, color = InkFaint)
                if (!dream.recalled) {
                    Spacer(Modifier.weight(1f))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, CardBorder),
                    ) {
                        Text(
                            if (revealed) "想起来了" else "醒来忘了",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            fontSize = 9.5.sp,
                            color = InkDim,
                        )
                    }
                }
            }

            Spacer(Modifier.height(9.dp))

            // 忘了的梦打毛玻璃，点一下才看清（像回想的过程）。
            // blur 需要 Android 12+，低版本不生效但不会崩；alpha 兜底，保证低版本也有"模糊感"。
            Text(
                dream.content,
                fontSize = 14.5.sp,
                color = if (revealed) InkBody else InkBody.copy(alpha = 0.5f),
                lineHeight = 26.sp,
                modifier = if (revealed) Modifier else Modifier.blur(3.dp),
            )

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IntensityBars(dream.intensity, skin.barStart)
                Spacer(Modifier.width(10.dp))
                Text(
                    String.format(Locale.US, "%.2f", dream.intensity),
                    fontFamily = Serif,
                    fontSize = 10.sp,
                    color = InkDim,
                )
                if (dream.emotionTags.isNotEmpty()) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        dream.emotionTags.joinToString(" · "),
                        fontSize = 10.5.sp,
                        color = InkFaint,
                    )
                }
            }
        }
    }
}

@Composable
private fun IntensityBars(intensity: Double, onColor: Color) {
    val filled = (intensity * 5).toInt().coerceIn(0, 5)
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(5) { i ->
            Box(
                Modifier
                    .width(12.dp)
                    .height(3.dp)
                    .background(
                        if (i < filled) onColor else BarEmpty,
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

// ── 时间 ────────────────────────────────────────────────────

/** dream_date 是 yyyy-MM-dd。今天显示"今夜"，昨天显示"昨夜"。 */
private fun formatDayLabel(date: String): String = runCatching {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val d = fmt.parse(date) ?: return date
    val today = fmt.format(Date())
    val yesterday = fmt.format(Date(System.currentTimeMillis() - 86_400_000L))
    val pretty = SimpleDateFormat("M月d日", Locale.CHINA).format(d)
    when (date) {
        today -> "$pretty · 今夜"
        yesterday -> "$pretty · 昨夜"
        else -> pretty
    }
}.getOrDefault(date)

/**
 * Supabase 返回 UTC 时间戳（带小数秒，形如 2026-08-09T18:05:03.673911+00:00）。
 * "yyyy-MM-dd'T'HH:mm:ssXXX" 解析不了小数秒会抛异常，所以先剥掉小数秒再解析、按本地时区输出。
 */
private fun formatClock(raw: String): String = runCatching {
    val cleaned = raw.replace(Regex("""\.\d+"""), "")
    val d = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(cleaned) ?: return ""
    SimpleDateFormat("HH:mm", Locale.CHINA).apply {
        timeZone = TimeZone.getDefault()
    }.format(d)
}.getOrDefault("")
