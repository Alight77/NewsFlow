package io.github.alight77.news.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.alight77.news.domain.model.ArticlePage
import io.github.alight77.news.domain.model.NewsCategory
import io.github.alight77.news.domain.model.NewsPageResult
import io.github.alight77.news.domain.repository.NewsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: NewsRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeScreenState(NewsCategory.GENERAL, HomeUiState.Loading))
    val uiState = _uiState.asStateFlow()
    private val _refreshSucceeded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val refreshSucceeded = _refreshSucceeded.asSharedFlow()
    private val completedPages = mutableMapOf<NewsCategory, CompletedPage>()
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
        _uiState.value = completedPage?.toScreenState(category)
            ?: HomeScreenState(category, HomeUiState.Loading)
        if (completedPage == null) loadFirstPage(category)
    }

    fun retry() {
        if (activeJob?.isActive == true) return
        loadFirstPage(_uiState.value.selectedCategory)
    }

    fun refresh() {
        val currentState = _uiState.value
        if (currentState.pageState !is HomeUiState.Content || currentState.isRefreshing) return

        val category = currentState.selectedCategory
        val previousPage = completedPages[category] ?: return
        if (activeJob?.isActive == true) {
            if (currentState.appendState != HomeAppendState.Loading) return
            activeJob?.cancel()
        }
        val version = ++requestVersion
        _uiState.value = currentState.copy(
            isRefreshing = true,
            refreshError = null,
            appendState = HomeAppendState.Idle,
        )
        activeJob = viewModelScope.launch {
            val result = repository.getHeadlines(category, page = 1)
            if (version != requestVersion || _uiState.value.selectedCategory != category) return@launch

            when (result) {
                is NewsPageResult.Success -> {
                    val newPage = firstPage(result.page)
                    completedPages[category] = newPage
                    _uiState.value = newPage.toScreenState(category)
                    _refreshSucceeded.tryEmit(Unit)
                }
                is NewsPageResult.Failure -> _uiState.value = currentState.copy(
                    isRefreshing = false,
                    refreshError = result.error,
                    appendState = previousPage.appendState,
                )
            }
        }
    }

    fun loadNextPage() = appendNextPage(explicit = false)

    fun retryNextPage() = appendNextPage(explicit = true)

    private fun appendNextPage(explicit: Boolean) {
        val currentState = _uiState.value
        if (currentState.pageState !is HomeUiState.Content || currentState.isRefreshing || activeJob?.isActive == true) return

        val category = currentState.selectedCategory
        val previousPage = completedPages[category] ?: return
        val page = previousPage.nextPage ?: return
        val appendState = currentState.appendState
        val allowed = if (explicit) {
            appendState is HomeAppendState.Error || appendState == HomeAppendState.ManualContinue
        } else {
            appendState == HomeAppendState.Idle
        }
        if (!allowed) return

        val version = ++requestVersion
        _uiState.value = currentState.copy(appendState = HomeAppendState.Loading)
        activeJob = viewModelScope.launch {
            val result = repository.getHeadlines(category, page)
            if (version != requestVersion || _uiState.value.selectedCategory != category) return@launch

            when (result) {
                is NewsPageResult.Success -> {
                    val oldArticles = (previousPage.pageState as HomeUiState.Content).articles
                    val articles = (oldArticles + result.page.articles).distinctBy { it.id }
                    val nextPage = nextPageAfter(page, result.page.rawArticleCount)
                    val nextAppendState = when {
                        nextPage == null -> HomeAppendState.EndReached
                        articles.size == oldArticles.size -> HomeAppendState.ManualContinue
                        else -> HomeAppendState.Idle
                    }
                    val newPage = CompletedPage(HomeUiState.Content(articles), nextPage, nextAppendState)
                    completedPages[category] = newPage
                    _uiState.value = newPage.toScreenState(category)
                }
                is NewsPageResult.Failure -> {
                    val failedPage = previousPage.copy(appendState = HomeAppendState.Error(result.error))
                    completedPages[category] = failedPage
                    _uiState.value = failedPage.toScreenState(category)
                }
            }
        }
    }

    private fun loadFirstPage(category: NewsCategory) {
        val version = ++requestVersion
        _uiState.value = HomeScreenState(category, HomeUiState.Loading)
        activeJob = viewModelScope.launch {
            val result = repository.getHeadlines(category, page = 1)
            if (version != requestVersion || _uiState.value.selectedCategory != category) return@launch

            when (result) {
                is NewsPageResult.Success -> {
                    val newPage = firstPage(result.page)
                    completedPages[category] = newPage
                    _uiState.value = newPage.toScreenState(category)
                }
                is NewsPageResult.Failure -> _uiState.value = HomeScreenState(category, HomeUiState.Error(result.error))
            }
        }
    }

    private fun firstPage(page: ArticlePage): CompletedPage {
        if (page.articles.isEmpty()) return CompletedPage(HomeUiState.Empty, null, HomeAppendState.Idle)
        val nextPage = nextPageAfter(1, page.rawArticleCount)
        return CompletedPage(
            HomeUiState.Content(page.articles),
            nextPage,
            if (nextPage == null) HomeAppendState.EndReached else HomeAppendState.Idle,
        )
    }

    private fun nextPageAfter(page: Int, rawCount: Int): Int? =
        if (rawCount < PAGE_SIZE || page * PAGE_SIZE >= MAX_ACCESSIBLE_ARTICLES) null else page + 1

    private data class CompletedPage(
        val pageState: HomeUiState,
        val nextPage: Int?,
        val appendState: HomeAppendState,
    ) {
        fun toScreenState(category: NewsCategory) = HomeScreenState(category, pageState, appendState = appendState)
    }

    private companion object {
        const val PAGE_SIZE = 10
        const val MAX_ACCESSIBLE_ARTICLES = 1000
    }
}
