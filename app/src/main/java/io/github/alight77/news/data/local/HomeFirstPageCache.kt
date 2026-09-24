package io.github.alight77.news.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

@Entity(
    tableName = "home_first_page_articles",
    primaryKeys = ["categoryApiValue", "position"],
)
data class HomeFirstPageArticleEntity(
    val categoryApiValue: String,
    val position: Int,
    val articleId: String,
    val title: String,
    val description: String?,
    val contentPreview: String?,
    val originalUrl: String?,
    val imageUrl: String?,
    val publishedAtEpochMillis: Long?,
    val sourceName: String?,
)

@Entity(tableName = "home_first_page_cache_metadata")
data class HomeFirstPageCacheMetadataEntity(
    @PrimaryKey val categoryApiValue: String,
    val fetchedAtEpochMillis: Long,
)

@Dao
abstract class HomeFirstPageCacheDao {
    @Query(
        "SELECT * FROM home_first_page_articles " +
            "WHERE categoryApiValue = :categoryApiValue ORDER BY position ASC",
    )
    abstract suspend fun getArticles(categoryApiValue: String): List<HomeFirstPageArticleEntity>

    @Query("SELECT * FROM home_first_page_cache_metadata WHERE categoryApiValue = :categoryApiValue LIMIT 1")
    abstract suspend fun getMetadata(categoryApiValue: String): HomeFirstPageCacheMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertArticles(articles: List<HomeFirstPageArticleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertMetadata(metadata: HomeFirstPageCacheMetadataEntity)

    @Query("DELETE FROM home_first_page_articles WHERE categoryApiValue = :categoryApiValue")
    protected abstract suspend fun deleteArticles(categoryApiValue: String)

    @Transaction
    open suspend fun replace(
        categoryApiValue: String,
        fetchedAtEpochMillis: Long,
        articles: List<HomeFirstPageArticleEntity>,
    ) {
        deleteArticles(categoryApiValue)
        if (articles.isNotEmpty()) insertArticles(articles)
        upsertMetadata(
            HomeFirstPageCacheMetadataEntity(
                categoryApiValue = categoryApiValue,
                fetchedAtEpochMillis = fetchedAtEpochMillis,
            ),
        )
    }
}
