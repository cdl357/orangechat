/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.dao.LoveDateDAO
import me.rerere.rikkahub.data.db.entity.LoveDateEntity

class LoveDateRepository(private val dao: LoveDateDAO) {
    fun observeAll(): Flow<List<LoveDateEntity>> = dao.getAll()
    suspend fun add(entity: LoveDateEntity) = dao.insert(entity)
    suspend fun delete(entity: LoveDateEntity) = dao.delete(entity)
    suspend fun deleteById(id: Int) = dao.deleteById(id)
}
