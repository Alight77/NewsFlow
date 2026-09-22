package io.github.alight77.news.domain.repository

import io.github.alight77.news.domain.model.NewsCategory
import io.github.alight77.news.domain.model.NewsPageResult

interface NewsRepository {
    suspend fun getHeadlines(category: NewsCategory, page: Int): NewsPageResult
}
