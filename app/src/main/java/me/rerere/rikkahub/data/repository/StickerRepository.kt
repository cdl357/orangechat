/* 
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.data.repository

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
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
     * 根据 Uri 推断 MIME type，返回如 "image/gif", "image/png", "image/jpeg" 等。
     * 回退默认 "image/png"。
     */
    private fun resolveMimeType(uri: Uri): String {
        // 先试 ContentResolver
        val fromCr = context.contentResolver.getType(uri)
        if (!fromCr.isNullOrBlank() && fromCr.startsWith("image/")) return fromCr

        // 再试扩展名
        val ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        if (!ext.isNullOrBlank()) {
            val fromExt = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
            if (!fromExt.isNullOrBlank() && fromExt.startsWith("image/")) return fromExt
        }

        return "image/png"
    }

    /**
     * 从 MIME type 得到文件扩展名（不带点），如 "gif", "png", "jpeg", "webp"。
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
     * 根据文件扩展名推断 MIME type（支持 gif/webp/jpg/png）。
     */
    suspend fun uploadFileToCloud(file: File): String? {
        if (!file.exists() || file.length() == 0L) return null
        val mime = when (file.extension.lowercase()) {
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "jpg", "jpeg" -> "image/jpeg"
            "svg" -> "image/svg+xml"
            else -> "image/png"
        }
        return file.inputStream().use { uploadToCloud(it, file.name, mime) }
    }

    /**
     * 启动时迁移：把所有还没上传的本地表情包批量上传到云端。
     * 成功的更新 remoteUrl，失败的跳过下次再来。
     * 全部完成后可以安全删除本地文件（但不强制删，让系统自己清理）。
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
     * 从 Uri 选图 → 上传云端 → 返回 StickerEntity。
     * 正确保留原始 MIME type（gif/webp/png/jpg 都行）。
     * 如果上传失败，远程 URL 为空，暂存本地路径兜底。
     */
    suspend fun addStickerFromUri(
        uri: Uri,
        name: String,
        tags: String,
        addedBy: String = "yuri"
    ): StickerEntity? = withContext(Dispatchers.IO) {
        // 1. 推断 MIME type 和正确扩展名
        val mimeType = resolveMimeType(uri)
        val ext = extensionFromMime(mimeType)

        // 2. 复制到本地临时文件（保留正确扩展名）
        val stickersDir = File(context.filesDir, "stickers")
        stickersDir.mkdirs()
        val destFile = File(stickersDir, "${System.currentTimeMillis()}_${UUID.randomUUID()}.$ext")

        val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
        inputStream.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        if (!destFile.exists() || destFile.length() == 0L) {
            return@withContext null
        }

        // 3. 上传到 Supabase Storage（用正确的 MIME type）
        val remoteUrl = destFile.inputStream().use { stream ->
            uploadToCloud(stream, destFile.name, mimeType)
        } ?: ""

        // 4. 存数据库
        val entity = StickerEntity(
            filePath = destFile.absolutePath,
            name = name,
            tags = tags,
            addedBy = addedBy,
            remoteUrl = remoteUrl,
        )
        val id = dao.insert(entity)

        // 5. 如果上传成功，可以删本地文件
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
