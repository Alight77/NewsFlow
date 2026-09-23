package io.github.alight77.news.ui.home

import io.github.alight77.news.domain.model.Article
import io.github.alight77.news.domain.model.NewsCategory
import io.github.alight77.news.domain.model.NewsError

data class HomeScreenState(
    val selectedCategory: NewsCategory,
    val pageState: HomeUiState,
    val isRefreshing: Boolean = false,
    val refreshError: NewsError? = null,
)

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Content(val articles: List<Article>) : HomeUiState
    data object Empty : HomeUiState
    data class Error(val error: NewsError) : HomeUiState
}
