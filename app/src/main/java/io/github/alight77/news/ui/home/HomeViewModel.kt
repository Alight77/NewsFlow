package io.github.alight77.news.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.alight77.news.domain.model.Article
import io.github.alight77.news.domain.model.ArticlePage
import io.github.alight77.news.domain.model.NewsCategory
import io.github.alight77.news.domain.model.NewsError
import io.github.alight77.news.domain.model.NewsPageResult
import io.github.alight77.news.domain.repository.CachedHomeFirstPage
import io.github.alight77.news.domain.repository.HomeFirstPageCache
import io.github.alight77.news.domain.repository.NewsRepository
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HomeViewModel(
    private val repository: NewsRepository,
    private val homeFirstPageCache: HomeFirstPageCache = EmptyHomeFirstPageCache,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeScreenState(NewsCategory.GENERAL, HomeUiState.Loading))
    val uiState = _uiState.asStateFlow()
    private val _refreshSucceeded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val refreshSucceeded = _refreshSucceeded.asSharedFlow()
    private val completedPages = mutableMapOf<NewsCategory, CompletedPage>()
    private val cacheWriteMutex = Mutex()
    private var activeJob: Job? = null
    private var requestVersion = 0L

    init {
        restoreOrLoadFirstPage(NewsCategory.GENERAL)
    }

    fun selectCategory(category: NewsCategory) {
        if (category == _uiState.value.selectedCategory) return

        activeJob?.cancel()
        requestVersion++
        val completedPage = completedPages[category]
        if (completedPage == null) {
            restoreOrLoadFirstPage(category)
        } else {
            _uiState.value = completedPage.toScreenState(category)
            if (completedPage.isExpired(currentTimeMillis())) {
                startRefresh(category, completedPage, emitRefreshSucceeded = false)
            }
        }
    }

    fun retry() {
        if (activeJob?.isActive == true) return

        val category = _uiState.value.selectedCategory
        val completedPage = completedPages[category]
        if (completedPage == null) restoreFirstPageFromNetwork(category)
        else startRefresh(category, completedPage, emitRefreshSucceeded = true)
    }

    fun refresh() {
        val currentState = _uiState.value
        val category = currentState.selectedCategory
        val previousPage = completedPages[category] ?: return
        if (currentState.isRefreshing) return
        if (activeJob?.isActive == true) {
            if (currentState.appendState != HomeAppendState.Loading) return
            activeJob?.cancel()
        }
        startRefresh(category, previousPage, emitRefreshSucceeded = true)
    }

    fun loadNextPage() = appendNextPage(explicit = false)

    fun retryNextPage() = appendNextPage(explicit = true)

    private fun appendNextPage(explicit: Boolean) {
        val currentState = _uiState.value
        if (currentState.pageState !is HomeUiState.Content || currentState.isRefreshing || activeJob?.isActive == true) return

        val category = currentState.selectedCategory
        val previousPage = completedPages[category] ?: return
        if (!previousPage.hasNetworkFirstPage) {
            startRefresh(category, previousPage, emitRefreshSucceeded = false)
            return
        }
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
            if (!isCurrentRequest(version, category)) return@launch

            when (result) {
                is NewsPageResult.Success -> {
                    val oldArticles = (previousPage.pageState as HomeUiState.Content).articles
                    val articles = (oldArticles + result.page.articles).distinctBy(Article::id)
                    val nextPage = nextPageAfter(page, result.page.rawArticleCount)
                    val nextAppendState = when {
                        nextPage == null -> HomeAppendState.EndReached
                        articles.size == oldArticles.size -> HomeAppendState.ManualContinue
                        else -> HomeAppendState.Idle
                    }
                    val newPage = previousPage.copy(
                        pageState = HomeUiState.Content(articles),
                        nextPage = nextPage,
                        appendState = nextAppendState,
                    )
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

    private fun restoreOrLoadFirstPage(category: NewsCategory) {
        val version = ++requestVersion
        _uiState.value = HomeScreenState(category, HomeUiState.Loading)
        activeJob = viewModelScope.launch {
            val cachedPage = try {
                homeFirstPageCache.read(category)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (!isCurrentRequest(version, category)) return@launch

            if (cachedPage == null) {
                requestFirstPage(category, version, previousPage = null, emitRefreshSucceeded = false)
                return@launch
            }

            val restoredPage = cachedFirstPage(cachedPage)
            completedPages[category] = restoredPage
            _uiState.value = restoredPage.toScreenState(category)
            if (restoredPage.isExpired(currentTimeMillis())) {
                _uiState.value = restoredPage.toScreenState(category, isRefreshing = true)
                requestFirstPage(category, version, restoredPage, emitRefreshSucceeded = false)
            }
        }
    }

    private fun restoreFirstPageFromNetwork(category: NewsCategory) {
        val version = ++requestVersion
        _uiState.value = HomeScreenState(category, HomeUiState.Loading)
        activeJob = viewModelScope.launch {
            requestFirstPage(category, version, previousPage = null, emitRefreshSucceeded = false)
        }
    }

    private fun startRefresh(
        category: NewsCategory,
        previousPage: CompletedPage,
        emitRefreshSucceeded: Boolean,
    ) {
        val version = ++requestVersion
        _uiState.value = previousPage.toScreenState(category, isRefreshing = true)
        activeJob = viewModelScope.launch {
            requestFirstPage(category, version, previousPage, emitRefreshSucceeded)
        }
    }

    private suspend fun requestFirstPage(
        category: NewsCategory,
        version: Long,
        previousPage: CompletedPage?,
        emitRefreshSucceeded: Boolean,
    ) {
        val result = repository.getHeadlines(category, page = 1)
        if (!isCurrentRequest(version, category)) return

        when (result) {
            is NewsPageResult.Success -> {
                val fetchedAtEpochMillis = currentTimeMillis()
                val newPage = networkFirstPage(result.page, fetchedAtEpochMillis)
                completedPages[category] = newPage
                _uiState.value = newPage.toScreenState(category)
                if (emitRefreshSucceeded) _refreshSucceeded.tryEmit(Unit)
                persistFirstPage(category, result.page.articles, fetchedAtEpochMillis, version)
            }
            is NewsPageResult.Failure -> {
                if (previousPage == null) {
                    _uiState.value = HomeScreenState(category, HomeUiState.Error(result.error))
                } else {
                    completedPages[category] = previousPage
                    _uiState.value = previousPage.toScreenState(category, refreshError = result.error)
                }
            }
        }
    }

    private suspend fun persistFirstPage(
        category: NewsCategory,
        articles: List<Article>,
        fetchedAtEpochMillis: Long,
        version: Long,
    ) {
        cacheWriteMutex.withLock {
            if (!isCurrentRequest(version, category)) return
            try {
                homeFirstPageCache.replace(category, articles, fetchedAtEpochMillis)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A cache write must never replace an already successful network result with an error state.
            }
        }
    }

    private fun networkFirstPage(page: ArticlePage, fetchedAtEpochMillis: Long): CompletedPage {
        if (page.articles.isEmpty()) {
            return CompletedPage(HomeUiState.Empty, null, HomeAppendState.Idle, fetchedAtEpochMillis, true)
        }
        val nextPage = nextPageAfter(1, page.rawArticleCount)
        return CompletedPage(
            pageState = HomeUiState.Content(page.articles),
            nextPage = nextPage,
            appendState = if (nextPage == null) HomeAppendState.EndReached else HomeAppendState.Idle,
            fetchedAtEpochMillis = fetchedAtEpochMillis,
            hasNetworkFirstPage = true,
        )
    }

    private fun cachedFirstPage(cachedPage: CachedHomeFirstPage): CompletedPage = CompletedPage(
        pageState = if (cachedPage.articles.isEmpty()) HomeUiState.Empty else HomeUiState.Content(cachedPage.articles),
        nextPage = null,
        appendState = HomeAppendState.Idle,
        fetchedAtEpochMillis = cachedPage.fetchedAtEpochMillis,
        hasNetworkFirstPage = false,
    )

    private fun isCurrentRequest(version: Long, category: NewsCategory): Boolean =
        version == requestVersion && _uiState.value.selectedCategory == category

    private fun nextPageAfter(page: Int, rawCount: Int): Int? =
        if (rawCount < PAGE_SIZE || page * PAGE_SIZE >= MAX_ACCESSIBLE_ARTICLES) null else page + 1

    private data class CompletedPage(
        val pageState: HomeUiState,
        val nextPage: Int?,
        val appendState: HomeAppendState,
        val fetchedAtEpochMillis: Long,
        val hasNetworkFirstPage: Boolean,
    ) {
        fun isExpired(now: Long): Boolean = now - fetchedAtEpochMillis >= FRESHNESS_MILLIS

        fun toScreenState(
            category: NewsCategory,
            isRefreshing: Boolean = false,
            refreshError: NewsError? = null,
        ) = HomeScreenState(category, pageState, isRefreshing, refreshError, appendState)
    }

    private companion object {
        const val PAGE_SIZE = 10
        const val MAX_ACCESSIBLE_ARTICLES = 1000
        const val FRESHNESS_MILLIS = 2 * 60 * 60 * 1_000L
    }
}

private object EmptyHomeFirstPageCache : HomeFirstPageCache {
    override suspend fun read(category: NewsCategory): CachedHomeFirstPage? = null

    override suspend fun replace(
        category: NewsCategory,
        articles: List<Article>,
        fetchedAtEpochMillis: Long,
    ) = Unit
}
