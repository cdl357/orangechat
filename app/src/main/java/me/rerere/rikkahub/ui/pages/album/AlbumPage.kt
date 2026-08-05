/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.ui.pages.album

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Image02
import me.rerere.rikkahub.data.db.entity.AlbumEntity
import me.rerere.rikkahub.ui.components.nav.BackButton
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── 拍立得配色 ──────────────────────────────────────────────
private val AlbumBgTop = Color(0xFFF5FBFD)
private val AlbumBgBottom = Color(0xFFE8F4F8)
private val AccentBlue = Color(0xFF6A9AAA)
private val TextMain = Color(0xFF4A6A7A)
private val TextSub = Color(0xFF8AB4C4)
private val TagBg = Color(0xFFD8EEF4)
private val TagBgActive = Color(0xFF7AB4C4)
private val TimelineDot = Color(0xFF7AB4C4)
private val TimelineLine = Color(0xFFC8E4EC)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumPage(vm: AlbumVM = koinViewModel()) {
    val items by vm.allItems.collectAsStateWithLifecycle()

    // 按 yyyy年M月 分组
    val grouped = remember(items) {
        items.groupBy { item ->
            SimpleDateFormat("yyyy年 M月", Locale.CHINA).format(Date(item.createdAt))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("相册")
                        Text("${items.size} 张", fontSize = 12.sp, color = TextSub)
                    }
                },
                navigationIcon = { BackButton() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AlbumBgTop),
            )
        },
        containerColor = AlbumBgTop,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { },
                containerColor = Color(0xFF6AB4C8),
                contentColor = Color.White,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            ) {
                Icon(HugeIcons.Image02, contentDescription = "相册")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Brush.verticalGradient(listOf(AlbumBgTop, AlbumBgBottom)))
        ) {
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有图片，点 + 从相册导入，或由 Sean 保存对话截图", color = TextSub, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 100.dp),
                ) {
                    // 分类标签行（静态展示，仅视觉）
                    item {
                        Row(
                            modifier = Modifier.padding(bottom = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AlbumTag("全部", true)
                            AlbumTag("Sean 保存的", false)
                            AlbumTag("Yuri 保存的", false)
                        }
                    }
                    grouped.forEach { (dateLabel, photos) ->
                        item {
                            Row(
                                modifier = Modifier.padding(bottom = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(10.dp)
                                        .height(10.dp)
                                        .background(TimelineDot, CircleShape)
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(dateLabel, fontSize = 13.sp, color = TextMain, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                            }
                        }
                        item {
                            FlowRow(
                                modifier = Modifier.padding(start = 36.dp, bottom = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                photos.forEach { photo ->
                                    PolaroidCard(photo)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumTag(label: String, active: Boolean) {
    Surface(
        color = if (active) TagBgActive else TagBg,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = if (active) Color.White else AccentBlue,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun PolaroidCard(photo: AlbumEntity) {
    val rotation = remember(photo.id) { if (photo.id % 2 == 0) 2f else -2f }
    val dateStr = remember(photo.createdAt) {
        SimpleDateFormat("M/d", Locale.CHINA).format(Date(photo.createdAt))
    }

    Column(
        modifier = Modifier
            .width(150.dp)
            .rotate(rotation)
            .background(Color.White)
            .padding(8.dp, 8.dp, 8.dp, 12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFE8F4F8), Color(0xFFD8EEF4)))),
            contentAlignment = Alignment.Center,
        ) {
            val file = remember(photo.filePath) { File(photo.filePath) }
            if (photo.filePath.isNotBlank() && file.exists()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(file).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text("🖼️", fontSize = 32.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (photo.caption.isNotBlank()) {
            Text(
                photo.caption,
                fontSize = 10.sp,
                color = AccentBlue,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
            )
        }
        Text(
            dateStr,
            fontSize = 9.sp,
            color = Color(0xFFA0C4D0),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
