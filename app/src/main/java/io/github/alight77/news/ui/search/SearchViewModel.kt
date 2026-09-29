package io.github.alight77.news.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.alight77.news.domain.model.Article
import io.github.alight77.news.domain.model.NewsPageResult
import io.github.alight77.news.domain.repository.NewsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(
    private val repository: NewsRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val restoredInput = savedStateHandle.get<String>(SEARCH_INPUT_KEY).orEmpty()
    private val _uiState = MutableStateFlow(
        SearchScreenState(input = restoredInput, normalizedQuery = restoredInput.trim()),
    )
    val uiState = _uiState.asStateFlow()
    private var activeJob: Job? = null
    private var requestVersion = 0L
    private var nextPage: Int? = null

    fun onSearchVisible() {
        val currentState = _uiState.value
        if (currentState.normalizedQuery.isEmpty() || currentState.resultState != SearchUiState.Initial) return

        val version = ++requestVersion
        _uiState.value = currentState.copy(resultState = SearchUiState.Loading)
        activeJob = viewModelScope.launch {
            searchFirstPage(currentState.normalizedQuery, version)
        }
    }

    fun updateQuery(input: String) {
        val normalizedQuery = input.trim()
        val currentState = _uiState.value
        if (normalizedQuery == currentState.normalizedQuery) {
            if (input != currentState.input) {
                savedStateHandle[SEARCH_INPUT_KEY] = input
                _uiState.value = currentState.copy(input = input)
            }
            return
        }

        savedStateHandle[SEARCH_INPUT_KEY] = input
        activeJob?.cancel()
        val version = ++requestVersion
        nextPage = null
        if (normalizedQuery.isEmpty()) {
            _uiState.value = SearchScreenState(input = input)
            return
        }

        _uiState.value = SearchScreenState(input, normalizedQuery, SearchUiState.Loading)
        activeJob = viewModelScope.launch {
            delay(AUTOMATIC_SEARCH_DEBOUNCE_MILLIS)
            searchFirstPage(normalizedQuery, version)
        }
    }

    fun retry() {
        val currentState = _uiState.value
        if (currentState.resultState !is SearchUiState.Error || currentState.normalizedQuery.isEmpty()) return

        activeJob?.cancel()
        val version = ++requestVersion
        nextPage = null
        _uiState.value = currentState.copy(resultState = SearchUiState.Loading)
        activeJob = viewModelScope.launch {
            searchFirstPage(currentState.normalizedQuery, version)
        }
    }

    fun loadNextPage() = appendNextPage(explicit = false)

    fun retryNextPage() = appendNextPage(explicit = true)

    private fun appendNextPage(explicit: Boolean) {
        val currentState = _uiState.value
        val content = currentState.resultState as? SearchUiState.Content ?: return
        if (activeJob?.isActive == true) return
        val page = nextPage ?: return
        val allowed = if (explicit) {
            currentState.appendState is SearchAppendState.Error || currentState.appendState == SearchAppendState.ManualContinue
        } else {
            currentState.appendState == SearchAppendState.Idle
        }
        if (!allowed) return

        val query = currentState.normalizedQuery
        val version = ++requestVersion
        _uiState.value = currentState.copy(appendState = SearchAppendState.Loading)
        activeJob = viewModelScope.launch {
            when (val result = repository.search(query, page)) {
                is NewsPageResult.Success -> {
                    if (!isCurrent(query, version)) return@launch
                    val articles = (content.articles + result.page.articles).distinctBy(Article::id)
                    nextPage = nextPageAfter(page, result.page.rawArticleCount)
                    val appendState = when {
                        nextPage == null -> SearchAppendState.EndReached
                        articles.size == content.articles.size -> SearchAppendState.ManualContinue
                        else -> SearchAppendState.Idle
                    }
                    _uiState.value = _uiState.value.copy(
                        resultState = SearchUiState.Content(articles),
                        appendState = appendState,
                    )
                }
                is NewsPageResult.Failure -> {
                    if (!isCurrent(query, version)) return@launch
                    _uiState.value = _uiState.value.copy(appendState = SearchAppendState.Error(result.error))
                }
            }
        }
    }

    private suspend fun searchFirstPage(query: String, version: Long) {
        when (val result = repository.search(query, page = 1)) {
            is NewsPageResult.Success -> {
                if (!isCurrent(query, version)) return
                nextPage = if (result.page.articles.isEmpty()) null else nextPageAfter(1, result.page.rawArticleCount)
                _uiState.value = _uiState.value.copy(
                    resultState = if (result.page.articles.isEmpty()) SearchUiState.Empty
                    else SearchUiState.Content(result.page.articles),
                    appendState = if (nextPage == null && result.page.articles.isNotEmpty()) SearchAppendState.EndReached
                    else SearchAppendState.Idle,
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

    private fun nextPageAfter(page: Int, rawCount: Int): Int? =
        if (rawCount < PAGE_SIZE || page * PAGE_SIZE >= MAX_ACCESSIBLE_ARTICLES) null else page + 1

    private companion object {
        const val SEARCH_INPUT_KEY = "search_input"
        const val AUTOMATIC_SEARCH_DEBOUNCE_MILLIS = 400L
        const val PAGE_SIZE = 10
        const val MAX_ACCESSIBLE_ARTICLES = 1000
    }
}
