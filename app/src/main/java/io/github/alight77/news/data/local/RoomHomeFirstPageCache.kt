package io.github.alight77.news.data.local

import io.github.alight77.news.domain.model.Article
import io.github.alight77.news.domain.model.NewsCategory
import io.github.alight77.news.domain.repository.CachedHomeFirstPage
import io.github.alight77.news.domain.repository.HomeFirstPageCache
import java.time.Instant

class RoomHomeFirstPageCache(
    private val dao: HomeFirstPageCacheDao,
) : HomeFirstPageCache {
    override suspend fun read(category: NewsCategory): CachedHomeFirstPage? =
        dao.read(category.apiValue)?.let { snapshot ->
            CachedHomeFirstPage(
                articles = snapshot.articles.map(HomeFirstPageArticleEntity::toArticle),
                fetchedAtEpochMillis = snapshot.fetchedAtEpochMillis,
            )
        }

    override suspend fun replace(
        category: NewsCategory,
        articles: List<Article>,
        fetchedAtEpochMillis: Long,
    ) {
        dao.replace(
            categoryApiValue = category.apiValue,
            fetchedAtEpochMillis = fetchedAtEpochMillis,
            articles = articles.mapIndexed { position, article ->
                article.toHomeFirstPageArticleEntity(category.apiValue, position)
            },
        )
    }
}

private fun Article.toHomeFirstPageArticleEntity(
    categoryApiValue: String,
    position: Int,
) = HomeFirstPageArticleEntity(
    categoryApiValue = categoryApiValue,
    position = position,
    articleId = id,
    title = title,
    description = description,
    contentPreview = contentPreview,
    originalUrl = originalUrl,
    imageUrl = imageUrl,
    publishedAtEpochMillis = publishedAt?.toEpochMilli(),
    sourceName = sourceName,
)

private fun HomeFirstPageArticleEntity.toArticle() = Article(
    id = articleId,
    title = title,
    description = description,
    contentPreview = contentPreview,
    originalUrl = originalUrl,
    imageUrl = imageUrl,
    publishedAt = publishedAtEpochMillis?.let(Instant::ofEpochMilli),
    sourceName = sourceName,
)
