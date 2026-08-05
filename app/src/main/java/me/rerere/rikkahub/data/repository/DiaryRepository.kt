package me.rerere.rikkahub.data.repository
import kotlinx.coroutines.flow.Flow
import me.rerere.rikkahub.data.db.dao.DiaryDAO
import me.rerere.rikkahub.data.db.entity.DiaryEntity

class DiaryRepository(private val dao: DiaryDAO) {
    fun observeAll(): Flow<List<DiaryEntity>> = dao.observeAll()
    fun observeByAuthor(author: String): Flow<List<DiaryEntity>> = dao.observeByAuthor(author)
    fun observeDates(): Flow<List<String>> = dao.observeDates()
    suspend fun add(item: DiaryEntity) = dao.insert(item)
    suspend fun update(item: DiaryEntity) = dao.update(item)
    suspend fun delete(item: DiaryEntity) = dao.delete(item)
    suspend fun getById(id: Int) = dao.getById(id)
}
