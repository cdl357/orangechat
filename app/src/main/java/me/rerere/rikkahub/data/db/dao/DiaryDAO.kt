/*
 * 橘瓣 OrangeChat
 */
package me.rerere.rikkahub.data.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.DiaryEntity

@Dao
interface DiaryDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DiaryEntity): Long

    @Update
    suspend fun update(item: DiaryEntity)

    @Delete
    suspend fun delete(item: DiaryEntity)

    @Query("SELECT * FROM diary_entry ORDER BY created_at DESC")
    fun observeAll(): Flow<List<DiaryEntity>>

    @Query("SELECT * FROM diary_entry WHERE author = :author ORDER BY created_at DESC")
    fun observeByAuthor(author: String): Flow<List<DiaryEntity>>

    @Query("SELECT * FROM diary_entry WHERE date_group = :date ORDER BY created_at DESC")
    suspend fun getByDate(date: String): List<DiaryEntity>

    @Query("SELECT DISTINCT date_group FROM diary_entry WHERE date_group != '' ORDER BY date_group DESC")
    fun observeDates(): Flow<List<String>>

    @Query("SELECT * FROM diary_entry WHERE id = :id")
    suspend fun getById(id: Int): DiaryEntity?
}
