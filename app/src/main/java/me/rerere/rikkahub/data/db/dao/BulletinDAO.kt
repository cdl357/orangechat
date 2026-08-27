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

    /** 删掉挂在某张便签下面的所有回复（删原贴时连带清理，避免留下孤儿回复） */
    @Query("DELETE FROM bulletin_note WHERE reply_to = :parentId")
    suspend fun deleteRepliesOf(parentId: Int)

    @Query("SELECT * FROM bulletin_note WHERE author = :author ORDER BY created_at DESC")
    fun observeByAuthor(author: String): Flow<List<BulletinEntity>>

    @Query("SELECT * FROM bulletin_note ORDER BY created_at DESC")
    fun observeAll(): Flow<List<BulletinEntity>>

    @Query("UPDATE bulletin_note SET collapsed = :collapsed WHERE id = :id")
    suspend fun setCollapsed(id: Int, collapsed: Boolean)

    /**
     * 按云端 id 查。同步时用来判断这张便签是不是已经拉下来过了。
     * remoteId 为空的一律当"没有"处理 —— 本地便签的 remote_id 全是空串，
     * 不排掉的话第一次同步就会误判成"已存在"，云端便签永远拉不进来。
     */
    @Query("SELECT * FROM bulletin_note WHERE remote_id = :remoteId AND remote_id != '' LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): BulletinEntity?
}
