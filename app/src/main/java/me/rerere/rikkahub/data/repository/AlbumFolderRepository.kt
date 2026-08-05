package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.dao.AlbumFolderDAO
import me.rerere.rikkahub.data.db.entity.AlbumFolderEntity

class AlbumFolderRepository(private val dao: AlbumFolderDAO) {
    fun observeAll(): Flow<List<AlbumFolderEntity>> = dao.observeAll()
    suspend fun add(item: AlbumFolderEntity): Long = dao.insert(item)
    suspend fun delete(item: AlbumFolderEntity) = dao.delete(item)
    suspend fun getById(id: Int) = dao.getById(id)
}
