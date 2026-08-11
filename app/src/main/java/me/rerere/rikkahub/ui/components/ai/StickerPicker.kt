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
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 复制表情包文件用的协程作用域。
 *
 * 不能用 rememberCoroutineScope()：那个作用域跟 Composable 绑定，面板一收起来（离开组合）
 * 协程立刻被取消。之前的表现就是——填完名字点保存、面板收回去，文件只写了一半或者零字节，
 * 于是列表里那张图是空白的，点了也发不出去。所以这里用一个进程级的 IO 作用域。
 */
private val stickerIoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * 共享表情包面板：人和 AI 用的是同一份库（同一张 sticker_item 表）。
 * 点某张图 = 插入到输入框（由调用方决定要不要跟文字一起发），不直接发送。
 * 长按可以删除；右上角"+"从相册选图进来，选完要求填名字+标签，AI 靠这些标签识别该发哪张。
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

    // 已经复制好、等着填名字标签的文件路径（不是 Uri）
    var pendingPath by remember { mutableStateOf<String?>(null) }
    var pendingDeleteSticker by remember { mutableStateOf<StickerEntity?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var copying by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // 选中之后立刻复制。相册给的读权限只在当前这一段时间有效，
        // 拖到"填完名字点保存"之后再复制，权限可能已经失效（云相册尤其容易），
        // 那时候复制失败就会留下一条指向空文件的记录。
        copying = true
        stickerIoScope.launch {
            val path = copyStickerFile(context, uri)
            withContext(Dispatchers.Main) {
                copying = false
                if (path == null) {
                    errorText = "这张图读不进来（可能是云端图片还没下载到本地，" +
                        "或者格式不支持）。先在相册里打开它、等图片完全显示出来，再试一次。"
                } else {
                    pendingPath = path
                    onAddDialogVisibleChange(true)
                }
            }
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
                            val f = File(sticker.filePath)
                            if (f.exists() && f.length() > 0L) {
                                onStickerPicked(UIMessagePart.Image(url = "file://${sticker.filePath}"))
                            } else {
                                // 以前这里是 if (exists) {...} 后面什么都没有，
                                // 文件丢了就静默不响应，表现是"点了没反应"。现在明确告诉她。
                                errorText = "「${sticker.name}」的图片文件已经不在了，发不出去。" +
                                    "长按这张可以删掉它。"
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
                .clickable(enabled = !copying) { imagePicker.launch("image/*") },
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (copying) {
                    Text("正在保存…", style = MaterialTheme.typography.bodySmall)
                } else {
                    Icon(HugeIcons.PlusSign, contentDescription = "添加表情包")
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !copying) {
                    val scope3 = rememberCoroutineScope()
                    scope3.launch {
                        val count = repository.cleanBroken()
                        errorText = if (count > 0) "已清理 " + count + " 个空白表情包" else "没有空白表情包，都很健康"
                    }
                },
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "清理空白表情包",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }

    // 选完图、文件已经复制好，填名字+标签
    val path = pendingPath
    if (path != null) {
        AddStickerDialog(
            onDismiss = {
                // 取消就把刚复制的文件删掉，不留孤儿文件
                stickerIoScope.launch { runCatching { File(path).delete() } }
                pendingPath = null
                onAddDialogVisibleChange(false)
            },
            onConfirm = { name, tags ->
                // 文件已经在本地了，这里只写数据库，很快
                scope.launch {
                    repository.add(
                        StickerEntity(
                            filePath = path,
                            name = name,
                            tags = tags,
                            addedBy = "yuri",
                        )
                    )
                }
                pendingPath = null
                onAddDialogVisibleChange(false)
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
                    scope.launch { repository.delete(toDelete) }
                    stickerIoScope.launch { runCatching { File(toDelete.filePath).delete() } }
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
    // 文件在不在，进来的时候看一眼。坏掉的图打个角标，让她能认出来长按删掉，
    // 不然那些空白格子会一直躺在面板里，每次都要试一下才知道点不动。
    val broken = remember(sticker.filePath) {
        val f = File(sticker.filePath)
        !f.exists() || f.length() == 0L
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
                // 传 File 而不是裸路径字符串。相册页（AlbumPage）就是这么写的，
                // Coil 对 File 的处理更明确，不会把本地路径当成网络地址去猜。
                AsyncImage(
                    model = File(sticker.filePath),
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
        // dismissOnClickOutside 默认 true：部分设备上点击输入框弹出键盘时，系统会把
        // 键盘弹出过程中的窗口尺寸变化误判成"点击了对话框外部"，导致对话框在你刚点进
        // 输入框、还没来得及打字时就自动关闭（表现就是"点进去就闪退回表情包面板"）。
        // 关掉这个属性，只允许点取消/保存按钮关闭，从根上避免这个误触发。
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

/**
 * 把选中的图片复制进 App 私有目录。
 *
 * 返回 null 表示失败。旧实现是这样的：
 *
 *     context.contentResolver.openInputStream(uri)?.use { ... }
 *     outFile.absolutePath      // 不管有没有真写进去，都返回路径
 *
 * 那个 `?.` 是问题所在：打不开输入流时整块 use 被跳过，文件根本没创建，
 * 但函数照样返回路径、数据库照样插记录，于是列表里出现一张点不动的空白表情包。
 * 现在复制完必须校验文件存在且非空，不合格就删掉半成品并返回 null。
 */
private fun copyStickerFile(context: Context, uri: Uri): String? {
    val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "stickers").apply { mkdirs() }
    val outFile = File(dir, "sticker_${UUID.randomUUID()}.img")
    return try {
        val input = context.contentResolver.openInputStream(uri)
            ?: return null.also { outFile.delete() }
        input.use { ins ->
            FileOutputStream(outFile).use { out -> ins.copyTo(out) }
        }
        if (outFile.exists() && outFile.length() > 0L) {
            outFile.absolutePath
        } else {
            outFile.delete()
            null
        }
    } catch (e: Exception) {
        runCatching { outFile.delete() }
        null
    }
}
