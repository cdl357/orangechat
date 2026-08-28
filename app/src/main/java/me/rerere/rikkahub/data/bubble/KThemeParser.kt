/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.data.bubble

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * .ktheme 皮肤包解析器。.ktheme 是一个 zip，里面（按任务书已知信息）：
 *  - 精灵图：发送/接收/群发送/群接收 四张 png + 可选背景图
 *  - CSS 里带 capLeft / capTop / padTop/padLeft/padBottom/padRight，单位 pt
 *  - 一个整数 scale，实际像素 = pt 值 × scale
 *
 * ⚠️ 任务书没给 zip 内条目的精确命名和 CSS 的精确字段名，这里用的是
 * **保守的启发式**（见 [roleOf] 与 [cssValue]），凡拿不准的都在 REPORT.md
 * 的诚实清单里列了「需要真实 .ktheme 样本才能确认」。
 *
 * 图片落地：照 StickerRepository 的复制+校验套路 —— 先写 .tmp，
 * 校验（长度 > 0 且 PNG magic bytes）后改成 .png，落 filesDir/ktheme/。
 */
object KThemeParser {

    /** zip 里 png 的角色归类（按文件名猜，见 REPORT 诚实清单） */
    private fun roleOf(name: String): String? {
        val n = name.lowercase()
        val isGroup = n.contains("group")
        val isSend = n.contains("send")
        val isReceive = n.contains("recv") || n.contains("receive")
        val isBg = n.contains("background") || n.endsWith("bg.png") ||
            n.contains("_bg") || n.contains("-bg")
        return when {
            isGroup && isSend -> "send_group"
            isGroup && isReceive -> "receive_group"
            isSend -> "send"
            isReceive -> "receive"
            isBg -> "bg"
            else -> null
        }
    }

    /** 从 CSS 文本里抠 `key: value` 形式的值（兼容大小写/空白/引号） */
    private fun cssValue(css: String, key: String): String? {
        val regex = Regex("""(?i)${Regex.escape(key)}\s*:\s*["']?([-\w#.%,]+)""")
        return regex.find(css)?.groupValues?.get(1)
    }

    private fun pt(css: String, key: String): Float? =
        cssValue(css, key)?.lowercase()?.removeSuffix("pt")?.toFloatOrNull()

    /** "#RRGGBB" / "#RRGGBBAA" → 0xAARRGGBB，失败返回 null */
    private fun hexColor(css: String, key: String): Long? {
        val raw = cssValue(css, key)?.removePrefix("#") ?: return null
        return when (raw.length) {
            6 -> runCatching { 0xFF000000L or java.lang.Long.parseLong(raw, 16) }.getOrNull()
            8 -> runCatching { java.lang.Long.parseLong(raw, 16) }.getOrNull()
            else -> null
        }
    }

    /**
     * 解析 .ktheme。任何一步不满足（打不开、不是 zip、缺发送/接收图、缺 capLeft/capTop）
     * 都返回 null，由调用方提示用户。不做「猜一个默认值继续跑」的事。
     */
    fun parse(context: Context, uri: Uri): KThemeSkin? = runCatching {
        parseOrThrow(context, uri)
    }.getOrNull()

    private fun parseOrThrow(context: Context, uri: Uri): KThemeSkin {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: error("无法打开所选文件")
        val dir = File(context.filesDir, "ktheme").apply { mkdirs() }

        var cssText: String? = null
        val images = mutableMapOf<String, File>()

        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory) {
                    when {
                        name.endsWith(".css", ignoreCase = true) -> {
                            // 只保留第一份 CSS；多份 CSS 的场景见 REPORT 诚实清单
                            if (cssText == null) {
                                cssText = zip.readBytes().toString(Charsets.UTF_8)
                            }
                        }

                        name.endsWith(".png", ignoreCase = true) -> {
                            val role = roleOf(name)
                            if (role != null && !images.containsKey(role)) {
                                images[role] = copyVerifiedPng(zip, dir)
                            }
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val css = cssText ?: error("ktheme 里没有 CSS")
        val sendFile = images["send"] ?: error("ktheme 里找不到发送气泡图")
        val receiveFile = images["receive"] ?: error("ktheme 里找不到接收气泡图")

        val capLeft = pt(css, "capLeft") ?: error("CSS 里找不到 capLeft")
        val capTop = pt(css, "capTop") ?: error("CSS 里找不到 capTop")
        val padTop = pt(css, "padTop") ?: 0f
        val padLeft = pt(css, "padLeft") ?: 0f
        val padBottom = pt(css, "padBottom") ?: 0f
        val padRight = pt(css, "padRight") ?: 0f
        val scale = cssValue(css, "scale")?.toIntOrNull() ?: 1
        val textColor = hexColor(css, "textColor") ?: hexColor(css, "color")
        val name = cssValue(css, "name") ?: "导入皮肤"

        fun spec(): BubbleSpriteSpec = BubbleSpriteSpec(
            capLeftPt = capLeft,
            capTopPt = capTop,
            padTopPt = padTop,
            padLeftPt = padLeft,
            padBottomPt = padBottom,
            padRightPt = padRight,
            textColor = textColor,
            scale = scale,
        )

        // 群发/群收是可选的；本项目聊天界面当前没有群聊上下文，
        // 解出来先入库备用（渲染端只用 send/receive，见 REPORT）
        val sendGroup = images["send_group"]?.let { it.absolutePath to spec() }
        val receiveGroup = images["receive_group"]?.let { it.absolutePath to spec() }

        return KThemeSkin(
            id = 0L, // 由 Room insert 分配
            name = name,
            sendImagePath = sendFile.absolutePath,
            receiveImagePath = receiveFile.absolutePath,
            sendGroupImagePath = sendGroup?.first,
            receiveGroupImagePath = receiveGroup?.first,
            bgImagePath = images["bg"]?.absolutePath,
            send = spec(),
            receive = spec(),
            sendGroup = sendGroup?.second,
            receiveGroup = receiveGroup?.second,
        )
    }

    /**
     * 把 zip 当前条目复制到 dir 下的临时文件，校验（长度 > 0 + PNG magic）
     * 后重命名为 .png。照 StickerRepository.addStickerFromUri 的 tmp→校验→改名套路
     * （ref/StickerRepository.kt:200-223）。
     */
    private fun copyVerifiedPng(zip: ZipInputStream, dir: File): File {
        val tmpFile = File(dir, "${System.currentTimeMillis()}_${UUID.randomUUID()}.tmp")
        // ZipInputStream 读到当前条目末尾返回 -1，copyTo 只消费当前条目
        tmpFile.outputStream().use { output -> zip.copyTo(output) }
        if (!tmpFile.exists() || tmpFile.length() == 0L) {
            runCatching { tmpFile.delete() }
            error("png 条目是空的")
        }
        val magic = ByteArray(4)
        val read = tmpFile.inputStream().use { it.read(magic) }
        val isPng = read >= 4 &&
            magic[0] == 0x89.toByte() &&
            magic[1] == 0x50.toByte() &&
            magic[2] == 0x4E.toByte() &&
            magic[3] == 0x47.toByte()
        if (!isPng) {
            runCatching { tmpFile.delete() }
            error("皮肤里的图不是有效 PNG")
        }
        val destFile = File(dir, "${tmpFile.nameWithoutExtension}.png")
        if (!tmpFile.renameTo(destFile)) {
            runCatching { tmpFile.delete() }
            error("落地文件重命名失败")
        }
        return destFile
    }
}
