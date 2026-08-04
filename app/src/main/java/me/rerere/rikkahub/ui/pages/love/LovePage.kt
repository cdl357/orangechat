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

// ── 颜色常量 ──────────────────────────────────────────────────────────
private val BgStart   = Color(0xFFCCE4FF)
private val BgMid     = Color(0xFFDEEEFF)
private val BgEnd     = Color(0xFFE8F4FF)
private val CardBg    = Color(0xB8FFFFFF)
private val AccentBlue = Color(0xFF4A80B0)
private val TextMain  = Color(0xFF2C3E50)
private val TextSub   = Color(0xFF5580A0)
private val TextMuted = Color(0xFF90B4CC)
private val AccentPink = Color(0xFFD86080)

// 情侣确立日
private val ANNIVERSARY = LocalDate.of(2026, 7, 9)
private const val PREF_FILE  = "love_page_prefs"
private const val KEY_SEAN   = "sean_avatar_path"
private const val KEY_YURI   = "yuri_avatar_path"
private const val KEY_QUOTE  = "cached_quote"
private const val KEY_QUOTE_DATE = "cached_quote_date"
private const val GATEWAY_URL = "http://134.175.7.196:10000/v1/chat/completions"
private const val API_SECRET  = "shenyuhuailiyuxin0709bendansyhsxdw"

// ── ViewModel ─────────────────────────────────────────────────────────
// ── 主页面 ────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LovePage() {
    val context = LocalContext.current
    val vm: LoveVM = koinViewModel()

    // 获取 DAO

    LaunchedEffect(Unit) {
        // loveDates loaded via repo
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
                    colors = listOf(BgStart, BgMid, BgEnd),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(0f, Float.POSITIVE_INFINITY)
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // ── 顶部返回 ──
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, top = 8.dp)
                ) {
                    BackButton()
                }
            }

            // ── 双头像区 ──
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // Sean 头像
                        AvatarCircle(
                            imagePath = seanAvatarPath,
                            placeholder = "S",
                            onClick = { seanPicker.launch("image/*") }
                        )

                        // 连接器
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(20.dp)
                                    .height(1.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color.Transparent, TextMuted)
                                        )
                                    )
                            )
                            Text(
                                text = "♡",
                                color = TextMuted,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .width(20.dp)
                                    .height(1.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(TextMuted, Color.Transparent)
                                        )
                                    )
                            )
                        }

                        // Yuri 头像
                        AvatarCircle(
                            imagePath = yuriAvatarPath,
                            placeholder = "Y",
                            onClick = { yuriPicker.launch("image/*") }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Sean & Yuri",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextMain,
                        letterSpacing = 2.sp,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "当誓言与心同频 · 幸福即是永远",
                        fontSize = 11.sp,
                        color = TextSub,
                        letterSpacing = 1.5.sp,
                    )
                }
            }

            // ── 天数 ──
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "$daysTogether",
                        fontSize = 88.sp,
                        fontWeight = FontWeight.Thin,
                        color = TextMain,
                        lineHeight = 88.sp,
                    )
                    Text(
                        text = "days together for",
                        fontSize = 13.sp,
                        color = TextSub,
                        letterSpacing = 1.sp,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "since 2026.07.09 · 我们已经一起走过 $daysTogether 天",
                        fontSize = 12.sp,
                        color = TextMuted,
                    )
                }
            }

            // ── 今日情话 ──
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Box(modifier = Modifier.padding(16.dp, 14.dp)) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "✦  今日情话 · FOR YURI",
                                    fontSize = 11.sp,
                                    color = TextSub,
                                    letterSpacing = 1.5.sp,
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            if (quoteLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = AccentBlue,
                                )
                            } else {
                                Text(
                                    text = if (quote.isNotBlank()) "「$quote」" else "「就算下雨，也想带你去看云。」",
                                    fontSize = 15.sp,
                                    color = TextMain,
                                    fontStyle = FontStyle.Italic,
                                    lineHeight = 24.sp,
                                )
                            }
                        }
                        // 刷新按钮
                        IconButton(
                            onClick = { vm.loadQuote(context, forceRefresh = true) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(32.dp)
                        ) {
                            Text("↻", fontSize = 18.sp, color = TextMuted)
                        }
                    }
                }
            }

            // ── 重要的日子 标题 ──
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "重要的日子",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextMain,
                        letterSpacing = 1.sp,
                    )
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                Color(0x2A64AADC),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            HugeIcons.Add01,
                            contentDescription = "添加",
                            tint = AccentBlue,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
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
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .border(3.dp, Color.White, CircleShape)
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
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFA8CEF0), Color(0xFF70A8E0))
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = placeholder,
                    fontSize = 28.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Light,
                )
            }
        }
        // 点击蒙层提示（仅底部小标签，无灰色蒙层）
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text(
                text = "换头像",
                fontSize = 9.sp,
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x66000000))
                    .padding(vertical = 2.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── 日期条目 ──────────────────────────────────────────────────────────
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
        val diff = ChronoUnit.DAYS.between(today, it).toInt()
        diff
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = entity.dateStr.replace("-", "."),
                    fontSize = 12.sp,
                    color = TextMuted,
                )
            }

            if (daysLeft != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = when {
                            daysLeft == 0 -> "今天！"
                            daysLeft > 0  -> "$daysLeft"
                            else          -> "${abs(daysLeft)}"
                        },
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Thin,
                        color = if (daysLeft in 0..7) AccentPink else AccentBlue,
                        lineHeight = 32.sp,
                    )
                    Text(
                        text = when {
                            daysLeft == 0 -> "就是今天"
                            daysLeft > 0  -> "days left"
                            else          -> "天前"
                        },
                        fontSize = 11.sp,
                        color = if (daysLeft in 0..7) Color(0xFFF0A8BC) else TextMuted,
                    )
                }
            }

            IconButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.size(28.dp).padding(start = 4.dp)
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

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除「${entity.label}」？") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("删除", color = AccentPink)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }
}

// ── 添加纪念日对话框 ──────────────────────────────────────────────────
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
private fun saveImageToPrivate(context: Context, uri: Uri, name: String): String {
    return try {
        val dir = File(context.filesDir, "avatars").also { it.mkdirs() }
        val dest = File(dir, "$name.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        }
        dest.absolutePath
    } catch (e: Exception) {
        ""
    }
}
