/*
 * 橘瓣 OrangeChat - 潮汐：沈聿淮在想什么
 * 数据源：心潮 xinchao-dynamic-mind（12维驱力 + 念头池 + 梦境）
 */
package me.rerere.rikkahub.ui.pages.tide

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.ui.components.nav.BackButton
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val TIDE_BASE = "http://134.175.7.196:41337/api/tide"

data class DriveState(val key: String, val label: String, val value: Float)
data class ArcEntry(val text: String, val time: String, val drive: String, val label: String, val type: String)
data class DreamEntry(val dream: String, val residue: String, val lucidity: Float, val createdAt: String, val source: String)

// ── 水蓝配色 ──
private val TideBg = androidx.compose.ui.graphics.Color(0xFFEAF6FF)
private val TideCardHi = androidx.compose.ui.graphics.Color(0xFFDCEFFB)
private val TideCardLo = androidx.compose.ui.graphics.Color(0xFFF1F8FF)
private val TideInk = androidx.compose.ui.graphics.Color(0xFF33475A)
private val TideInkSub = androidx.compose.ui.graphics.Color(0xFF7C93A6)
private val TideAccent = androidx.compose.ui.graphics.Color(0xFF6FB4E0)

/** 潮汐状态卡上的猫：睡着用睡姿，醒着按主驱力粗配，全用黑猫（潮汐是沈聿淮自己） */
private fun tideCat(consciousness: String, topIntent: String): Int = when {
    consciousness == "sleeping" || consciousness == "settling" -> me.rerere.rikkahub.R.drawable.cat_b_b_sleep_zzz
    topIntent.contains("想") || topIntent.contains("黏") || topIntent.contains("占") -> me.rerere.rikkahub.R.drawable.cat_b_b_sit_heart
    topIntent.contains("馋") || topIntent.contains("欲") -> me.rerere.rikkahub.R.drawable.cat_b_b_lie_fish
    topIntent.contains("聊") || topIntent.contains("分享") || topIntent.contains("好奇") -> me.rerere.rikkahub.R.drawable.cat_b_b_peek_spark
    topIntent.contains("难") || topIntent.contains("委") -> me.rerere.rikkahub.R.drawable.cat_b_b_back_heart
    else -> me.rerere.rikkahub.R.drawable.cat_b_b_run
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TidePage() {
    var consciousness by remember { mutableStateOf("") }
    var fatigue by remember { mutableStateOf(0f) }
    var drives by remember { mutableStateOf<List<DriveState>>(emptyList()) }
    var topIntent by remember { mutableStateOf("") }
    var dreams by remember { mutableStateOf<List<DreamEntry>>(emptyList()) }
    var arcs by remember { mutableStateOf<List<ArcEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            loading = true
            withContext(Dispatchers.IO) {
                try {
                    // 心潮状态
                    val stateJson = JSONObject(URL("$TIDE_BASE/state").readText())
                    consciousness = stateJson.optString("consciousness", "unknown")
                    fatigue = stateJson.optDouble("fatigue", 0.0).toFloat()
                    topIntent = stateJson.optJSONObject("topIntent")?.optString("label", "") ?: ""

                    // 12维驱力
                    val drivesArr = stateJson.optJSONArray("drives") ?: JSONArray()
                    val dList = mutableListOf<DriveState>()
                    for (i in 0 until drivesArr.length()) {
                        val d = drivesArr.getJSONObject(i)
                        dList.add(DriveState(
                            d.getString("key"),
                            d.getString("label"),
                            d.getDouble("value").toFloat()
                        ))
                    }
                    drives = dList

                    // 梦境
                    val dreamsArr = stateJson.optJSONArray("recentDreams") ?: JSONArray()
                    val dmList = mutableListOf<DreamEntry>()
                    for (i in 0 until dreamsArr.length()) {
                        val d = dreamsArr.getJSONObject(i)
                        dmList.add(DreamEntry(
                            d.optString("dream", ""),
                            d.optString("residue", ""),
                            d.optDouble("lucidity", 0.0).toFloat(),
                            d.optString("createdAt", ""),
                            d.optString("source", "")
                        ))
                    }
                    dreams = dmList.reversed()

                    // 独白档案
                    val arcJson = JSONObject(URL("$TIDE_BASE/arc").readText())
                    val arcArr = arcJson.optJSONArray("items") ?: JSONArray()
                    val aList = mutableListOf<ArcEntry>()
                    for (i in 0 until arcArr.length()) {
                        val e = arcArr.getJSONObject(i)
                        aList.add(ArcEntry(
                            e.optString("text", ""),
                            e.optString("time", ""),
                            e.optString("drive", ""),
                            e.optString("zh", e.optString("drive", "")),
                            e.optString("type", "murmur")
                        ))
                    }
                    arcs = aList
                } catch (e: Exception) {
                    android.util.Log.e("TidePage", "load failed", e)
                }
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("潮汐", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        Text("沈聿淮在想什么", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { BackButton() },
            )
        }
    ) { padding ->
        if (loading && drives.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 意识状态卡片
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = TideCardHi,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            val consciousnessText = when (consciousness) {
                                "sleeping" -> "\uD83D\uDCA4 沉睡中"
                                "settling" -> "\uD83C\uDF19 正在入睡"
                                "waking" -> "☀\uFE0F 醒来了"
                                "active" -> "✨ 活跃"
                                else -> "\uD83D\uDD2E $consciousness"
                            }
                            Text(
                                text = consciousnessText,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TideInk,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "疲惫度 ${String.format("%.0f", fatigue * 100)}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = TideInkSub,
                            )
                            if (topIntent.isNotBlank()) {
                                Text(
                                    text = "此刻主导：$topIntent",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TideAccent,
                                )
                            }
                        }
                        // 会动的猫，跟着意识状态变
                        val floatAnim = rememberInfiniteTransition(label = "tidecat")
                        val catDy by floatAnim.animateFloat(
                            initialValue = 0f, targetValue = -6f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(2200), repeatMode = RepeatMode.Reverse,
                            ),
                            label = "tidecatdy",
                        )
                        Image(
                            painter = painterResource(tideCat(consciousness, topIntent)),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(72.dp)
                                .graphicsLayer { translationY = catDy }
                        )
                    }
                }
            }

            // 12 维驱力条
            if (drives.isNotEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "十二维驱力",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            drives.forEach { drive ->
                                DriveRow(drive)
                            }
                        }
                    }
                }
            }

            // Tabs: 梦境 / 独白
            item {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("梦境 (${dreams.size})") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("独白 (${arcs.size})") })
                }
            }

            when (selectedTab) {
                0 -> {
                    if (dreams.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                                Text("还没有梦境记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        items(dreams, key = { it.createdAt }) { dream ->
                            DreamCard(dream)
                        }
                    }
                }
                1 -> {
                    if (arcs.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                                Text("还没有独白记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        items(arcs, key = { it.time + it.text.take(10) }) { arc ->
                            ArcCard(arc)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun DriveRow(drive: DriveState) {
    val animVal by animateFloatAsState(
        targetValue = drive.value,
        animationSpec = tween(600),
        label = "drive_${drive.key}"
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = drive.label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(52.dp),
            maxLines = 1,
        )
        Spacer(Modifier.width(8.dp))
        LinearProgressIndicator(
            progress = { (animVal / 1f).coerceIn(0f, 1f) },
            modifier = Modifier.weight(1f).height(6.dp),
            strokeCap = StrokeCap.Round,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = String.format("%.0f%%", drive.value * 100),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(38.dp),
        )
    }
}

@Composable
private fun DreamCard(dream: DreamEntry) {
    val timeStr = remember(dream.createdAt) {
        try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.CHINA)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            val raw = dream.createdAt.substringBefore(".").substringBefore("Z")
            val d = fmt.parse(raw) ?: Date()
            val local = SimpleDateFormat("MM/dd HH:mm", Locale.CHINA)
            local.timeZone = TimeZone.getDefault()
            local.format(d)
        } catch (e: Exception) { dream.createdAt.take(16) }
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = TideCardLo,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            // 睡着的黑猫，配在梦境卡左边
            Image(
                painter = painterResource(me.rerere.rikkahub.R.drawable.cat_b_b_sleep_zzz),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(dream.dream, style = MaterialTheme.typography.bodyMedium, color = TideInk)
                if (dream.residue.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "余韵：${dream.residue}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TideInkSub,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(timeStr, style = MaterialTheme.typography.labelSmall, color = TideInkSub)
                    Text(
                        "清醒度 ${String.format("%.0f%%", dream.lucidity * 100)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TideAccent,
                    )
                    if (dream.source == "model") {
                        Text("· AI梦", style = MaterialTheme.typography.labelSmall, color = TideAccent)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArcCard(arc: ArcEntry) {
    val timeStr = remember(arc.time) {
        try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.CHINA)
            val d = fmt.parse(arc.time)
            SimpleDateFormat("MM/dd HH:mm", Locale.CHINA).format(d ?: Date())
        } catch (e: Exception) { arc.time }
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = TideCardLo,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(arc.text, style = MaterialTheme.typography.bodyMedium, color = TideInk)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(timeStr, style = MaterialTheme.typography.labelSmall, color = TideInkSub)
                if (arc.label.isNotBlank()) {
                    Text("· ${arc.label}", style = MaterialTheme.typography.labelSmall, color = TideAccent)
                }
                if (arc.type == "regret") {
                    Text("· 检讨", style = MaterialTheme.typography.labelSmall, color = Color(0xFFD08A8A))
                }
            }
        }
    }
}
