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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.PlusSign
import me.rerere.rikkahub.data.db.entity.StickerEntity
import me.rerere.rikkahub.data.repository.StickerRepository
import org.koin.compose.koinInject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 共享表情包面板：人和 AI 用的是同一份库（同一张 sticker_item 表）。
 * 点某张图 = 插入到输入框（由调用方决定要不要跟文字一起发），不直接发送。
 * 长按可以删除；右上角"+"从相册选图进来，选完要求填名字+标签，AI 靠这些标签识别该发哪张。
 */
@Composable
fun StickerPicker(
    modifier: Modifier = Modifier,
    onStickerPicked: (UIMessagePart) -> Unit,
) {
    val context = LocalContext.current
    val repository: StickerRepository = koinInject()
    val stickers by repository.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingDeleteSticker by remember { mutableStateOf<StickerEntity?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            pendingUri = uri
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp)
            .padding(12.dp),
    ) {
        if (stickers.isEmpty()) {
            // 注意：这里必须用 weight(1f) 而不是 fillMaxSize()。
            // Column 按顺序测量子项，fillMaxSize() 会让这个空状态提示占满整个 320dp 高度，
            // 导致排在它后面的"+"添加按钮（Surface）被挤压到 0 高度、彻底不可见——
            // 这就是"没有表情包时看不到+号"的根因。weight(1f) 能让它和下面的按钮正确分享空间。
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
                            if (File(sticker.filePath).exists()) {
                                onStickerPicked(UIMessagePart.Image(url = "file://${sticker.filePath}"))
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
                .clickable { imagePicker.launch("image/*") },
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(HugeIcons.PlusSign, contentDescription = "添加表情包")
            }
        }
    }

    // 选完图，弹框要求填名字+标签
    val uri = pendingUri
    if (uri != null) {
        AddStickerDialog(
            onDismiss = { pendingUri = null },
            onConfirm = { name, tags ->
                scope.launch {
                    val filePath = saveStickerToPrivate(context, uri)
                    if (filePath.isNotBlank()) {
                        repository.add(
                            StickerEntity(
                                filePath = filePath,
                                name = name,
                                tags = tags,
                                addedBy = "sean",
                            )
                        )
                    }
                }
                pendingUri = null
            }
        )
    }

    // 长按删除确认
    val toDelete = pendingDeleteSticker
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteSticker = null },
            containerColor = androidx.compose.ui.graphics.Color.White,
            title = { Text("删除表情包") },
            text = { Text("删除\"${toDelete.name}\"？删除后双方都不能再用了。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repository.delete(toDelete)
                        runCatching { File(toDelete.filePath).delete() }
                    }
                    pendingDeleteSticker = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteSticker = null }) { Text("取消") }
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
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
    ) {
        AsyncImage(
            model = sticker.filePath,
            contentDescription = sticker.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
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
        containerColor = androidx.compose.ui.graphics.Color.White,
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

private fun saveStickerToPrivate(context: Context, uri: Uri): String {
    return try {
        val dir = File(context.filesDir, "stickers").apply { mkdirs() }
        val outFile = File(dir, "sticker_${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
        }
        outFile.absolutePath
    } catch (e: Exception) {
        ""
    }
}
