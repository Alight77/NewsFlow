package io.github.alight77.news.domain.model

import java.time.Instant

data class Article(
    val id: String,
    val title: String,
    val description: String?,
    val contentPreview: String?,
    val originalUrl: String?,
    val imageUrl: String?,
    val publishedAt: Instant?,
    val sourceName: String?,
)

data class ArticlePage(
    val articles: List<Article>,
    val rawArticleCount: Int,
)

class InvalidNewsDataException : Exception("GNews returned no usable articles.")
