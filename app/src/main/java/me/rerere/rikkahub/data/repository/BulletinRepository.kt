/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.dao.BulletinDAO
import me.rerere.rikkahub.data.db.entity.BulletinEntity

class BulletinRepository(private val dao: BulletinDAO) {
    fun observeByAuthor(author: String): Flow<List<BulletinEntity>> = dao.observeByAuthor(author)
    fun observeAll(): Flow<List<BulletinEntity>> = dao.observeAll()
    suspend fun add(item: BulletinEntity) = dao.insert(item)
    suspend fun delete(item: BulletinEntity) = dao.delete(item)
    suspend fun setCollapsed(id: Int, collapsed: Boolean) = dao.setCollapsed(id, collapsed)
}
