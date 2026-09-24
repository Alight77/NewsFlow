package io.github.alight77.news.domain.repository

import io.github.alight77.news.domain.model.Article
import kotlinx.coroutines.flow.Flow

interface FavoriteRepository {
    fun observeFavorites(): Flow<List<Article>>

    fun observeFavoriteArticleIds(): Flow<Set<String>>

    suspend fun isFavorite(articleId: String): Boolean

    suspend fun save(article: Article)

    suspend fun remove(articleId: String)
}
