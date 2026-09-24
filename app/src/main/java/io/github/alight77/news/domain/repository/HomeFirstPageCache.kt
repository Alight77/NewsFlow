package io.github.alight77.news.domain.repository

import io.github.alight77.news.domain.model.Article
import io.github.alight77.news.domain.model.NewsCategory

data class CachedHomeFirstPage(
    val articles: List<Article>,
    val fetchedAtEpochMillis: Long,
)

interface HomeFirstPageCache {
    suspend fun read(category: NewsCategory): CachedHomeFirstPage?

    suspend fun replace(
        category: NewsCategory,
        articles: List<Article>,
        fetchedAtEpochMillis: Long,
    )
}
