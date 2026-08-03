/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.TodoEntity

@Dao
interface TodoDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: TodoEntity): Long

    @Update
    suspend fun update(item: TodoEntity)

    @Delete
    suspend fun delete(item: TodoEntity)

    @Query("DELETE FROM todo_item WHERE id = :id")
    suspend fun deleteById(id: Int)

    /** 所有未完成，按创建时间升序 */
    @Query("SELECT * FROM todo_item WHERE done = 0 ORDER BY created_at ASC")
    fun observeActive(): Flow<List<TodoEntity>>

    /** 所有已完成，按完成时间降序 */
    @Query("SELECT * FROM todo_item WHERE done = 1 ORDER BY done_at DESC")
    fun observeDone(): Flow<List<TodoEntity>>

    /** 指定 target 的未完成 */
    @Query("SELECT * FROM todo_item WHERE done = 0 AND target = :target ORDER BY created_at ASC")
    fun observeActiveByTarget(target: String): Flow<List<TodoEntity>>

    /** 全部（用于调试或备份） */
    @Query("SELECT * FROM todo_item ORDER BY created_at DESC")
    fun observeAll(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todo_item WHERE id = :id")
    suspend fun getById(id: Int): TodoEntity?

    @Query("UPDATE todo_item SET done = :done, done_at = :doneAt WHERE id = :id")
    suspend fun setDone(id: Int, done: Boolean, doneAt: Long)
}
