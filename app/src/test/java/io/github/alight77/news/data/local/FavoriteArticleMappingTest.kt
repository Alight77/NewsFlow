package io.github.alight77.news.data.local

import io.github.alight77.news.domain.model.Article
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteArticleMappingTest {
    @Test
    fun `favorite snapshot preserves every article field needed by the detail screen`() {
        val article = Article(
            id = "article-id",
            title = "新闻标题",
            description = "新闻摘要",
            contentPreview = "内容预览",
            originalUrl = "https://example.com/article",
            imageUrl = "https://example.com/image.jpg",
            publishedAt = Instant.parse("2026-09-24T01:02:03Z"),
            sourceName = "新闻来源",
        )

        val snapshot = article.toFavoriteArticleEntity(savedAtEpochMillis = 1_727_136_000_000)

        assertEquals(
            FavoriteArticleEntity(
                articleId = article.id,
                title = article.title,
                description = article.description,
                contentPreview = article.contentPreview,
                originalUrl = article.originalUrl,
                imageUrl = article.imageUrl,
                publishedAtEpochMillis = article.publishedAt?.toEpochMilli(),
                sourceName = article.sourceName,
                savedAtEpochMillis = 1_727_136_000_000,
            ),
            snapshot,
        )
        assertEquals(article, snapshot.toArticle())
    }
}
