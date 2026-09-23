package io.github.alight77.news.ui.search

import io.github.alight77.news.domain.model.Article
import io.github.alight77.news.domain.model.NewsError

data class SearchScreenState(
    val input: String = "",
    val normalizedQuery: String = "",
    val resultState: SearchUiState = SearchUiState.Initial,
)

sealed interface SearchUiState {
    data object Initial : SearchUiState
    data object Loading : SearchUiState
    data class Content(val articles: List<Article>) : SearchUiState
    data object Empty : SearchUiState
    data class Error(val error: NewsError) : SearchUiState
}
