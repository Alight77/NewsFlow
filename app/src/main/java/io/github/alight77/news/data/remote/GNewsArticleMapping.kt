package io.github.alight77.news.data.remote

import io.github.alight77.news.domain.model.Article
import io.github.alight77.news.domain.model.ArticlePage
import io.github.alight77.news.domain.model.InvalidNewsDataException
import java.time.Instant
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun GNewsResponseDto.toArticlePage(): ArticlePage {
    val rawArticles = articles ?: throw InvalidNewsDataException()
    val mappedArticles = rawArticles.mapNotNull(GNewsArticleDto::toArticleOrNull)
    if (rawArticles.isNotEmpty() && mappedArticles.isEmpty()) {
        throw InvalidNewsDataException()
    }
    return ArticlePage(mappedArticles, rawArticles.size)
}

private fun GNewsArticleDto.toArticleOrNull(): Article? {
    val articleId = id.trimToNull() ?: return null
    val articleTitle = title.trimToNull() ?: return null
    return Article(
        id = articleId,
        title = articleTitle,
        description = description.trimToNull(),
        contentPreview = content.trimToNull(),
        originalUrl = url.toValidHttpUrlOrNull(),
        imageUrl = image.toValidHttpUrlOrNull(),
        publishedAt = publishedAt.trimToNull()?.let { runCatching { Instant.parse(it) }.getOrNull() },
        sourceName = source?.name.trimToNull(),
    )
}

private fun String?.trimToNull(): String? = this?.trim()?.takeIf(String::isNotEmpty)

private fun String?.toValidHttpUrlOrNull(): String? =
    trimToNull()?.takeIf { it.toHttpUrlOrNull() != null }
