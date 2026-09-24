package io.github.alight77.news.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteArticleDao {
    @Query("SELECT * FROM favorite_articles ORDER BY savedAtEpochMillis DESC")
    fun observeAll(): Flow<List<FavoriteArticleEntity>>

    @Query("SELECT articleId FROM favorite_articles")
    fun observeArticleIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(article: FavoriteArticleEntity)

    @Query("SELECT * FROM favorite_articles WHERE articleId = :articleId LIMIT 1")
    suspend fun getById(articleId: String): FavoriteArticleEntity?

    @Query("DELETE FROM favorite_articles WHERE articleId = :articleId")
    suspend fun deleteById(articleId: String)
}
