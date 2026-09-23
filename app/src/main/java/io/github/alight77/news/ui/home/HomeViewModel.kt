package io.github.alight77.news.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.alight77.news.domain.model.NewsCategory
import io.github.alight77.news.domain.model.NewsPageResult
import io.github.alight77.news.domain.repository.NewsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: NewsRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeScreenState(NewsCategory.GENERAL, HomeUiState.Loading))
    val uiState = _uiState.asStateFlow()
    private val completedPages = mutableMapOf<NewsCategory, HomeUiState>()
    private var activeJob: Job? = null
    private var requestVersion = 0L

    init {
        loadFirstPage(NewsCategory.GENERAL)
    }

    fun selectCategory(category: NewsCategory) {
        if (category == _uiState.value.selectedCategory) return

        activeJob?.cancel()
        requestVersion++
        val completedPage = completedPages[category]
        _uiState.value = HomeScreenState(category, completedPage ?: HomeUiState.Loading)
        if (completedPage == null) loadFirstPage(category)
    }

    fun retry() {
        if (activeJob?.isActive == true) return
        loadFirstPage(_uiState.value.selectedCategory)
    }

    private fun loadFirstPage(category: NewsCategory) {
        val version = ++requestVersion
        _uiState.value = HomeScreenState(category, HomeUiState.Loading)
        activeJob = viewModelScope.launch {
            val newPageState = when (val result = repository.getHeadlines(category, page = 1)) {
                is NewsPageResult.Success -> {
                    val articles = result.page.articles
                    if (articles.isEmpty()) HomeUiState.Empty else HomeUiState.Content(articles)
                }
                is NewsPageResult.Failure -> HomeUiState.Error(result.error)
            }
            if (version != requestVersion || _uiState.value.selectedCategory != category) return@launch

            if (newPageState is HomeUiState.Content || newPageState == HomeUiState.Empty) {
                completedPages[category] = newPageState
            }
            _uiState.value = HomeScreenState(category, newPageState)
        }
    }
}
