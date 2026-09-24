package io.github.alight77.news.ui.home

import io.github.alight77.news.domain.model.Article
import io.github.alight77.news.domain.model.ArticlePage
import io.github.alight77.news.domain.model.NewsCategory
import io.github.alight77.news.domain.model.NewsError
import io.github.alight77.news.domain.model.NewsPageResult
import io.github.alight77.news.domain.repository.CachedHomeFirstPage
import io.github.alight77.news.domain.repository.HomeFirstPageCache
import io.github.alight77.news.domain.repository.NewsRepository
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelCacheTest {
    private val dispatcher = StandardTestDispatcher()
    private val now = 1_727_136_000_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `fresh disk cache is displayed without a first page request`() = runTest(dispatcher) {
        val cached = article("cached")
        val cache = FakeHomeFirstPageCache().apply {
            pages[NewsCategory.GENERAL] = CachedHomeFirstPage(
                articles = listOf(cached),
                fetchedAtEpochMillis = now - FRESHNESS_MILLIS + 1,
            )
        }
        val repository = FakeNewsRepository { _, _ -> error("Fresh cache must not request network") }

        val viewModel = HomeViewModel(repository, cache, currentTimeMillis = { now })
        runCurrent()

        assertEquals(
            HomeScreenState(NewsCategory.GENERAL, HomeUiState.Content(listOf(cached))),
            viewModel.uiState.value,
        )
        assertEquals(emptyList<Pair<NewsCategory, Int>>(), repository.requests)
    }

    @Test
    fun `expired disk cache remains visible when refresh fails`() = runTest(dispatcher) {
        val cached = article("cached")
        val cache = FakeHomeFirstPageCache().apply {
            pages[NewsCategory.GENERAL] = CachedHomeFirstPage(
                articles = listOf(cached),
                fetchedAtEpochMillis = now - FRESHNESS_MILLIS,
            )
        }
        val repository = FakeNewsRepository { _, _ -> NewsPageResult.Failure(NewsError.CONNECTION) }

        val viewModel = HomeViewModel(repository, cache, currentTimeMillis = { now })
        runCurrent()

        assertEquals(
            HomeScreenState(
                selectedCategory = NewsCategory.GENERAL,
                pageState = HomeUiState.Content(listOf(cached)),
                refreshError = NewsError.CONNECTION,
            ),
            viewModel.uiState.value,
        )
        assertEquals(listOf(NewsCategory.GENERAL to 1), repository.requests)
    }

    @Test
    fun `cached first page refreshes before an append can request page two`() = runTest(dispatcher) {
        val cached = article("cached")
        val refreshed = article("refreshed")
        val appended = article("appended")
        val cache = FakeHomeFirstPageCache().apply {
            pages[NewsCategory.GENERAL] = CachedHomeFirstPage(listOf(cached), now - 1)
        }
        val repository = FakeNewsRepository { _, page ->
            when (page) {
                1 -> NewsPageResult.Success(ArticlePage(listOf(refreshed), 10))
                2 -> NewsPageResult.Success(ArticlePage(listOf(appended), 1))
                else -> error("Unexpected page $page")
            }
        }
        val viewModel = HomeViewModel(repository, cache, currentTimeMillis = { now })
        runCurrent()

        viewModel.loadNextPage()
        runCurrent()
        assertEquals(listOf(NewsCategory.GENERAL to 1), repository.requests)
        assertEquals(HomeUiState.Content(listOf(refreshed)), viewModel.uiState.value.pageState)

        viewModel.loadNextPage()
        runCurrent()
        assertEquals(listOf(NewsCategory.GENERAL to 1, NewsCategory.GENERAL to 2), repository.requests)
        assertEquals(HomeUiState.Content(listOf(refreshed, appended)), viewModel.uiState.value.pageState)
    }

    @Test
    fun `cache write failure keeps a successful network result on screen`() = runTest(dispatcher) {
        val fresh = article("fresh")
        val cache = FakeHomeFirstPageCache().apply {
            replaceFailure = IOException("disk unavailable")
        }
        val repository = FakeNewsRepository { _, _ -> NewsPageResult.Success(ArticlePage(listOf(fresh), 1)) }

        val viewModel = HomeViewModel(repository, cache, currentTimeMillis = { now })
        runCurrent()

        assertEquals(HomeUiState.Content(listOf(fresh)), viewModel.uiState.value.pageState)
        assertEquals(NewsCategory.GENERAL, viewModel.uiState.value.selectedCategory)
        assertEquals(listOf(NewsCategory.GENERAL to 1), repository.requests)
    }

    @Test
    fun `late cache read cannot replace the newer selected category`() = runTest(dispatcher) {
        val delayedGeneral = CompletableDeferred<CachedHomeFirstPage?>()
        val technology = article("technology")
        val cache = FakeHomeFirstPageCache().apply {
            readBlock = { category ->
                if (category == NewsCategory.GENERAL) withContext(NonCancellable) { delayedGeneral.await() }
                else null
            }
        }
        val repository = FakeNewsRepository { category, _ ->
            if (category == NewsCategory.TECHNOLOGY) NewsPageResult.Success(ArticlePage(listOf(technology), 1))
            else error("General must wait for cache before requesting network")
        }
        val viewModel = HomeViewModel(repository, cache, currentTimeMillis = { now })
        runCurrent()

        viewModel.selectCategory(NewsCategory.TECHNOLOGY)
        runCurrent()
        delayedGeneral.complete(CachedHomeFirstPage(listOf(article("stale")), now))
        runCurrent()

        assertEquals(HomeUiState.Content(listOf(technology)), viewModel.uiState.value.pageState)
        assertEquals(NewsCategory.TECHNOLOGY, viewModel.uiState.value.selectedCategory)
        assertEquals(listOf(NewsCategory.TECHNOLOGY to 1), repository.requests)
    }

    private class FakeNewsRepository(
        private val response: suspend (NewsCategory, Int) -> NewsPageResult,
    ) : NewsRepository {
        val requests = mutableListOf<Pair<NewsCategory, Int>>()

        override suspend fun getHeadlines(category: NewsCategory, page: Int): NewsPageResult {
            requests += category to page
            return response(category, page)
        }
    }

    private class FakeHomeFirstPageCache : HomeFirstPageCache {
        val pages = mutableMapOf<NewsCategory, CachedHomeFirstPage>()
        var readBlock: suspend (NewsCategory) -> CachedHomeFirstPage? = { pages[it] }
        var replaceFailure: Throwable? = null

        override suspend fun read(category: NewsCategory): CachedHomeFirstPage? = readBlock(category)

        override suspend fun replace(
            category: NewsCategory,
            articles: List<Article>,
            fetchedAtEpochMillis: Long,
        ) {
            replaceFailure?.let { throw it }
            pages[category] = CachedHomeFirstPage(articles, fetchedAtEpochMillis)
        }
    }

    private fun article(id: String) = Article(id, id, null, null, null, null, null, null)

    private companion object {
        const val FRESHNESS_MILLIS = 2 * 60 * 60 * 1_000L
    }
}
