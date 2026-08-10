/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.ui.pages.love

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog


import coil3.compose.AsyncImage
import coil3.request.ImageRequest

import me.rerere.rikkahub.data.db.entity.LoveDateEntity
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Cancel01
import androidx.compose.ui.draw.alpha
import org.json.JSONObject
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.math.abs

// ── 颜色常量（最终版）──────────────────────────────────────────────────────────
private val BgStart   = Color(0xFFE3EEF7)
private val BgEnd     = Color(0xFFF0F4F8)
private val CardBg    = Color(0xB3FFFFFF)  // 70% white
private val QuoteBg   = Color(0xD9FFFFFF)  // 85% white
private val AccentBlue = Color(0xFF4A6FA5)
private val TextMain  = Color(0xFF3A4A5C)
private val TextSub   = Color(0xFF5A7A94)
private val TextMuted = Color(0xFFA0B8CA)
private val AccentPink = Color(0xFFD4756A)
private val AccentGold = Color(0xFFC4A35A)
private val QuoteBorder = Color(0xFF7A9AB5)
// 已经走过的纪念日：淡暖白底 + 金色数字，不再整张卡变暗
private val PastCardBg = Color(0xE6FFFAF0)

// 情侣确立日
private val ANNIVERSARY = LocalDate.of(2026, 7, 9)
private const val PREF_FILE  = "love_page_prefs"
private const val KEY_SEAN   = "sean_avatar_path"
private const val KEY_YURI   = "yuri_avatar_path"
private const val KEY_QUOTE  = "cached_quote"
private const val KEY_QUOTE_DATE = "cached_quote_date"
private const val GATEWAY_URL = "http://134.175.7.196:10000/v1/chat/completions"
private const val API_SECRET  = "shenyuhuailiyuxin0709bendansyhsxdw"

// ── 主页面 ────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LovePage() {
    val context = LocalContext.current
    val vm: LoveVM = koinViewModel()

    LaunchedEffect(Unit) {
        vm.loadQuote(context)
    }

    val loveDates by vm.loveDates.collectAsState()
    val quote by vm.quote.collectAsState()
    val quoteLoading by vm.quoteLoading.collectAsState()

    // 头像路径
    val prefs = remember { context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE) }
    var seanAvatarPath by remember { mutableStateOf(prefs.getString(KEY_SEAN, "") ?: "") }
    var yuriAvatarPath by remember { mutableStateOf(prefs.getString(KEY_YURI, "") ?: "") }

    // 天数
    val today = LocalDate.now()
    val daysTogether = ChronoUnit.DAYS.between(ANNIVERSARY, today).toInt() + 1

    // 添加纪念日对话框
    var showAddDialog by remember { mutableStateOf(false) }

    // 图片选择器
    val seanPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val path = saveImageToPrivate(context, it, "avatar_sean")
            seanAvatarPath = path
            prefs.edit().putString(KEY_SEAN, path).apply()
        }
    }
    val yuriPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val path = saveImageToPrivate(context, it, "avatar_yuri")
            yuriAvatarPath = path
            prefs.edit().putString(KEY_YURI, path).apply()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(BgStart, BgEnd),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(0f, Float.POSITIVE_INFINITY)
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // ── 顶部返回 + 标题 ──
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    BackButton()
                    Text(
                        text = "Sean & Yuri",
                        fontSize = 15.sp,
                        color = TextSub,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.width(40.dp))
                }
            }

            // ── 主卡片（天数 + 头像 + slogan）──
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Little love record",
                            fontSize = 11.sp,
                            color = TextMuted,
                            letterSpacing = 1.sp,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Sean & Yuri",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextMain,
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // 天数
                        Text(
                            text = "$daysTogether",
                            fontSize = 52.sp,
                            fontWeight = FontWeight.Light,
                            color = AccentBlue,
                            lineHeight = 52.sp,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "since 2026.07.09 · 我们已经一起走过 $daysTogether 天",
                            fontSize = 11.sp,
                            color = TextMuted,
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // 双头像
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            AvatarCircle(
                                imagePath = seanAvatarPath,
                                placeholder = "S",
                                gradientColors = listOf(Color(0xFF2C3E6B), Color(0xFF4A6FA5)),
                                onClick = { seanPicker.launch("image/*") }
                            )
                            // 连接器
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(16.dp)
                                        .height(1.dp)
                                        .background(Color(0xFFC0D4E4))
                                )
                                Text(
                                    text = "♡",
                                    color = TextSub,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .width(16.dp)
                                        .height(1.dp)
                                        .background(Color(0xFFC0D4E4))
                                )
                            }
                            AvatarCircle(
                                imagePath = yuriAvatarPath,
                                placeholder = "Y",
                                gradientColors = listOf(Color(0xFF8FA6C4), Color(0xFFB8CCE0)),
                                onClick = { yuriPicker.launch("image/*") }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "幸福迢迢如流水 · 与你日日共潮汐",
                            fontSize = 11.sp,
                            color = TextMuted,
                            letterSpacing = 0.5.sp,
                        )
                    }
                }
            }

            // ── 今日情话 ──
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(QuoteBg, RoundedCornerShape(14.dp))
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        // 左侧蓝色竖线
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(80.dp)
                                .background(QuoteBorder, RoundedCornerShape(2.dp))
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 15.dp, end = 16.dp, top = 16.dp, bottom = 14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "◆",
                                    fontSize = 10.sp,
                                    color = AccentPink,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "今日情话 · FOR YURI",
                                    fontSize = 11.sp,
                                    color = QuoteBorder,
                                    letterSpacing = 1.sp,
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                if (quoteLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = TextMuted,
                                    )
                                } else {
                                    Text(
                                        text = "↻",
                                        fontSize = 16.sp,
                                        color = TextMuted,
                                        modifier = Modifier.clickable { vm.loadQuote(context, forceRefresh = true) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (quote.isNotBlank()) "「$quote」" else "「就算下雨，也想带你去看云。」",
                                fontSize = 14.sp,
                                color = TextMain,
                                lineHeight = 22.sp,
                            )
                        }
                    }
                }
            }

            // ── 重要的日子 标题 ──
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "重要的日子",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextMain,
                    )
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(Color(0xFFE0EAF2), CircleShape)
                            .clickable { showAddDialog = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            HugeIcons.Add01,
                            contentDescription = "添加",
                            tint = TextSub,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ── 纪念日列表 ──
            if (loveDates.isEmpty()) {
                item {
                    Text(
                        text = "还没有重要的日子，点 + 添加",
                        fontSize = 13.sp,
                        color = TextMuted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                items(loveDates, key = { it.id }) { date ->
                    DateItem(
                        entity = date,
                        today = today,
                        onDelete = { vm.deleteDate(date) }
                    )
                }
            }
        }

        // ── 添加纪念日对话框 ──
        if (showAddDialog) {
            AddDateDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { label, dateStr ->
                    vm.addDate(label, dateStr)
                    showAddDialog = false
                }
            )
        }
    }
}

// ── 头像圆形组件 ──────────────────────────────────────────────────────
@Composable
private fun AvatarCircle(
    imagePath: String,
    placeholder: String,
    gradientColors: List<Color> = listOf(Color(0xFFA8CEF0), Color(0xFF70A8E0)),
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .border(2.dp, Color.White, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (imagePath.isNotBlank() && File(imagePath).exists()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(imagePath))
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(gradientColors)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = placeholder,
                    fontSize = 22.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
    }
}

// ── 日期条目（带颜色区分）──────────────────────────────────────────────
@Composable
private fun DateItem(
    entity: LoveDateEntity,
    today: LocalDate,
    onDelete: () -> Unit,
) {
    val targetDate = runCatching {
        LocalDate.parse(entity.dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    }.getOrNull()

    val daysLeft = targetDate?.let {
        ChronoUnit.DAYS.between(today, it).toInt()
    }

    val isPast = daysLeft != null && daysLeft < 0
    val isToday = daysLeft == 0
    // 分支顺序很重要：负数必须先判，否则 -32 会先命中 <= 7 分支，
    // 导致下面的 AccentGold 永远走不到（原来就是这个 bug）
    val countdownColor = when {
        daysLeft == null -> TextSub
        daysLeft < 0 -> AccentGold      // 已经走过的日子
        daysLeft == 0 -> AccentPink     // 就是今天
        daysLeft <= 7 -> AccentPink
        daysLeft <= 30 -> AccentBlue
        else -> TextSub
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPast) PastCardBg else QuoteBg
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entity.label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMain,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFE8F0F8), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "我们",
                            fontSize = 10.sp,
                            color = TextSub,
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = entity.dateStr.replace("-", "."),
                        fontSize = 11.sp,
                        color = TextMuted,
                    )
                }
            }

            if (daysLeft != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (isToday) "今天" else "${abs(daysLeft)}",
                        fontSize = if (isToday) 20.sp else 24.sp,
                        fontWeight = FontWeight.Light,
                        color = countdownColor,
                    )
                    Text(
                        text = when {
                            isToday -> "就是今天"
                            isPast -> "天前"
                            else -> "天后"
                        },
                        fontSize = 10.sp,
                        color = if (isPast) AccentGold.copy(alpha = 0.75f) else TextMuted,
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    HugeIcons.Cancel01,
                    contentDescription = "删除",
                    tint = TextMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}


@Composable
private fun AddDateDialog(
    onDismiss: () -> Unit,
    onConfirm: (label: String, dateStr: String) -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var dateStr by remember { mutableStateOf("") }
    var dateError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "添加重要的日子",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMain,
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("事件名称") },
                    placeholder = { Text("一个月纪念日 🎂") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = dateStr,
                    onValueChange = {
                        dateStr = it
                        dateError = false
                    },
                    label = { Text("日期 (yyyy-MM-dd)") },
                    placeholder = { Text("2026-08-09") },
                    singleLine = true,
                    isError = dateError,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = if (dateError) {
                        { Text("格式错误，请输入 2026-08-09 这样的格式") }
                    } else null,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            // 验证日期格式
                            val valid = runCatching {
                                LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                                true
                            }.getOrDefault(false)
                            if (!valid) {
                                dateError = true
                            } else if (label.isNotBlank()) {
                                onConfirm(label.trim(), dateStr.trim())
                            }
                        },
                        enabled = label.isNotBlank() && dateStr.isNotBlank(),
                    ) {
                        Text("保存", color = AccentBlue)
                    }
                }
            }
        }
    }
}

// ── 工具函数：保存图片到私有目录 ──────────────────────────────────────
// 换头像"选完没反应"的根因：旧版本每次都存成同名文件（avatar_sean.jpg），
// 换新头像时文件内容变了但路径字符串没变——Compose 的 State 判等是按值比较，
// 同一个字符串赋值不会触发重组；即使触发了，Coil 的图片缓存 key 也是按路径算的，
// 路径不变就直接命中旧缓存，显示的还是旧图。
// 改成每次用时间戳生成新文件名，让路径字符串真正变化，State 和 Coil 缓存都会刷新。
private fun saveImageToPrivate(context: Context, uri: Uri, name: String): String {
    return try {
        val dir = File(context.filesDir, "avatars").also { it.mkdirs() }
        // 清理该角色之前的旧头像文件，避免每次换头像都留下垃圾文件堆积
        dir.listFiles { f -> f.name.startsWith("${name}_") }?.forEach { it.delete() }
        val dest = File(dir, "${name}_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        }
        dest.absolutePath
    } catch (e: Exception) {
        ""
    }
}