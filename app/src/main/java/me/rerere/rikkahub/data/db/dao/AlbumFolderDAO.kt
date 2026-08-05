/*
 * 橘瓣 OrangeChat
 */
package me.rerere.rikkahub.data.db.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.entity.AlbumFolderEntity

@Dao
interface AlbumFolderDAO {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: AlbumFolderEntity): Long

    @Delete
    suspend fun delete(item: AlbumFolderEntity)

    @Query("SELECT * FROM album_folder ORDER BY created_at ASC")
    fun observeAll(): Flow<List<AlbumFolderEntity>>

    @Query("SELECT * FROM album_folder WHERE id = :id")
    suspend fun getById(id: Int): AlbumFolderEntity?
}
