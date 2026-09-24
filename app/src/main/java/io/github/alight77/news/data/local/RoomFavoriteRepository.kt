package io.github.alight77.news.data.local

import io.github.alight77.news.domain.model.Article
import io.github.alight77.news.domain.repository.FavoriteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomFavoriteRepository(
    private val dao: FavoriteArticleDao,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : FavoriteRepository {
    override fun observeFavorites(): Flow<List<Article>> =
        dao.observeAll().map { entities -> entities.map(FavoriteArticleEntity::toArticle) }

    override fun observeFavoriteArticleIds(): Flow<Set<String>> =
        dao.observeArticleIds().map { it.toSet() }

    override suspend fun isFavorite(articleId: String): Boolean = dao.getById(articleId) != null

    override suspend fun save(article: Article) {
        dao.upsert(article.toFavoriteArticleEntity(currentTimeMillis()))
    }

    override suspend fun remove(articleId: String) {
        dao.deleteById(articleId)
    }
}
