/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Update
import androidx.room.Update
import androidx.room.Update
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.StickerEntity

@Dao
interface StickerDAO {
    @Query("SELECT * FROM sticker_item ORDER BY created_at DESC")
    fun observeAll(): Flow<List<StickerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: StickerEntity): Long

    @Delete
    suspend fun delete(item: StickerEntity)

    @Query("DELETE FROM sticker_item WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Update(onConflict = OnConflictStrategy.REPLACE)
    suspend fun update(item: StickerEntity)
}
