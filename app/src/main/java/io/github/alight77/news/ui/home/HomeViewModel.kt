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
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init {
        loadFirstPage()
    }

    fun retry() = loadFirstPage()

    private fun loadFirstPage() {
        if (loadJob?.isActive == true) return
        _uiState.value = HomeUiState.Loading
        loadJob = viewModelScope.launch {
            _uiState.value = when (val result = repository.getHeadlines(NewsCategory.GENERAL, page = 1)) {
                is NewsPageResult.Success -> {
                    val articles = result.page.articles
                    if (articles.isEmpty()) HomeUiState.Empty else HomeUiState.Content(articles)
                }
                is NewsPageResult.Failure -> HomeUiState.Error(result.error)
            }
        }
    }
}
