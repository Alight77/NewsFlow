package io.github.alight77.news.ui.search

import io.github.alight77.news.domain.model.Article
import io.github.alight77.news.domain.model.NewsError

data class SearchScreenState(
    val input: String = "",
    val normalizedQuery: String = "",
    val resultState: SearchUiState = SearchUiState.Initial,
    val appendState: SearchAppendState = SearchAppendState.Idle,
)

sealed interface SearchAppendState {
    data object Idle : SearchAppendState
    data object Loading : SearchAppendState
    data class Error(val error: NewsError) : SearchAppendState
    data object ManualContinue : SearchAppendState
    data object EndReached : SearchAppendState
}

sealed interface SearchUiState {
    data object Initial : SearchUiState
    data object Loading : SearchUiState
    data class Content(val articles: List<Article>) : SearchUiState
    data object Empty : SearchUiState
    data class Error(val error: NewsError) : SearchUiState
}
