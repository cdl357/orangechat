/*
 * 橘瓣 OrangeChat
 */
package me.rerere.rikkahub.data.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.AlbumEntity

@Dao
interface AlbumDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: AlbumEntity): Long

    @Delete
    suspend fun delete(item: AlbumEntity)

    @Query("DELETE FROM album_item WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM album_item ORDER BY created_at DESC")
    fun observeAll(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM album_item WHERE saved_by = :savedBy ORDER BY created_at DESC")
    fun observeBySavedBy(savedBy: String): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM album_item WHERE folder_id = :folderId ORDER BY created_at DESC")
    fun observeByFolder(folderId: Int): Flow<List<AlbumEntity>>
}
