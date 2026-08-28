/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */
package me.rerere.rikkahub.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.BubbleKThemeEntity

@Dao
interface BubbleKThemeDAO {
    @Query("SELECT * FROM bubble_ktheme_skin ORDER BY created_at DESC")
    fun observeAll(): Flow<List<BubbleKThemeEntity>>

    @Query("SELECT * FROM bubble_ktheme_skin WHERE id = :id")
    suspend fun getById(id: Long): BubbleKThemeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: BubbleKThemeEntity): Long

    @Query("DELETE FROM bubble_ktheme_skin WHERE id = :id")
    suspend fun deleteById(id: Long)
}
