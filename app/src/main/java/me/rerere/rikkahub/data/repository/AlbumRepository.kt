package me.rerere.rikkahub.data.repository

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.db.dao.AlbumDAO
import me.rerere.rikkahub.data.db.entity.AlbumEntity
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

class AlbumRepository(
    private val dao: AlbumDAO,
    private val context: Context
) {
    companion object {
        private const val SUPABASE_URL = "https://byqqwypdfiwvalozihgs.supabase.co"
        private const val SUPABASE_KEY =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJ5cXF3eXBkZml3dmFsb3ppaGdzIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4MzY1NDA4MCwiZXhwIjoyMDk5MjMwMDgwfQ.LIbE9DFsLSRhOig5bUUfUP4r7t1ykdNy8L0gZM_xtGw"
        private const val BUCKET = "album"
    }

    fun observeAll(): Flow<List<AlbumEntity>> = dao.observeAll()
    fun observeBySavedBy(savedBy: String): Flow<List<AlbumEntity>> = dao.observeBySavedBy(savedBy)
    fun observeByFolder(folderId: Int): Flow<List<AlbumEntity>> = dao.observeByFolder(folderId)
    suspend fun add(item: AlbumEntity) = dao.insert(item)
    suspend fun delete(item: AlbumEntity) = dao.delete(item)
    suspend fun deleteById(id: Int) = dao.deleteById(id)
    suspend fun updateCaption(id: Int, caption: String) = dao.updateCaption(id, caption)

    /** 给已存过的照片补写画面描述和第一印象。空串表示该字段不改。 */
    suspend fun updateNote(id: Int, photoDesc: String, impression: String) =
        dao.updateNote(id, photoDesc, impression)

    /** 又见到这张照片了：seen +1 */
    suspend fun touchSeen(id: Int) = dao.touchSeen(id, System.currentTimeMillis())

    // ── 内容哈希 ────────────────────────────────────────────────

    /**
     * 算一个文件的 SHA-256 全量哈希。
     *
     * 全量而不是抽样：抽样在同一张图被重新压缩过之后就对不上了，
     * 而全量哈希的语义很干净 —— 字节完全一样才算同一张。
     * 分块读，不整个文件读进内存（大图会 OOM）。
     */
    suspend fun hashOf(file: File): String? = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) return@withContext null
        try {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { ins ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = ins.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
            }
            md.digest().joinToString("") { b -> "%02x".format(b) }
        } catch (_: Exception) {
            null
        }
    }

    /** 这张图见过没有？见过就返回那条记录（并把 seen +1）。 */
    suspend fun findByHash(hash: String): AlbumEntity? = withContext(Dispatchers.IO) {
        if (hash.isBlank()) return@withContext null
        dao.findByHash(hash)
    }

    /**
     * 给老照片补算哈希。启动时跑一次，之后就没活干了。
     * 本地文件已经没了的跳过（可能只剩云端，那种没法算）。
     */
    suspend fun backfillHashes(): Int = withContext(Dispatchers.IO) {
        val pending = dao.getAllOnce().filter { it.contentHash.isBlank() && it.filePath.isNotBlank() }
        var done = 0
        for (item in pending) {
            val h = hashOf(File(item.filePath)) ?: continue
            dao.updateHash(item.id, h)
            done++
        }
        done
    }

    // ── 云端 ────────────────────────────────────────────────────

    /**
     * 上传到 Supabase Storage，返回公网 URL；失败返回 null。
     * 和表情包用的是同一套（StickerRepository.uploadToCloud）。
     */
    suspend fun uploadToCloud(
        inputStream: InputStream,
        fileName: String,
        mimeType: String = "image/jpeg"
    ): String? = withContext(Dispatchers.IO) {
        try {
            val bytes = inputStream.readBytes()
            val remoteName = "${UUID.randomUUID()}_$fileName"
            val conn = URL("$SUPABASE_URL/storage/v1/object/$BUCKET/$remoteName")
                .openConnection() as HttpURLConnection
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
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /** MIME 从 magic bytes 认，不信扩展名（有机型会把 GIF 报成 jpeg）。 */
    private fun detectMime(file: File): String = try {
        val head = ByteArray(12)
        val n = file.inputStream().use { it.read(head) }
        when {
            n >= 8 && head[0] == 0x89.toByte() && head[1] == 'P'.code.toByte() -> "image/png"
            n >= 3 && head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() -> "image/jpeg"
            n >= 6 && head[0] == 'G'.code.toByte() && head[1] == 'I'.code.toByte() -> "image/gif"
            n >= 12 && head[8] == 'W'.code.toByte() && head[9] == 'E'.code.toByte() -> "image/webp"
            else -> "image/jpeg"
        }
    } catch (_: Exception) {
        "image/jpeg"
    }

    suspend fun uploadFileToCloud(file: File): String? {
        if (!file.exists() || file.length() == 0L) return null
        return file.inputStream().use { uploadToCloud(it, file.name, detectMime(file)) }
    }

    /**
     * 启动时批量上传还没上云的照片，顺便回填哈希。
     *
     * 和表情包那套的关键差别：**上传成功后不删本地文件**。
     * 表情包丢了重新加一张就是，照片丢了就真没了 —— 相册要的是安全，不是省空间。
     * 失败的不管，下次启动再试。
     */
    suspend fun migrateLocalToCloud(): Int = withContext(Dispatchers.IO) {
        val pending = dao.getAllOnce().filter { it.remoteUrl.isBlank() && it.filePath.isNotBlank() }
        var done = 0
        for (item in pending) {
            val file = File(item.filePath)
            if (!file.exists() || file.length() == 0L) continue
            val url = uploadFileToCloud(file)
            if (url != null) {
                dao.updateRemoteUrl(item.id, url)
                done++
            }
            if (item.contentHash.isBlank()) {
                hashOf(file)?.let { h -> dao.updateHash(item.id, h) }
            }
        }
        done
    }

    /**
     * 从 URL 保存图片到相册（支持 file:// 和 http(s):// ）
     * @param url 图片地址（file:// 本地路径 或 http(s):// 网络图片）
     * @param folderId 目标相册 ID（0 = 未分组，UI 里归到"未分类"）
     * @param savedBy "sean" 或 "yuri"
     * @param caption 备注（可选）
     * @return 保存成功的 AlbumEntity，失败返回 null
     */
    suspend fun saveFromUrl(
        url: String,
        folderId: Int = 0,
        savedBy: String = "sean",
        caption: String = ""
    ): AlbumEntity? = withContext(Dispatchers.IO) {
        try {
            val albumDir = File(context.filesDir, "album_photos")
            if (!albumDir.exists()) albumDir.mkdirs()

            val fileName = "album_${UUID.randomUUID()}.jpg"
            val destFile = File(albumDir, fileName)

            when {
                url.startsWith("content://") -> {
                    val input = context.contentResolver.openInputStream(Uri.parse(url))
                        ?: return@withContext null
                    input.use { ins ->
                        FileOutputStream(destFile).use { out -> ins.copyTo(out) }
                    }
                }
                url.startsWith("file://") -> {
                    // 本地文件直接复制
                    val srcFile = File(url.removePrefix("file://"))
                    if (!srcFile.exists()) return@withContext null
                    srcFile.copyTo(destFile, overwrite = true)
                }
                url.startsWith("http://") || url.startsWith("https://") -> {
                    // 网络图片下载
                    URL(url).openStream().use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                else -> {
                    // 其他格式（可能是本地绝对路径）
                    val srcFile = File(url)
                    if (!srcFile.exists()) return@withContext null
                    srcFile.copyTo(destFile, overwrite = true)
                }
            }

            if (!destFile.exists() || destFile.length() == 0L) {
                destFile.delete()
                return@withContext null
            }

            // 去重：算哈希，见过就不重复存，只把 seen +1
            val hash = hashOf(destFile) ?: ""
            if (hash.isNotBlank()) {
                val dup = dao.findByHash(hash)
                if (dup != null) {
                    destFile.delete()
                    dao.touchSeen(dup.id, System.currentTimeMillis())
                    return@withContext dup
                }
            }

            val remoteUrl = uploadFileToCloud(destFile) ?: ""

            val entity = AlbumEntity(
                id = 0,
                filePath = destFile.absolutePath,
                remoteUrl = remoteUrl,
                contentHash = hash,
                folderId = folderId,
                savedBy = savedBy,
                caption = caption,
                lastSeen = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis()
            )
            dao.insert(entity)
            entity
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
