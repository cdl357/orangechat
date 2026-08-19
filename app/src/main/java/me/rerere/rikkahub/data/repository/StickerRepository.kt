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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.db.dao.StickerDAO
import me.rerere.rikkahub.data.db.entity.StickerEntity
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class StickerRepository(
    private val dao: StickerDAO,
    private val context: Context,
) {
    companion object {
        private const val SUPABASE_URL = "https://byqqwypdfiwvalozihgs.supabase.co"
        private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJ5cXF3eXBkZml3dmFsb3ppaGdzIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4MzY1NDA4MCwiZXhwIjoyMDk5MjMwMDgwfQ.LIbE9DFsLSRhOig5bUUfUP4r7t1ykdNy8L0gZM_xtGw"
        private const val BUCKET = "stickers"
    }

    /** 全部记录——只给内部迁移用 */
    fun observeAll(): Flow<List<StickerEntity>> = dao.observeAll()

    /**
     * UI 用的：只返回有效的记录。
     * 有 remoteUrl 的直接有效；没有 remoteUrl 的，回退看本地文件是否存在。
     */
    fun observeAllValid(): Flow<List<StickerEntity>> = dao.observeAll().map { list ->
        list.filter { s ->
            if (s.remoteUrl.isNotBlank()) return@filter true
            val f = File(s.filePath)
            f.exists() && f.length() > 0L
        }
    }

    suspend fun add(item: StickerEntity): Long = dao.insert(item)
    suspend fun delete(item: StickerEntity) = dao.delete(item)
    suspend fun deleteById(id: Int) = dao.deleteById(id)
    suspend fun update(item: StickerEntity) = dao.update(item)

    /** 批量删除文件已丢失且没有远程 URL 的记录，返回清理数量 */
    suspend fun cleanBroken(): Int {
        val all = dao.observeAll().first()
        var count = 0
        all.forEach { s ->
            // 有远程 URL 的不算坏
            if (s.remoteUrl.isNotBlank()) return@forEach
            val f = File(s.filePath)
            if (!f.exists() || f.length() == 0L) {
                dao.deleteById(s.id)
                runCatching { f.delete() }
                count++
            }
        }
        return count
    }

    /**
     * 从文件的 magic bytes 检测真实 MIME type。
     * 不依赖 ContentResolver（某些机型 ContentResolver 会把 GIF 误报为 jpeg）。
     */
    private fun detectMimeFromFile(file: File): String {
        if (!file.exists() || file.length() < 12) return "image/png"
        return try {
            file.inputStream().use { input ->
                val bytes = ByteArray(16)
                val read = input.read(bytes)
                if (read < 8) return "image/png"

                // GIF: "GIF89a" or "GIF87a"
                val header6 = bytes.copyOfRange(0, 6).toString(Charsets.US_ASCII)
                if (header6 == "GIF89a" || header6 == "GIF87a") return "image/gif"

                // JPEG: FF D8
                if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return "image/jpeg"

                // PNG: 89 50 4E 47 0D 0A 1A 0A
                if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                    bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) return "image/png"

                // WebP: "RIFF" + 4 bytes + "WEBP"
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

    /**
     * 从 MIME type 得到文件扩展名（不带点）。
     */
    private fun extensionFromMime(mime: String): String {
        return when {
            mime.contains("gif") -> "gif"
            mime.contains("webp") -> "webp"
            mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
            mime.contains("svg") -> "svg"
            else -> "png"
        }
    }

    /**
     * 上传图片到 Supabase Storage，返回公网 URL。
     * 失败返回 null。
     */
    suspend fun uploadToCloud(inputStream: InputStream, fileName: String, mimeType: String = "image/png"): String? =
        withContext(Dispatchers.IO) {
            try {
                val bytes = inputStream.readBytes()
                val remoteName = "${UUID.randomUUID()}_$fileName"
                val uploadUrl = "$SUPABASE_URL/storage/v1/object/$BUCKET/$remoteName"

                val conn = URL(uploadUrl).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
                conn.setRequestProperty("Content-Type", mimeType)
                conn.setRequestProperty("x-upsert", "true")
                conn.doOutput = true
                conn.connectTimeout = 30_000
                conn.readTimeout = 30_000

                conn.outputStream.use { it.write(bytes) }

                val code = conn.responseCode
                conn.disconnect()

                if (code in 200..299) {
                    "$SUPABASE_URL/storage/v1/object/public/$BUCKET/$remoteName"
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }

    /**
     * 上传本地文件到 Supabase Storage，返回公网 URL。
     * MIME type 从文件 magic bytes 检测（不信任扩展名）。
     */
    suspend fun uploadFileToCloud(file: File): String? {
        if (!file.exists() || file.length() == 0L) return null
        val mime = detectMimeFromFile(file)
        return file.inputStream().use { uploadToCloud(it, file.name, mime) }
    }

    /**
     * 启动时迁移：把所有还没上传的本地表情包批量上传到云端。
     * 成功的更新 remoteUrl，失败的跳过下次再来。
     */
    suspend fun migrateLocalToCloud() = withContext(Dispatchers.IO) {
        val all = dao.observeAll().first()
        val pending = all.filter { it.remoteUrl.isBlank() && it.filePath.isNotBlank() }
        if (pending.isEmpty()) return@withContext

        for (sticker in pending) {
            val file = File(sticker.filePath)
            if (!file.exists() || file.length() == 0L) {
                // 本地文件已丢，直接删记录
                dao.deleteById(sticker.id)
                continue
            }
            val url = uploadFileToCloud(file)
            if (url != null) {
                dao.update(sticker.copy(remoteUrl = url))
                // 上传成功后删本地文件（可选，省空间）
                runCatching { file.delete() }
            }
            // 失败的不管，下次启动再试
        }
    }

    /**
     * 从 Uri 选图 → 复制到本地 → 用 magic bytes 检测真实 MIME → 上传云端。
     * 不信任 ContentResolver.getType()（已知 HONOR 等机型会把 GIF 误报为 jpeg）。
     */
    suspend fun addStickerFromUri(
        uri: Uri,
        name: String,
        tags: String,
        addedBy: String = "yuri"
    ): StickerEntity? = withContext(Dispatchers.IO) {
        // 1. 先复制到本地临时文件（先用 .tmp 扩展名，稍后改名）
        val stickersDir = File(context.filesDir, "stickers")
        stickersDir.mkdirs()
        val tmpFile = File(stickersDir, "${System.currentTimeMillis()}_${UUID.randomUUID()}.tmp")

        val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
        inputStream.use { input ->
            tmpFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        if (!tmpFile.exists() || tmpFile.length() == 0L) {
            return@withContext null
        }

        // 2. 从文件 magic bytes 检测真实 MIME type（不信任 ContentResolver）
        val mimeType = detectMimeFromFile(tmpFile)
        val ext = extensionFromMime(mimeType)

        // 3. 用正确的扩展名重命名
        val destFile = File(stickersDir, "${tmpFile.nameWithoutExtension}.$ext")
        tmpFile.renameTo(destFile)

        // 4. 上传到 Supabase Storage（用正确的 MIME type）
        val remoteUrl = destFile.inputStream().use { stream ->
            uploadToCloud(stream, destFile.name, mimeType)
        } ?: ""

        // 5. 存数据库
        val entity = StickerEntity(
            filePath = destFile.absolutePath,
            name = name,
            tags = tags,
            addedBy = addedBy,
            remoteUrl = remoteUrl,
        )
        val id = dao.insert(entity)

        // 6. 如果上传成功，可以删本地文件
        if (remoteUrl.isNotBlank()) {
            runCatching { destFile.delete() }
        }

        entity.copy(id = id.toInt())
    }

    /**
     * 删除表情包：同时删云端文件（如果有的话）。
     */
    suspend fun deleteSticker(sticker: StickerEntity) = withContext(Dispatchers.IO) {
        dao.deleteById(sticker.id)
        // 删本地文件
        if (sticker.filePath.isNotBlank()) {
            runCatching { File(sticker.filePath).delete() }
        }
        // 删云端文件（best effort，失败不管）
        if (sticker.remoteUrl.isNotBlank()) {
            runCatching { deleteFromCloud(sticker.remoteUrl) }
        }
    }

    private fun deleteFromCloud(url: String) {
        // 从 URL 提取文件名
        val prefix = "$SUPABASE_URL/storage/v1/object/public/$BUCKET/"
        if (!url.startsWith(prefix)) return
        val remoteName = url.removePrefix(prefix)
        val deleteUrl = "$SUPABASE_URL/storage/v1/object/$BUCKET/$remoteName"

        val conn = URL(deleteUrl).openConnection() as HttpURLConnection
        conn.requestMethod = "DELETE"
        conn.setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        runCatching { conn.responseCode }
        conn.disconnect()
    }
}
