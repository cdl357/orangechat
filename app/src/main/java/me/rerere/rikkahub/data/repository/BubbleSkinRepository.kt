/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.data.repository

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.bubble.BubbleSpriteSpec
import me.rerere.rikkahub.data.bubble.KThemeParser
import me.rerere.rikkahub.data.bubble.KThemeSkin
import me.rerere.rikkahub.data.db.dao.BubbleKThemeDAO
import me.rerere.rikkahub.data.db.entity.BubbleKThemeEntity
import java.io.File
import java.util.UUID

/**
 * 气泡皮肤库：ktheme 皮肤条的增删查 + 挂件图片导入。
 * 图片全部落 app 私有目录（filesDir/ktheme、filesDir/bubble_charm），
 * 复制+校验套路照 StickerRepository。
 */
class BubbleSkinRepository(
    private val dao: BubbleKThemeDAO,
    private val context: Context,
) {
    fun observeAllKThemes(): Flow<List<KThemeSkin>> = dao.observeAll().map { list ->
        list.mapNotNull { it.toKThemeSkin() }
    }

    suspend fun getKTheme(id: Long): KThemeSkin? =
        dao.getById(id)?.toKThemeSkin()

    /** 从 Uri 导入 .ktheme：解析 + 落图 + 入库，返回带 id 的皮肤；失败返回 null */
    suspend fun importKTheme(uri: Uri): KThemeSkin? = withContext(Dispatchers.IO) {
        val parsed = KThemeParser.parse(context, uri) ?: return@withContext null
        // 发送/接收图是解析强校验过的；群图/背景图存在才登记
        val entity = parsed.toEntity()
        val id = dao.insert(entity)
        parsed.copy(id = id)
    }

    /** 删除皮肤：先删库再删私有目录里的图文件 */
    suspend fun deleteKTheme(skin: KThemeSkin) = withContext(Dispatchers.IO) {
        dao.deleteById(skin.id)
        listOfNotNull(
            skin.sendImagePath,
            skin.receiveImagePath,
            skin.sendGroupImagePath,
            skin.receiveGroupImagePath,
            skin.bgImagePath,
        ).forEach { path ->
            if (path.isNotBlank()) {
                runCatching { File(path).delete() }
            }
        }
    }

    /**
     * 挂件图片导入：选图 → 复制进私有目录 → 存路径。
     * 照 StickerRepository.addStickerFromUri 的 tmp→校验→magic 定格式→改名套路
     * （不信任 ContentResolver 报的 MIME）。
     */
    suspend fun importCharmImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "bubble_charm").apply { mkdirs() }
        val tmpFile = File(dir, "${System.currentTimeMillis()}_${UUID.randomUUID()}.tmp")
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: return@withContext null
        inputStream.use { input ->
            tmpFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        if (!tmpFile.exists() || tmpFile.length() == 0L) {
            runCatching { tmpFile.delete() }
            return@withContext null
        }
        val ext = extensionFromMime(detectMimeFromFile(tmpFile))
        val destFile = File(dir, "${tmpFile.nameWithoutExtension}.$ext")
        if (!tmpFile.renameTo(destFile)) {
            runCatching { tmpFile.delete() }
            return@withContext null
        }
        destFile.absolutePath
    }

    /** 从 magic bytes 检测格式（照抄 StickerRepository.detectMimeFromFile 的判定） */
    private fun detectMimeFromFile(file: File): String {
        if (!file.exists() || file.length() < 12) return "image/png"
        return try {
            file.inputStream().use { input ->
                val bytes = ByteArray(16)
                val read = input.read(bytes)
                if (read < 8) return "image/png"
                val header6 = bytes.copyOfRange(0, 6).toString(Charsets.US_ASCII)
                if (header6 == "GIF89a" || header6 == "GIF87a") return "image/gif"
                if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return "image/jpeg"
                if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                    bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
                ) return "image/png"
                if (read >= 12) {
                    val riff = bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII)
                    val webp = bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII)
                    if (riff == "RIFF" && webp == "WEBP") return "image/webp"
                }
                "image/png"
            }
        } catch (_: Exception) {
            "image/png"
        }
    }

    private fun extensionFromMime(mime: String): String = when {
        mime.contains("gif") -> "gif"
        mime.contains("webp") -> "webp"
        mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
        mime.contains("svg") -> "svg"
        else -> "png"
    }
}

// ===== KThemeSkin <-> Entity 映射 =====

internal fun KThemeSkin.toEntity(): BubbleKThemeEntity = BubbleKThemeEntity(
    name = name,
    sendImagePath = sendImagePath,
    receiveImagePath = receiveImagePath,
    sendGroupImagePath = sendGroupImagePath,
    receiveGroupImagePath = receiveGroupImagePath,
    bgImagePath = bgImagePath,
    sendCapLeftPt = send.capLeftPt,
    sendCapTopPt = send.capTopPt,
    sendPadTopPt = send.padTopPt,
    sendPadLeftPt = send.padLeftPt,
    sendPadBottomPt = send.padBottomPt,
    sendPadRightPt = send.padRightPt,
    sendTextColor = send.textColor,
    receiveCapLeftPt = receive.capLeftPt,
    receiveCapTopPt = receive.capTopPt,
    receivePadTopPt = receive.padTopPt,
    receivePadLeftPt = receive.padLeftPt,
    receivePadBottomPt = receive.padBottomPt,
    receivePadRightPt = receive.padRightPt,
    receiveTextColor = receive.textColor,
    sendGroupCapLeftPt = sendGroup?.capLeftPt,
    sendGroupCapTopPt = sendGroup?.capTopPt,
    sendGroupPadTopPt = sendGroup?.padTopPt,
    sendGroupPadLeftPt = sendGroup?.padLeftPt,
    sendGroupPadBottomPt = sendGroup?.padBottomPt,
    sendGroupPadRightPt = sendGroup?.padRightPt,
    sendGroupTextColor = sendGroup?.textColor,
    receiveGroupCapLeftPt = receiveGroup?.capLeftPt,
    receiveGroupCapTopPt = receiveGroup?.capTopPt,
    receiveGroupPadTopPt = receiveGroup?.padTopPt,
    receiveGroupPadLeftPt = receiveGroup?.padLeftPt,
    receiveGroupPadBottomPt = receiveGroup?.padBottomPt,
    receiveGroupPadRightPt = receiveGroup?.padRightPt,
    receiveGroupTextColor = receiveGroup?.textColor,
    scale = send.scale,
    createdAt = System.currentTimeMillis(),
)

internal fun BubbleKThemeEntity.toKThemeSkin(): KThemeSkin = KThemeSkin(
    id = id,
    name = name,
    sendImagePath = sendImagePath,
    receiveImagePath = receiveImagePath,
    sendGroupImagePath = sendGroupImagePath,
    receiveGroupImagePath = receiveGroupImagePath,
    bgImagePath = bgImagePath,
    send = BubbleSpriteSpec(
        capLeftPt = sendCapLeftPt,
        capTopPt = sendCapTopPt,
        padTopPt = sendPadTopPt,
        padLeftPt = sendPadLeftPt,
        padBottomPt = sendPadBottomPt,
        padRightPt = sendPadRightPt,
        textColor = sendTextColor,
        scale = scale,
    ),
    receive = BubbleSpriteSpec(
        capLeftPt = receiveCapLeftPt,
        capTopPt = receiveCapTopPt,
        padTopPt = receivePadTopPt,
        padLeftPt = receivePadLeftPt,
        padBottomPt = receivePadBottomPt,
        padRightPt = receivePadRightPt,
        textColor = receiveTextColor,
        scale = scale,
    ),
    sendGroup = if (sendGroupImagePath != null && sendGroupCapLeftPt != null && sendGroupCapTopPt != null) {
        BubbleSpriteSpec(
            capLeftPt = sendGroupCapLeftPt,
            capTopPt = sendGroupCapTopPt,
            padTopPt = sendGroupPadTopPt ?: 0f,
            padLeftPt = sendGroupPadLeftPt ?: 0f,
            padBottomPt = sendGroupPadBottomPt ?: 0f,
            padRightPt = sendGroupPadRightPt ?: 0f,
            textColor = sendGroupTextColor,
            scale = scale,
        )
    } else {
        null
    },
    receiveGroup = if (receiveGroupImagePath != null && receiveGroupCapLeftPt != null && receiveGroupCapTopPt != null) {
        BubbleSpriteSpec(
            capLeftPt = receiveGroupCapLeftPt,
            capTopPt = receiveGroupCapTopPt,
            padTopPt = receiveGroupPadTopPt ?: 0f,
            padLeftPt = receiveGroupPadLeftPt ?: 0f,
            padBottomPt = receiveGroupPadBottomPt ?: 0f,
            padRightPt = receiveGroupPadRightPt ?: 0f,
            textColor = receiveGroupTextColor,
            scale = scale,
        )
    } else {
        null
    },
)
