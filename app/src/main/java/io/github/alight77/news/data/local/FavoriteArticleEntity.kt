package io.github.alight77.news.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.alight77.news.domain.model.Article
import java.time.Instant

@Entity(tableName = "favorite_articles")
data class FavoriteArticleEntity(
    @PrimaryKey val articleId: String,
    val title: String,
    val description: String?,
    val contentPreview: String?,
    val originalUrl: String?,
    val imageUrl: String?,
    val publishedAtEpochMillis: Long?,
    val sourceName: String?,
    val savedAtEpochMillis: Long,
)

fun Article.toFavoriteArticleEntity(savedAtEpochMillis: Long) = FavoriteArticleEntity(
    articleId = id,
    title = title,
    description = description,
    contentPreview = contentPreview,
    originalUrl = originalUrl,
    imageUrl = imageUrl,
    publishedAtEpochMillis = publishedAt?.toEpochMilli(),
    sourceName = sourceName,
    savedAtEpochMillis = savedAtEpochMillis,
)

fun FavoriteArticleEntity.toArticle() = Article(
    id = articleId,
    title = title,
    description = description,
    contentPreview = contentPreview,
    originalUrl = originalUrl,
    imageUrl = imageUrl,
    publishedAt = publishedAtEpochMillis?.let(Instant::ofEpochMilli),
    sourceName = sourceName,
)
