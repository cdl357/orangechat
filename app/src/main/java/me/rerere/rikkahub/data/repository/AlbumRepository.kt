package me.rerere.rikkahub.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.db.dao.AlbumDAO
import me.rerere.rikkahub.data.db.entity.AlbumEntity
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.UUID

class AlbumRepository(
    private val dao: AlbumDAO,
    private val context: Context
) {
    fun observeAll(): Flow<List<AlbumEntity>> = dao.observeAll()
    fun observeBySavedBy(savedBy: String): Flow<List<AlbumEntity>> = dao.observeBySavedBy(savedBy)
    fun observeByFolder(folderId: Int): Flow<List<AlbumEntity>> = dao.observeByFolder(folderId)
    suspend fun add(item: AlbumEntity) = dao.insert(item)
    suspend fun delete(item: AlbumEntity) = dao.delete(item)
    suspend fun deleteById(id: Int) = dao.deleteById(id)
    suspend fun updateCaption(id: Int, caption: String) = dao.updateCaption(id, caption)

    /**
     * 从 URL 保存图片到相册（支持 file:// 和 http(s):// ）
     * @param url 图片地址（file:// 本地路径 或 http(s):// 网络图片）
     * @param folderId 目标相册 ID（-1 = 未分类）
     * @param savedBy "sean" 或 "yuri"
     * @param caption 备注（可选）
     * @return 保存成功的 AlbumEntity，失败返回 null
     */
    suspend fun saveFromUrl(
        url: String,
        folderId: Int = -1,
        savedBy: String = "sean",
        caption: String = ""
    ): AlbumEntity? = withContext(Dispatchers.IO) {
        try {
            val albumDir = File(context.filesDir, "album_images")
            if (!albumDir.exists()) albumDir.mkdirs()

            val fileName = "album_${UUID.randomUUID()}.jpg"
            val destFile = File(albumDir, fileName)

            when {
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

            val entity = AlbumEntity(
                id = 0,
                filePath = destFile.absolutePath,
                folderId = folderId,
                savedBy = savedBy,
                caption = caption,
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
