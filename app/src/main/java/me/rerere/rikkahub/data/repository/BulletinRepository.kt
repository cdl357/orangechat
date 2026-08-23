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

    /**
     * 删便签。如果删的是原贴，挂在它下面的回复一起删掉，
     * 否则那些回复会变成指向不存在的 id 的孤儿（页面上就消失了但还占着库）。
     */
    suspend fun delete(item: BulletinEntity) {
        if (item.replyTo == 0) {
            dao.deleteRepliesOf(item.id)
        }
        dao.delete(item)
    }

    suspend fun setCollapsed(id: Int, collapsed: Boolean) = dao.setCollapsed(id, collapsed)
}
