/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.dao.TodoDAO
import me.rerere.rikkahub.data.db.entity.TodoEntity

class TodoRepository(private val dao: TodoDAO) {

    fun observeActiveForTarget(target: String): Flow<List<TodoEntity>> =
        dao.observeActiveByTarget(target)

    fun observeAll(): Flow<List<TodoEntity>> = dao.observeAll()

    fun observeActive(): Flow<List<TodoEntity>> = dao.observeActive()

    fun observeDone(): Flow<List<TodoEntity>> = dao.observeDone()

    suspend fun add(item: TodoEntity) = dao.insert(item)

    suspend fun update(item: TodoEntity) = dao.update(item)

    suspend fun setDone(id: Int, done: Boolean) {
        val doneAt = if (done) System.currentTimeMillis() else 0L
        dao.setDone(id, done, doneAt)
    }

    suspend fun delete(item: TodoEntity) = dao.delete(item)

    suspend fun deleteById(id: Int) = dao.deleteById(id)
}
