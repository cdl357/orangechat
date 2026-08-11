/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.ui.pages.album

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.PlusSign
import me.rerere.rikkahub.data.db.entity.AlbumEntity
import me.rerere.rikkahub.data.db.entity.AlbumFolderEntity
import me.rerere.rikkahub.ui.components.nav.BackButton
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// ── 拍立得配色 ──────────────────────────────────────────────
private val AlbumBgTop = Color(0xFFF5FBFD)
private val AlbumBgBottom = Color(0xFFE8F4F8)
private val AccentBlue = Color(0xFF6A9AAA)
private val TextMain = Color(0xFF4A6A7A)
private val TextSub = Color(0xFF8AB4C4)
private val TimelineDot = Color(0xFF7AB4C4)

/**
 * 相册主页面：三层结构
 * 1. 相册本子列表（本页）
 * 2. 点进一本 -> AlbumFolderDetail（照片时间线）
 * 3. 点开一张 -> 大图查看（内嵌 Dialog）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumPage(vm: AlbumVM = koinViewModel()) {
    val items by vm.allItems.collectAsStateWithLifecycle()
    val folders by vm.allFolders.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var openedFolder by remember { mutableStateOf<AlbumFolderEntity?>(null) }

    // 兜底：老版本/AI存图（save_to_album默认folderId=0）留下的、不属于任何现存相册本子的照片，
    // 不能让它们"存进去了但哪都看不到"，用一个虚拟的"未分类"相册兜底展示。
    val orphanPhotos = remember(items, folders) {
        val realFolderIds = folders.map { it.id }.toSet()
        items.filter { it.folderId !in realFolderIds }
    }
    val virtualUnsortedFolder = remember(orphanPhotos.isNotEmpty()) {
        if (orphanPhotos.isNotEmpty()) {
            AlbumFolderEntity(id = -1, name = "未分类", createdBy = "sean")
        } else null
    }

    val opened = openedFolder
    if (opened != null) {
        val isVirtual = opened.id == -1
        AlbumFolderDetailPage(
            folder = opened,
            photos = if (isVirtual) orphanPhotos else items.filter { it.folderId == opened.id },
            vm = vm,
            onBack = { openedFolder = null },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("相册") },
                navigationIcon = { BackButton() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AlbumBgTop),
            )
        },
        containerColor = AlbumBgTop,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = Color(0xFF6AB4C8),
                contentColor = Color.White,
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(HugeIcons.PlusSign, contentDescription = "新建相册")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Brush.verticalGradient(listOf(AlbumBgTop, AlbumBgBottom)))
        ) {
            if (folders.isEmpty() && virtualUnsortedFolder == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有相册，点 + 新建一本", color = TextSub, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    virtualUnsortedFolder?.let { unsorted ->
                        item(key = "unsorted") {
                            FolderRow(
                                folder = unsorted,
                                photoCount = orphanPhotos.size,
                                previewPhotos = orphanPhotos.take(3),
                                onClick = { openedFolder = unsorted },
                            )
                        }
                    }
                    items(folders, key = { it.id }) { folder ->
                        val folderPhotos = items.filter { it.folderId == folder.id }
                        FolderRow(
                            folder = folder,
                            photoCount = folderPhotos.size,
                            previewPhotos = folderPhotos.take(3),
                            onClick = { openedFolder = folder },
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, createdBy ->
                vm.createFolder(name, createdBy)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun FolderRow(
    folder: AlbumFolderEntity,
    photoCount: Int,
    previewPhotos: List<AlbumEntity>,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = Color.White,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 预览缩略图堆叠
            Box(modifier = Modifier.size(56.dp)) {
                if (previewPhotos.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFFE8F4F8), Color(0xFFD8EEF4)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(HugeIcons.Image02, contentDescription = null, tint = Color(0xFFA0C8D4), modifier = Modifier.size(22.dp))
                    }
                } else {
                    val photo = previewPhotos.first()
                    val file = remember(photo.filePath) { File(photo.filePath) }
                    if (photo.filePath.isNotBlank() && file.exists()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(file).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFFE8F4F8), Color(0xFFD8EEF4))))
                        )
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(folder.name, fontSize = 15.sp, color = TextMain, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                val subLabel = if (folder.id == -1) {
                    "$photoCount 张 · 旧数据兜底"
                } else {
                    "$photoCount 张 · ${if (folder.createdBy == "sean") "Sean" else "Yuri"}建的"
                }
                Text(subLabel, fontSize = 11.sp, color = TextSub)
            }
        }
    }
}

@Composable
private fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, createdBy: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var createdBy by remember { mutableStateOf("sean") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text("新建相册") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("相册名称") },
                    placeholder = { Text("例：她 / 我们俩 / 猫猫") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("建立人：", fontSize = 13.sp)
                    Surface(
                        onClick = { createdBy = "sean" },
                        color = if (createdBy == "sean") Color(0xFFD8EEF4) else Color(0xFFF0F0F0),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text("Sean", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp)
                    }
                    Surface(
                        onClick = { createdBy = "yuri" },
                        color = if (createdBy == "yuri") Color(0xFFD8EEF4) else Color(0xFFF0F0F0),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text("Yuri", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = { onConfirm(name, createdBy) }, enabled = name.isNotBlank()) {
                Text("建立")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ══════════════════════════════════════════════════════════
// 相册本子详情页（第二层）
// ══════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumFolderDetailPage(
    folder: AlbumFolderEntity,
    photos: List<AlbumEntity>,
    vm: AlbumVM,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var viewingPhoto by remember { mutableStateOf<AlbumEntity?>(null) }
    // 虚拟"未分类"相册（id = -1）只是兜底展示旧数据/AI存图落到默认分组的照片，
    // 不支持往里加照片——加照片应该走真实相册本子。
    val isVirtualFolder = folder.id == -1

    // 刚导进来、等着写备注的照片路径。
    // 存图和写备注分成两步：图先落地（相册给的读权限时效短，拖不得），
    // 备注可以慢慢写，也可以跳过。
    var pendingPhotoPath by remember { mutableStateOf<String?>(null) }
    // 正在编辑备注的已有照片
    var editingCaptionOf by remember { mutableStateOf<AlbumEntity?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val path = saveImageToPrivate(context, it, "album_${folder.id}")
            if (path.isNotBlank()) {
                pendingPhotoPath = path
            }
        }
    }

    val grouped = remember(photos) {
        photos.sortedByDescending { it.createdAt }
            .groupBy { SimpleDateFormat("yyyy年 M月", Locale.CHINA).format(Date(it.createdAt)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(folder.name)
                        Text("${photos.size} 张", fontSize = 12.sp, color = TextSub)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(HugeIcons.ArrowLeft01, contentDescription = "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AlbumBgTop),
            )
        },
        containerColor = AlbumBgTop,
        floatingActionButton = {
            if (!isVirtualFolder) {
                FloatingActionButton(
                    onClick = { imagePicker.launch("image/*") },
                    containerColor = Color(0xFF6AB4C8),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(HugeIcons.PlusSign, contentDescription = "加照片")
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Brush.verticalGradient(listOf(AlbumBgTop, AlbumBgBottom)))
        ) {
            if (photos.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (isVirtualFolder) "没有未分类的照片" else "这本相册还没有照片，点 + 导入",
                        color = TextSub,
                        fontSize = 13.sp,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp),
                ) {
                    grouped.forEach { (dateLabel, groupPhotos) ->
                        item {
                            Row(
                                modifier = Modifier.padding(bottom = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(modifier = Modifier.width(10.dp).height(10.dp).background(TimelineDot, CircleShape))
                                Spacer(Modifier.width(16.dp))
                                Text(dateLabel, fontSize = 13.sp, color = TextMain, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                            }
                        }
                        item {
                            androidx.compose.foundation.layout.FlowRow(
                                modifier = Modifier.padding(start = 36.dp, bottom = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                groupPhotos.forEach { photo ->
                                    PolaroidCard(photo, onClick = { viewingPhoto = photo })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    viewingPhoto?.let { photo ->
        PhotoViewerDialog(
            photo = photo,
            onDismiss = { viewingPhoto = null },
            onDelete = {
                vm.delete(photo)
                viewingPhoto = null
            },
            onEditCaption = {
                editingCaptionOf = photo
                viewingPhoto = null
            }
        )
    }

    // 新导入的照片：写备注
    pendingPhotoPath?.let { path ->
        CaptionDialog(
            title = "给这张照片写点什么",
            hint = "存的时候是什么心情？看到它想起了什么？\n不想写就跳过，以后点开照片还能补。",
            initial = "",
            onDismiss = {
                // 跳过也要把照片存进去，只是没有备注
                vm.saveImage(filePath = path, savedBy = folder.createdBy, folderId = folder.id)
                pendingPhotoPath = null
            },
            onConfirm = { text ->
                vm.saveImage(
                    filePath = path,
                    caption = text,
                    savedBy = folder.createdBy,
                    folderId = folder.id,
                )
                pendingPhotoPath = null
            },
            dismissLabel = "跳过",
        )
    }

    // 已有照片：改备注
    editingCaptionOf?.let { photo ->
        CaptionDialog(
            title = if (photo.caption.isBlank()) "给这张照片写点什么" else "改一下备注",
            hint = "当时的心情，或者现在再看到它的感觉。",
            initial = photo.caption,
            onDismiss = { editingCaptionOf = null },
            onConfirm = { text ->
                vm.updateCaption(photo.id, text)
                editingCaptionOf = null
            },
        )
    }
}

/**
 * 备注编辑框。
 * 刻意做成多行、不限长度——她说的是"短短的几句话，或者一段话都可以"。
 * containerColor 写死白色：玻璃主题下 AlertDialog 默认容器色会被全局
 * 透明度设置调透，字会糊在背景上看不清。
 */
@Composable
private fun CaptionDialog(
    title: String,
    hint: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    dismissLabel: String = "取消",
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { Text(title, fontSize = 16.sp, color = TextMain) },
        text = {
            Column {
                Text(hint, fontSize = 12.sp, color = TextSub)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    placeholder = { Text("写点什么…", fontSize = 13.sp, color = TextSub) },
                    minLines = 5,
                    maxLines = 12,
                )
                if (text.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text("${text.length} 字", fontSize = 10.sp, color = TextSub)
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = { onConfirm(text) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        }
    )
}

@Composable
private fun PolaroidCard(photo: AlbumEntity, onClick: () -> Unit) {
    val rotation = remember(photo.id) { if (photo.id % 2 == 0) 2f else -2f }
    val dateStr = remember(photo.createdAt) {
        SimpleDateFormat("M/d", Locale.CHINA).format(Date(photo.createdAt))
    }

    Column(
        modifier = Modifier
            .width(150.dp)
            .rotate(rotation)
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(8.dp, 8.dp, 8.dp, 12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFE8F4F8), Color(0xFFD8EEF4))))
                ,
            contentAlignment = Alignment.Center,
        ) {
            val file = remember(photo.filePath) { File(photo.filePath) }
            if (photo.filePath.isNotBlank() && file.exists()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(file).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                        
                )
            } else {
                Text("\uD83D\uDDBC\uFE0F", fontSize = 32.sp)
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
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                lineHeight = 14.sp,
            )
        } else {
            // 空备注也占一行位置，一是提示可以写，二是让卡片高度别忽高忽低
            Text(
                "点开写点什么",
                fontSize = 9.sp,
                color = Color(0xFFC0D8E0),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun PhotoViewerDialog(
    photo: AlbumEntity,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onEditCaption: () -> Unit = {},
) {
    val dateStr = remember(photo.createdAt) {
        SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(photo.createdAt))
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
        ) {
            // 备注可能写得很长，整个内容区要能滚
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                val file = remember(photo.filePath) { File(photo.filePath) }
                if (photo.filePath.isNotBlank() && file.exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(file).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(10.dp)),
                    )
                }
                Spacer(Modifier.height(12.dp))

                // 备注区：有就显示全文（可点改），没有就显示一句引导
                if (photo.caption.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF5FBFD))
                            .clickable(onClick = onEditCaption)
                            .padding(12.dp)
                    ) {
                        Text(photo.caption, fontSize = 14.sp, color = TextMain, lineHeight = 22.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("点这里改备注", fontSize = 10.sp, color = TextSub)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF5FBFD))
                            .clickable(onClick = onEditCaption)
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("＋ 写点什么", fontSize = 13.sp, color = AccentBlue)
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(dateStr, fontSize = 11.sp, color = TextSub)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDelete) { Text("删除", color = Color(0xFFD86060)) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onEditCaption) { Text("备注", color = AccentBlue) }
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        }
    }
}

private fun saveImageToPrivate(context: android.content.Context, uri: android.net.Uri, prefix: String): String {
    return try {
        val dir = File(context.filesDir, "album_photos").apply { mkdirs() }
        val outFile = File(dir, "${prefix}_${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
        outFile.absolutePath
    } catch (e: Exception) {
        ""
    }
}
