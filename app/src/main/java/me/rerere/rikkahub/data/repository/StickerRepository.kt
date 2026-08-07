/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.dao.StickerDAO
import me.rerere.rikkahub.data.db.entity.StickerEntity

class StickerRepository(private val dao: StickerDAO) {
    fun observeAll(): Flow<List<StickerEntity>> = dao.observeAll()
    suspend fun add(item: StickerEntity): Long = dao.insert(item)
    suspend fun delete(item: StickerEntity) = dao.delete(item)
    suspend fun deleteById(id: Int) = dao.deleteById(id)
}
