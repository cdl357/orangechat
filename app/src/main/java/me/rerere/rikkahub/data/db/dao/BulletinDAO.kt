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
import me.rerere.rikkahub.data.db.entity.BulletinEntity

@Dao
interface BulletinDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: BulletinEntity): Long

    @Update
    suspend fun update(item: BulletinEntity)

    @Delete
    suspend fun delete(item: BulletinEntity)

    @Query("DELETE FROM bulletin_note WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM bulletin_note WHERE author = :author ORDER BY created_at DESC")
    fun observeByAuthor(author: String): Flow<List<BulletinEntity>>

    @Query("SELECT * FROM bulletin_note ORDER BY created_at DESC")
    fun observeAll(): Flow<List<BulletinEntity>>

    @Query("UPDATE bulletin_note SET collapsed = :collapsed WHERE id = :id")
    suspend fun setCollapsed(id: Int, collapsed: Boolean)
}
