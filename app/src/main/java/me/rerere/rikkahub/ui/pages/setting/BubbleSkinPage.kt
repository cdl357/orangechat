/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.ui.pages.setting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.bubble.BubblePresets
import me.rerere.rikkahub.data.bubble.BubbleSkin
import me.rerere.rikkahub.data.bubble.BubbleStyle
import me.rerere.rikkahub.data.bubble.CharmConfig
import me.rerere.rikkahub.data.bubble.CharmCorner
import me.rerere.rikkahub.data.bubble.KThemeSkin
import me.rerere.rikkahub.data.bubble.resolveStyle
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.repository.BubbleSkinRepository
import me.rerere.rikkahub.ui.components.bubble.BubbleCharm
import me.rerere.rikkahub.ui.components.bubble.CodeStyleBubbleBackground
import me.rerere.rikkahub.ui.components.bubble.NinePatchBubbleBackground
import me.rerere.rikkahub.ui.components.ui.ColorPickerDialog
import org.koin.compose.koinInject

/**
 * 进程级 IO 作用域（照抄 StickerPicker 的做法）：
 * 页面关掉后正在进行的文件复制不能被取消。
 */
private val bubbleIoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * 气泡皮肤库页面。
 *
 * 入口在 SettingDisplayColorPage（见 PATCH.md 对该文件的 diff）。
 * 用全屏 Dialog 承载而不是注册新路由：Screen 是 sealed class，
 * 加路由要改导航图（该文件不在本次交付范围），Dialog 方式零侵入。
 *
 * 所有预览都用真气泡渲染（CodeStyleBubbleBackground / NinePatchBubbleBackground /
 * BubbleCharm），不用截图。
 */
@Composable
fun BubbleSkinPage(
    onClose: () -> Unit,
) {
    val settingsStore: SettingsStore = koinInject()
    val settings by settingsStore.settingsFlow.collectAsState()
    val bubbleRepository: BubbleSkinRepository = koinInject()
    val kThemes by bubbleRepository.observeAllKThemes().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var editingUser by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var pendingDeleteKTheme by remember { mutableStateOf<KThemeSkin?>(null) }
    var showOverrideColorPicker by remember {
        mutableStateOf<Pair<BubbleStyle, BubbleSkin.CodeStyle>?>(null)
    }

    val skin: BubbleSkin = if (editingUser) {
        settings.displaySetting.userBubbleSkin
    } else {
        settings.displaySetting.assistantBubbleSkin
    }

    // 用 settingsFlow.value（MutableStateFlow，始终最新）做读改写，
    // 避免协程回写时把闭包里捕获的旧 settings 整个写回去
    fun updateSkin(transform: (BubbleSkin) -> BubbleSkin) {
        val currentSkin: BubbleSkin = if (editingUser) {
            settingsStore.settingsFlow.value.displaySetting.userBubbleSkin
        } else {
            settingsStore.settingsFlow.value.displaySetting.assistantBubbleSkin
        }
        val next = transform(currentSkin)
        scope.launch {
            settingsStore.update(
                settingsStore.settingsFlow.value.copy(
                    displaySetting = settingsStore.settingsFlow.value.displaySetting.copy(
                        userBubbleSkin = if (editingUser) next else settingsStore.settingsFlow.value.displaySetting.userBubbleSkin,
                        assistantBubbleSkin = if (!editingUser) next else settingsStore.settingsFlow.value.displaySetting.assistantBubbleSkin,
                    )
                )
            )
        }
    }

    val charmPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        bubbleIoScope.launch {
            val path = runCatching { bubbleRepository.importCharmImage(uri) }.getOrNull()
            withContext(Dispatchers.Main) {
                importing = false
                if (path == null) {
                    errorText = "挂件图片保存失败，换一张试试"
                } else {
                    val current = (skin as? BubbleSkin.CodeStyle)
                    updateSkin {
                        BubbleSkin.CodeStyle(
                            styleId = current?.styleId ?: BubblePresets.ALL.first().id,
                            overrideColor = current?.overrideColor,
                            tailEnabled = current?.tailEnabled ?: true,
                            charm = (current?.charm ?: CharmConfig(imagePath = path)).copy(imagePath = path),
                        )
                    }
                }
            }
        }
    }

    val kThemePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        bubbleIoScope.launch {
            val imported = runCatching { bubbleRepository.importKTheme(uri) }.getOrNull()
            withContext(Dispatchers.Main) {
                importing = false
                if (imported == null) {
                    errorText = "导入失败：不是有效的 .ktheme 皮肤包（缺图或缺 cap 参数）"
                } else {
                    // 导入成功直接应用
                    updateSkin { BubbleSkin.KTheme(themeId = imported.id) }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            // 标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "气泡皮肤",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClose) { Text("关闭") }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 作用侧：用户气泡 / AI 气泡
                item {
                    SectionTitle("应用到")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = editingUser,
                            onClick = { editingUser = true },
                            label = { Text("我的气泡") },
                        )
                        FilterChip(
                            selected = !editingUser,
                            onClick = { editingUser = false },
                            label = { Text("AI 气泡") },
                        )
                    }
                }

                // 模式选择
                item {
                    SectionTitle("皮肤模式")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = skin == BubbleSkin.None,
                            onClick = { updateSkin { BubbleSkin.None } },
                            label = { Text("默认（跟随原配色）") },
                        )
                        FilterChip(
                            selected = skin is BubbleSkin.CodeStyle,
                            onClick = {
                                if (skin !is BubbleSkin.CodeStyle) {
                                    updateSkin {
                                        BubbleSkin.CodeStyle(styleId = BubblePresets.ALL.first().id)
                                    }
                                }
                            },
                            label = { Text("预设皮肤") },
                        )
                        FilterChip(
                            selected = skin is BubbleSkin.KTheme,
                            onClick = {
                                val first = kThemes.firstOrNull()
                                if (first != null) {
                                    updateSkin { BubbleSkin.KTheme(themeId = first.id) }
                                }
                            },
                            label = { Text("ktheme 皮肤") },
                        )
                    }
                    if (skin is BubbleSkin.KTheme && kThemes.isEmpty()) {
                        Text(
                            "还没有导入 .ktheme 皮肤，先在下面导入一个",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ===== CodeStyle：预设 + 底色 + 尾巴 + 挂件 =====
                if (skin is BubbleSkin.CodeStyle) {
                    item {
                        SectionTitle("预设样式（实时预览）")
                    }
                    items(BubblePresets.ALL.chunked(2).size) { rowIndex ->
                        val row = BubblePresets.ALL.chunked(2)[rowIndex]
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { style ->
                                PresetCard(
                                    style = style,
                                    overrideColor = skin.overrideColor,
                                    tailEnabled = skin.tailEnabled,
                                    charm = skin.charm,
                                    selected = skin.styleId == style.id,
                                    onClick = {
                                        updateSkin { skin.copy(styleId = style.id) }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("自定义底色", modifier = Modifier.weight(1f))
                            if (skin.overrideColor != null) {
                                TextButton(onClick = { updateSkin { skin.copy(overrideColor = null) } }) {
                                    Text("重置")
                                }
                            }
                            TextButton(onClick = {
                                val current = skin.resolveStyle() ?: BubblePresets.ALL.first()
                                showOverrideColorPicker = current to skin
                            }) { Text("自定义") }
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("气泡尾巴")
                                Text(
                                    "在气泡底边画一个小三角",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = skin.tailEnabled,
                                onCheckedChange = { checked ->
                                    updateSkin { skin.copy(tailEnabled = checked) }
                                },
                            )
                        }
                    }
                    item { CharmSection(skin = skin, onPickImage = { charmPicker.launch("image/*") }, onUpdate = { c -> updateSkin { skin.copy(charm = c) } }) }
                }

                // ===== KTheme：导入 + 列表 =====
                item {
                    SectionTitle("ktheme 皮肤库")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable(enabled = !importing) { kThemePicker.launch("*/*") }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(if (importing) "正在导入…" else "导入 .ktheme 皮肤包")
                    }
                }
                items(kThemes.size) { index ->
                    val k = kThemes[index]
                    val selected = (skin as? BubbleSkin.KTheme)?.themeId == k.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .let { m ->
                                if (selected) {
                                    m.border(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(12.dp),
                                    )
                                } else {
                                    m
                                }
                            }
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        KThemePreviewBubble(skin = k, modifier = Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            if (selected) {
                                Text(
                                    "使用中",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                TextButton(onClick = { updateSkin { BubbleSkin.KTheme(themeId = k.id) } }) {
                                    Text("使用")
                                }
                            }
                            TextButton(onClick = { pendingDeleteKTheme = k }) {
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    // ===== 弹窗们 =====
    val pickerRequest = showOverrideColorPicker
    if (pickerRequest != null) {
        val (baseStyle, codeStyleSkin) = pickerRequest
        ColorPickerDialog(
            initialColor = codeStyleSkin.overrideColor,
            defaultColor = Color(baseStyle.fillColor),
            onConfirm = { c ->
                showOverrideColorPicker = null
                updateSkin { codeStyleSkin.copy(overrideColor = c) }
            },
            onDismiss = { showOverrideColorPicker = null },
        )
    }

    val toDelete = pendingDeleteKTheme
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteKTheme = null },
            title = { Text("删除皮肤") },
            text = { Text("删除「${toDelete.name}」？皮肤图会一起从本机清掉。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteKTheme = null
                    bubbleIoScope.launch {
                        runCatching { bubbleRepository.deleteKTheme(toDelete) }
                        // 删的是正在用的 → 回到默认
                        if ((skin as? BubbleSkin.KTheme)?.themeId == toDelete.id) {
                            withContext(Dispatchers.Main) { updateSkin { BubbleSkin.None } }
                        }
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteKTheme = null }) { Text("取消") }
            },
        )
    }

    val err = errorText
    if (err != null) {
        AlertDialog(
            onDismissRequest = { errorText = null },
            title = { Text("气泡皮肤") },
            text = { Text(err) },
            confirmButton = {
                TextButton(onClick = { errorText = null }) { Text("知道了") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

/** 单个预设卡片：真气泡实时预览 + 选中态 */
@Composable
private fun PresetCard(
    style: BubbleStyle,
    overrideColor: Long?,
    tailEnabled: Boolean,
    charm: CharmConfig?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resolved = if (overrideColor != null) {
        style.copy(fillColor = overrideColor, gradientEndColor = null)
    } else {
        style
    }
    Box(
        modifier = modifier
            .let { m ->
                if (selected) {
                    m.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(12.dp),
                    )
                } else {
                    m
                }
            }
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SkinPreviewBubble(
                style = resolved,
                tailEnabled = tailEnabled,
                charm = charm,
            )
            Spacer(Modifier.height(6.dp))
            Text(style.name, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** 代码皮肤预览气泡：背景层（无裁剪）→ 内容层 → 挂件层（无裁剪） */
@Composable
private fun SkinPreviewBubble(
    style: BubbleStyle,
    tailEnabled: Boolean = false,
    tailStartSide: Boolean = false,
    charm: CharmConfig? = null,
) {
    Box {
        CodeStyleBubbleBackground(
            style = style,
            tailEnabled = tailEnabled,
            tailStartSide = tailStartSide,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(style.cornerRadiusDp.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = "喵呜~",
                color = style.textColor?.let { Color(it) } ?: Color(0xFF3C3C3C),
            )
        }
        charm?.let {
            Box(modifier = Modifier.matchParentSize()) {
                BubbleCharm(charm = it)
            }
        }
    }
}

/** ktheme 预览气泡：九宫格背景 + 按 spec 内边距排文字 */
@Composable
private fun KThemePreviewBubble(
    skin: KThemeSkin,
    isUser: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val spec = if (isUser) skin.send else skin.receive
    val path = if (isUser) skin.sendImagePath else skin.receiveImagePath
    Box(modifier = modifier) {
        NinePatchBubbleBackground(
            imagePath = path,
            spec = spec,
            modifier = Modifier.matchParentSize(),
        )
        Text(
            text = "预览一条消息",
            color = spec.textColor?.let { Color(it) } ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(
                start = (6f + spec.padLeftPt).dp,
                top = (6f + spec.padTopPt).dp,
                end = (6f + spec.padRightPt).dp,
                bottom = (6f + spec.padBottomPt).dp,
            ),
        )
    }
}

/** 挂件配置区：选图 / 选角 / 大小 / 偏移，带实时预览 */
@Composable
private fun CharmSection(
    skin: BubbleSkin.CodeStyle,
    onPickImage: () -> Unit,
    onUpdate: (CharmConfig?) -> Unit,
) {
    val charm = skin.charm
    SectionTitle("角挂件（小猫趴在气泡上）")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onPickImage)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(if (charm == null) "选择挂件图片（透明底小图）" else "换一张挂件图")
    }

    if (charm != null) {
        // 实时预览
        val previewStyle = skin.resolveStyle() ?: BubblePresets.ALL.first()
        Box(modifier = Modifier.padding(vertical = 8.dp)) {
            SkinPreviewBubble(
                style = previewStyle,
                tailEnabled = skin.tailEnabled,
                charm = charm,
            )
        }

        // 选角
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                CharmCorner.TOP_START to "左上",
                CharmCorner.TOP_END to "右上",
                CharmCorner.BOTTOM_START to "左下",
                CharmCorner.BOTTOM_END to "右下",
            ).forEach { (corner, label) ->
                FilterChip(
                    selected = charm.corner == corner,
                    onClick = { onUpdate(charm.copy(corner = corner)) },
                    label = { Text(label) },
                )
            }
        }

        LabeledSlider(
            label = "大小",
            value = charm.sizeDp,
            range = 16f..64f,
            display = { "${it.toInt()} dp" },
            onChange = { onUpdate(charm.copy(sizeDp = it)) },
        )
        LabeledSlider(
            label = "水平偏移（正=向内）",
            value = charm.offsetXDp,
            range = -30f..30f,
            display = { "${it.toInt()} dp" },
            onChange = { onUpdate(charm.copy(offsetXDp = it)) },
        )
        LabeledSlider(
            label = "垂直偏移（正=向内）",
            value = charm.offsetYDp,
            range = -30f..30f,
            display = { "${it.toInt()} dp" },
            onChange = { onUpdate(charm.copy(offsetYDp = it)) },
        )

        TextButton(onClick = { onUpdate(null) }) {
            Text("移除挂件", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = display(value),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
        )
    }
}
