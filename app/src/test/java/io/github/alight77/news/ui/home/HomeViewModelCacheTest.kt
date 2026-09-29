package io.github.alight77.news.ui.home

import androidx.lifecycle.SavedStateHandle
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

        val viewModel = HomeViewModel(repository, cache, currentTimeMillis = { now }, savedStateHandle = SavedStateHandle())
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

        val viewModel = HomeViewModel(repository, cache, currentTimeMillis = { now }, savedStateHandle = SavedStateHandle())
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
    fun `initial Home visibility does not repeat a failed stale empty cache refresh`() = runTest(dispatcher) {
        val cache = FakeHomeFirstPageCache().apply {
            pages[NewsCategory.GENERAL] = CachedHomeFirstPage(
                articles = emptyList(),
                fetchedAtEpochMillis = now - FRESHNESS_MILLIS,
            )
        }
        val repository = FakeNewsRepository { _, _ -> NewsPageResult.Failure(NewsError.CONNECTION) }
        val viewModel = HomeViewModel(repository, cache, currentTimeMillis = { now }, savedStateHandle = SavedStateHandle())
        runCurrent()

        viewModel.onHomeVisibilityChanged(isVisible = true)
        runCurrent()

        assertEquals(
            HomeScreenState(
                selectedCategory = NewsCategory.GENERAL,
                pageState = HomeUiState.Empty,
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
        val viewModel = HomeViewModel(repository, cache, currentTimeMillis = { now }, savedStateHandle = SavedStateHandle())
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

        val viewModel = HomeViewModel(repository, cache, currentTimeMillis = { now }, savedStateHandle = SavedStateHandle())
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
        val viewModel = HomeViewModel(repository, cache, currentTimeMillis = { now }, savedStateHandle = SavedStateHandle())
        runCurrent()

        viewModel.selectCategory(NewsCategory.TECHNOLOGY)
        runCurrent()
        delayedGeneral.complete(CachedHomeFirstPage(listOf(article("stale")), now))
        runCurrent()

        assertEquals(HomeUiState.Content(listOf(technology)), viewModel.uiState.value.pageState)
        assertEquals(NewsCategory.TECHNOLOGY, viewModel.uiState.value.selectedCategory)
        assertEquals(listOf(NewsCategory.TECHNOLOGY to 1), repository.requests)
    }

    @Test
    fun `returning to Home refreshes expired memory once without reading disk`() = runTest(dispatcher) {
        var currentTime = now
        val old = article("old")
        val fresh = article("fresh")
        var firstPageRequests = 0
        val cache = FakeHomeFirstPageCache()
        val repository = FakeNewsRepository { _, page ->
            check(page == 1)
            if (++firstPageRequests == 1) NewsPageResult.Success(ArticlePage(listOf(old), 10))
            else NewsPageResult.Success(ArticlePage(listOf(fresh), 1))
        }
        val viewModel = HomeViewModel(repository, cache, currentTimeMillis = { currentTime }, savedStateHandle = SavedStateHandle())
        runCurrent()

        viewModel.onHomeVisibilityChanged(isVisible = true)
        viewModel.onHomeVisibilityChanged(isVisible = false)
        currentTime += FRESHNESS_MILLIS
        viewModel.onHomeVisibilityChanged(isVisible = true)
        runCurrent()
        viewModel.onHomeVisibilityChanged(isVisible = true)
        runCurrent()

        assertEquals(HomeUiState.Content(listOf(fresh)), viewModel.uiState.value.pageState)
        assertEquals(listOf(NewsCategory.GENERAL to 1, NewsCategory.GENERAL to 1), repository.requests)
        assertEquals(listOf(NewsCategory.GENERAL), cache.readCategories)
    }

    @Test
    fun `fresh memory is retained while an explicit refresh still requests network`() = runTest(dispatcher) {
        val cached = article("cached")
        val refreshed = article("refreshed")
        val cache = FakeHomeFirstPageCache().apply {
            pages[NewsCategory.GENERAL] = CachedHomeFirstPage(listOf(cached), now)
        }
        val repository = FakeNewsRepository { _, _ -> NewsPageResult.Success(ArticlePage(listOf(refreshed), 1)) }
        val viewModel = HomeViewModel(repository, cache, currentTimeMillis = { now }, savedStateHandle = SavedStateHandle())
        runCurrent()

        viewModel.onHomeVisibilityChanged(isVisible = true)
        viewModel.onHomeVisibilityChanged(isVisible = false)
        viewModel.onHomeVisibilityChanged(isVisible = true)
        runCurrent()
        assertEquals(emptyList<Pair<NewsCategory, Int>>(), repository.requests)

        viewModel.refresh()
        runCurrent()
        assertEquals(HomeUiState.Content(listOf(refreshed)), viewModel.uiState.value.pageState)
        assertEquals(listOf(NewsCategory.GENERAL to 1), repository.requests)
    }

    @Test
    fun `returning to a multi page category keeps memory and does not reread cache`() = runTest(dispatcher) {
        val first = article("first")
        val second = article("second")
        val technology = article("technology")
        val cache = FakeHomeFirstPageCache()
        val repository = FakeNewsRepository { category, page ->
            when (category to page) {
                NewsCategory.GENERAL to 1 -> NewsPageResult.Success(ArticlePage(listOf(first), 10))
                NewsCategory.GENERAL to 2 -> NewsPageResult.Success(ArticlePage(listOf(second), 1))
                NewsCategory.TECHNOLOGY to 1 -> NewsPageResult.Success(ArticlePage(listOf(technology), 1))
                else -> error("Unexpected request: $category/$page")
            }
        }
        val viewModel = HomeViewModel(repository, cache, currentTimeMillis = { now }, savedStateHandle = SavedStateHandle())
        runCurrent()
        viewModel.loadNextPage()
        runCurrent()

        viewModel.selectCategory(NewsCategory.TECHNOLOGY)
        runCurrent()
        viewModel.selectCategory(NewsCategory.GENERAL)
        runCurrent()

        assertEquals(HomeUiState.Content(listOf(first, second)), viewModel.uiState.value.pageState)
        assertEquals(listOf(NewsCategory.GENERAL, NewsCategory.TECHNOLOGY), cache.readCategories)
    }

    @Test
    fun `late same category cache write cannot win over a newer first page`() = runTest(dispatcher) {
        var currentTime = now
        val old = article("old")
        val fresh = article("fresh")
        val technology = article("technology")
        val firstWriteStarted = CompletableDeferred<Unit>()
        val releaseFirstWrite = CompletableDeferred<Unit>()
        var generalFirstPageRequests = 0
        var generalWrites = 0
        val cache = FakeHomeFirstPageCache().apply {
            replaceBlock = { category, articles, fetchedAtEpochMillis ->
                if (category == NewsCategory.GENERAL && generalWrites++ == 0) {
                    firstWriteStarted.complete(Unit)
                    withContext(NonCancellable) { releaseFirstWrite.await() }
                }
                pages[category] = CachedHomeFirstPage(articles, fetchedAtEpochMillis)
            }
        }
        val repository = FakeNewsRepository { category, page ->
            when (category to page) {
                NewsCategory.GENERAL to 1 -> {
                    if (++generalFirstPageRequests == 1) NewsPageResult.Success(ArticlePage(listOf(old), 1))
                    else NewsPageResult.Success(ArticlePage(listOf(fresh), 1))
                }
                NewsCategory.TECHNOLOGY to 1 -> NewsPageResult.Success(ArticlePage(listOf(technology), 1))
                else -> error("Unexpected request: $category/$page")
            }
        }
        val viewModel = HomeViewModel(repository, cache, currentTimeMillis = { currentTime }, savedStateHandle = SavedStateHandle())
        runCurrent()
        firstWriteStarted.await()

        viewModel.selectCategory(NewsCategory.TECHNOLOGY)
        runCurrent()
        currentTime += FRESHNESS_MILLIS
        viewModel.selectCategory(NewsCategory.GENERAL)
        runCurrent()
        releaseFirstWrite.complete(Unit)
        runCurrent()

        assertEquals(CachedHomeFirstPage(listOf(fresh), currentTime), cache.pages[NewsCategory.GENERAL])
    }

    @Test
    fun `failed refresh after a successful empty result does not restore prior articles`() = runTest(dispatcher) {
        var currentTime = now
        val original = article("original")
        val cache = FakeHomeFirstPageCache()
        var firstPageRequests = 0
        val repository = FakeNewsRepository { _, page ->
            check(page == 1)
            when (++firstPageRequests) {
                1 -> NewsPageResult.Success(ArticlePage(listOf(original), 1))
                2 -> NewsPageResult.Success(ArticlePage(emptyList(), 0))
                3 -> NewsPageResult.Failure(NewsError.CONNECTION)
                else -> error("Unexpected first page request")
            }
        }
        val viewModel = HomeViewModel(repository, cache, currentTimeMillis = { currentTime }, savedStateHandle = SavedStateHandle())
        runCurrent()

        viewModel.refresh()
        runCurrent()
        assertEquals(HomeUiState.Empty, viewModel.uiState.value.pageState)
        assertEquals(CachedHomeFirstPage(emptyList(), now), cache.pages[NewsCategory.GENERAL])

        viewModel.onHomeVisibilityChanged(isVisible = true)
        viewModel.onHomeVisibilityChanged(isVisible = false)
        currentTime += FRESHNESS_MILLIS
        viewModel.onHomeVisibilityChanged(isVisible = true)
        runCurrent()

        assertEquals(
            HomeScreenState(
                selectedCategory = NewsCategory.GENERAL,
                pageState = HomeUiState.Empty,
                refreshError = NewsError.CONNECTION,
            ),
            viewModel.uiState.value,
        )
        assertEquals(
            listOf(
                NewsCategory.GENERAL to 1,
                NewsCategory.GENERAL to 1,
                NewsCategory.GENERAL to 1,
            ),
            repository.requests,
        )
    }

    @Test
    fun `retry after an expired empty cache failure requests network and replaces cache`() = runTest(dispatcher) {
        val fresh = article("fresh")
        val cache = FakeHomeFirstPageCache().apply {
            pages[NewsCategory.GENERAL] = CachedHomeFirstPage(
                articles = emptyList(),
                fetchedAtEpochMillis = now - FRESHNESS_MILLIS,
            )
        }
        var firstPageRequests = 0
        val repository = FakeNewsRepository { _, page ->
            check(page == 1)
            if (++firstPageRequests == 1) NewsPageResult.Failure(NewsError.CONNECTION)
            else NewsPageResult.Success(ArticlePage(listOf(fresh), 1))
        }
        val viewModel = HomeViewModel(repository, cache, currentTimeMillis = { now }, savedStateHandle = SavedStateHandle())
        runCurrent()

        assertEquals(
            HomeScreenState(
                selectedCategory = NewsCategory.GENERAL,
                pageState = HomeUiState.Empty,
                refreshError = NewsError.CONNECTION,
            ),
            viewModel.uiState.value,
        )

        viewModel.retry()
        runCurrent()

        assertEquals(HomeUiState.Content(listOf(fresh)), viewModel.uiState.value.pageState)
        assertEquals(CachedHomeFirstPage(listOf(fresh), now), cache.pages[NewsCategory.GENERAL])
        assertEquals(
            listOf(NewsCategory.GENERAL to 1, NewsCategory.GENERAL to 1),
            repository.requests,
        )
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
        val readCategories = mutableListOf<NewsCategory>()
        var readBlock: suspend (NewsCategory) -> CachedHomeFirstPage? = { pages[it] }
        var replaceFailure: Throwable? = null
        var replaceBlock: suspend (NewsCategory, List<Article>, Long) -> Unit = { category, articles, fetchedAtEpochMillis ->
            pages[category] = CachedHomeFirstPage(articles, fetchedAtEpochMillis)
        }

        override suspend fun read(category: NewsCategory): CachedHomeFirstPage? {
            readCategories += category
            return readBlock(category)
        }

        override suspend fun replace(
            category: NewsCategory,
            articles: List<Article>,
            fetchedAtEpochMillis: Long,
        ) {
            replaceFailure?.let { throw it }
            replaceBlock(category, articles, fetchedAtEpochMillis)
        }
    }

    private fun article(id: String) = Article(id, id, null, null, null, null, null, null)

    private companion object {
        const val FRESHNESS_MILLIS = 2 * 60 * 60 * 1_000L
    }
}
