package me.rerere.rikkahub.data.repository
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.dao.AlbumDAO
import me.rerere.rikkahub.data.db.entity.AlbumEntity

class AlbumRepository(private val dao: AlbumDAO) {
    fun observeAll(): Flow<List<AlbumEntity>> = dao.observeAll()
    fun observeBySavedBy(savedBy: String): Flow<List<AlbumEntity>> = dao.observeBySavedBy(savedBy)
    suspend fun add(item: AlbumEntity) = dao.insert(item)
    suspend fun delete(item: AlbumEntity) = dao.delete(item)
    suspend fun deleteById(id: Int) = dao.deleteById(id)
}
