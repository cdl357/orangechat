/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.ui.components.ai

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.PlusSign
import me.rerere.rikkahub.data.db.entity.StickerEntity
import me.rerere.rikkahub.data.repository.StickerRepository
import org.koin.compose.koinInject
import java.io.File

/**
 * 进程级 IO 作用域。不用 rememberCoroutineScope()——面板收起协程立刻取消。
 */
private val stickerIoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * 共享表情包面板：人和 AI 用的是同一份库（同一张 sticker_item 表）。
 * 点某张图 = 插入到输入框，不直接发送。
 * 长按可以删除；右上角"+"从相册选图，上传到 Supabase Storage 云端存储。
 *
 * 图片存储在云端，换手机/重装/清数据都不会丢。
 */
@Composable
fun StickerPicker(
    modifier: Modifier = Modifier,
    onStickerPicked: (UIMessagePart) -> Unit,
    onAddDialogVisibleChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val repository: StickerRepository = koinInject()
    val stickers by repository.observeAllValid().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    // 启动时自动迁移本地表情包到云端（只跑一次）
    LaunchedEffect(Unit) {
        stickerIoScope.launch {
            runCatching { repository.migrateLocalToCloud() }
        }
    }

    // 等着填名字标签的 Uri
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingDeleteSticker by remember { mutableStateOf<StickerEntity?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        pendingUri = uri
        onAddDialogVisibleChange(true)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp)
            .padding(12.dp),
    ) {
        if (stickers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "还没有表情包，点下面 + 号添加",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            var cleaning by remember { mutableStateOf(false) }
            var cleanedTip by remember { mutableStateOf<String?>(null) }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !cleaning) {
                        cleaning = true
                        stickerIoScope.launch {
                            val n = runCatching { repository.cleanBroken() }.getOrDefault(0)
                            withContext(Dispatchers.Main) {
                                cleaning = false
                                cleanedTip = if (n > 0) "清掉了 $n 条丢失的记录" else "没有需要清理的"
                            }
                        }
                    },
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        cleanedTip ?: if (cleaning) "正在清理…" else "图片丢了的表情包？点这里清掉记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 72.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(stickers, key = { it.id }) { sticker ->
                    StickerGridItem(
                        sticker = sticker,
                        onClick = {
                            // 优先用远程 URL
                            val url = sticker.remoteUrl.ifBlank { null }
                                ?: "file://${sticker.filePath}".takeIf { File(sticker.filePath).exists() }
                            if (url != null) {
                                onStickerPicked(UIMessagePart.Image(url = url))
                            } else {
                                errorText = "「${sticker.name}」的图片已丢失，发不出去。长按可以删掉它。"
                            }
                        },
                        onLongClick = { pendingDeleteSticker = sticker },
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !uploading) { imagePicker.launch("image/*") },
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (uploading) {
                    Text("正在上传…", style = MaterialTheme.typography.bodySmall)
                } else {
                    Icon(HugeIcons.PlusSign, contentDescription = "添加表情包")
                }
            }
        }
    }

    // 选完图，填名字+标签
    val uri = pendingUri
    if (uri != null) {
        AddStickerDialog(
            onDismiss = {
                pendingUri = null
                onAddDialogVisibleChange(false)
            },
            onConfirm = { name, tags ->
                pendingUri = null
                onAddDialogVisibleChange(false)
                uploading = true
                stickerIoScope.launch {
                    val result = repository.addStickerFromUri(
                        uri = uri,
                        name = name,
                        tags = tags,
                        addedBy = "yuri",
                    )
                    withContext(Dispatchers.Main) {
                        uploading = false
                        if (result == null) {
                            errorText = "上传失败，请检查网络后重试。"
                        } else if (result.remoteUrl.isBlank()) {
                            errorText = "图片已保存到本地，但上传云端失败。下次打开会自动重试上传。"
                        }
                    }
                }
            }
        )
    }

    // 长按删除确认
    val toDelete = pendingDeleteSticker
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteSticker = null },
            containerColor = Color.White,
            title = { Text("删除表情包") },
            text = { Text("删除\"${toDelete.name}\"？删除后双方都不能再用了。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteSticker = null
                    stickerIoScope.launch {
                        repository.deleteSticker(toDelete)
                    }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSticker = null }) { Text("取消") }
            }
        )
    }

    // 出错提示
    val err = errorText
    if (err != null) {
        AlertDialog(
            onDismissRequest = { errorText = null },
            containerColor = Color.White,
            title = { Text("表情包") },
            text = { Text(err) },
            confirmButton = {
                TextButton(onClick = { errorText = null }) { Text("知道了") }
            }
        )
    }
}

@Composable
private fun StickerGridItem(
    sticker: StickerEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    // 有远程 URL 的永远不算坏
    val hasRemote = sticker.remoteUrl.isNotBlank()
    val broken = remember(sticker.filePath, sticker.remoteUrl) {
        if (hasRemote) false
        else {
            val f = File(sticker.filePath)
            !f.exists() || f.length() == 0L
        }
    }

    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!broken) {
                // 优先用远程 URL 加载（网络图），否则用本地文件
                val model: Any = if (hasRemote) sticker.remoteUrl else File(sticker.filePath)
                AsyncImage(
                    model = model,
                    contentDescription = sticker.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        sticker.name.take(4),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(16.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "!",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddStickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, tags: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        containerColor = Color.White,
        title = { Text("添加表情包") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "起个名字、加几个标签，我才知道什么场合该发这张",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名字，如 委屈") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("标签，逗号分隔，如 委屈,难过,撒娇") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), tags.trim()) },
                enabled = name.isNotBlank(),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
