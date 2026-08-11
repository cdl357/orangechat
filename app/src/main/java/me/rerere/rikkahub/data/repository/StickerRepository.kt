/* 
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.rerere.rikkahub.data.db.dao.StickerDAO
import me.rerere.rikkahub.data.db.entity.StickerEntity
import java.io.File

class StickerRepository(private val dao: StickerDAO) {
    /** 全部记录（含可能坏掉的）——只给 cleanBroken 内部用 */
    fun observeAll(): Flow<List<StickerEntity>> = dao.observeAll()

    /** UI 用的：只返回文件真实存在且非空的记录，坏的不显示 */
    fun observeAllValid(): Flow<List<StickerEntity>> = dao.observeAll().map { list ->
        list.filter { s ->
            val f = File(s.filePath)
            f.exists() && f.length() > 0L
        }
    }

    suspend fun add(item: StickerEntity): Long = dao.insert(item)
    suspend fun delete(item: StickerEntity) = dao.delete(item)
    suspend fun deleteById(id: Int) = dao.deleteById(id)
    suspend fun update(item: StickerEntity) = dao.update(item)

    /** 批量删除文件已丢失的记录，返回清理数量 */
    suspend fun cleanBroken(): Int {
        val all = dao.observeAll().first()
        var count = 0
        all.forEach { s ->
            val f = File(s.filePath)
            if (!f.exists() || f.length() == 0L) {
                dao.deleteById(s.id)
                runCatching { f.delete() }
                count++
            }
        }
        return count
    }
}
