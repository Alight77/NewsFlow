package io.github.alight77.news.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

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
