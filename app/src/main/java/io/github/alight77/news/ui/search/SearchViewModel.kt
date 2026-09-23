package io.github.alight77.news.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.alight77.news.domain.model.NewsPageResult
import io.github.alight77.news.domain.repository.NewsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(private val repository: NewsRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchScreenState())
    val uiState = _uiState.asStateFlow()
    private var activeJob: Job? = null
    private var requestVersion = 0L

    fun updateQuery(input: String) {
        val normalizedQuery = input.trim()
        val currentState = _uiState.value
        if (normalizedQuery == currentState.normalizedQuery) {
            if (input != currentState.input) _uiState.value = currentState.copy(input = input)
            return
        }

        activeJob?.cancel()
        val version = ++requestVersion
        if (normalizedQuery.isEmpty()) {
            _uiState.value = SearchScreenState(input = input)
            return
        }

        _uiState.value = SearchScreenState(input, normalizedQuery, SearchUiState.Loading)
        activeJob = viewModelScope.launch {
            delay(AUTOMATIC_SEARCH_DEBOUNCE_MILLIS)
            search(normalizedQuery, version)
        }
    }

    fun retry() {
        val currentState = _uiState.value
        if (currentState.resultState !is SearchUiState.Error || currentState.normalizedQuery.isEmpty()) return

        activeJob?.cancel()
        val version = ++requestVersion
        _uiState.value = currentState.copy(resultState = SearchUiState.Loading)
        activeJob = viewModelScope.launch {
            search(currentState.normalizedQuery, version)
        }
    }

    private suspend fun search(query: String, version: Long) {
        when (val result = repository.search(query)) {
            is NewsPageResult.Success -> {
                if (!isCurrent(query, version)) return
                _uiState.value = _uiState.value.copy(
                    resultState = if (result.page.articles.isEmpty()) SearchUiState.Empty
                    else SearchUiState.Content(result.page.articles),
                )
            }
            is NewsPageResult.Failure -> {
                if (!isCurrent(query, version)) return
                _uiState.value = _uiState.value.copy(resultState = SearchUiState.Error(result.error))
            }
        }
    }

    private fun isCurrent(query: String, version: Long): Boolean =
        version == requestVersion && _uiState.value.normalizedQuery == query

    private companion object {
        const val AUTOMATIC_SEARCH_DEBOUNCE_MILLIS = 400L
    }
}
