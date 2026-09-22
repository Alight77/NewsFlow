package io.github.alight77.news.domain.model

sealed interface NewsPageResult {
    data class Success(val page: ArticlePage) : NewsPageResult
    data class Failure(val error: NewsError) : NewsPageResult
}
