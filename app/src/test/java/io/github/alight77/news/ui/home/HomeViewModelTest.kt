package io.github.alight77.news.ui.home

import io.github.alight77.news.domain.model.Article
import io.github.alight77.news.domain.model.ArticlePage
import io.github.alight77.news.domain.model.NewsCategory
import io.github.alight77.news.domain.model.NewsError
import io.github.alight77.news.domain.model.NewsPageResult
import io.github.alight77.news.domain.repository.NewsRepository
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial load uses general first page and moves from loading to content`() = runTest(dispatcher) {
        val article = Article(
            id = "news-1",
            title = "Headline",
            description = null,
            contentPreview = null,
            originalUrl = null,
            imageUrl = null,
            publishedAt = null,
            sourceName = null,
        )
        val fake = FakeNewsRepository { _, _ -> NewsPageResult.Success(ArticlePage(listOf(article), 1)) }
        val viewModel = HomeViewModel(fake)

        assertEquals(HomeScreenState(NewsCategory.GENERAL, HomeUiState.Loading), viewModel.uiState.value)
        runCurrent()

        assertEquals(HomeScreenState(NewsCategory.GENERAL, HomeUiState.Content(listOf(article))), viewModel.uiState.value)
        assertEquals(listOf(NewsCategory.GENERAL to 1), fake.requests)
    }

    @Test
    fun `an empty first page moves to empty state`() = runTest(dispatcher) {
        val fake = FakeNewsRepository { _, _ -> NewsPageResult.Success(ArticlePage(emptyList(), 0)) }
        val viewModel = HomeViewModel(fake)

        runCurrent()

        assertEquals(HomeScreenState(NewsCategory.GENERAL, HomeUiState.Empty), viewModel.uiState.value)
    }

    @Test
    fun `a failed first page exposes only a business error`() = runTest(dispatcher) {
        val fake = FakeNewsRepository { _, _ -> NewsPageResult.Failure(NewsError.RATE_LIMITED) }
        val viewModel = HomeViewModel(fake)

        runCurrent()

        assertEquals(HomeScreenState(NewsCategory.GENERAL, HomeUiState.Error(NewsError.RATE_LIMITED)), viewModel.uiState.value)
    }

    @Test
    fun `retry repeats the failed first page and recovers to content`() = runTest(dispatcher) {
        val article = Article("news-1", "Headline", null, null, null, null, null, null)
        var attempt = 0
        val fake = FakeNewsRepository { _, _ ->
            if (++attempt == 1) NewsPageResult.Failure(NewsError.CONNECTION)
            else NewsPageResult.Success(ArticlePage(listOf(article), 1))
        }
        val viewModel = HomeViewModel(fake)
        runCurrent()
        assertEquals(HomeScreenState(NewsCategory.GENERAL, HomeUiState.Error(NewsError.CONNECTION)), viewModel.uiState.value)

        viewModel.retry()
        assertEquals(HomeScreenState(NewsCategory.GENERAL, HomeUiState.Loading), viewModel.uiState.value)
        runCurrent()

        assertEquals(HomeScreenState(NewsCategory.GENERAL, HomeUiState.Content(listOf(article))), viewModel.uiState.value)
        assertEquals(listOf(NewsCategory.GENERAL to 1, NewsCategory.GENERAL to 1), fake.requests)
    }

    @Test
    fun `repeated retry during an active first page request does not start another request`() = runTest(dispatcher) {
        val response = CompletableDeferred<NewsPageResult>()
        val fake = FakeNewsRepository { _, _ -> response.await() }
        val viewModel = HomeViewModel(fake)
        runCurrent()

        viewModel.retry()
        viewModel.retry()
        runCurrent()
        assertEquals(listOf(NewsCategory.GENERAL to 1), fake.requests)

        response.complete(NewsPageResult.Success(ArticlePage(emptyList(), 0)))
        runCurrent()
        assertEquals(HomeScreenState(NewsCategory.GENERAL, HomeUiState.Empty), viewModel.uiState.value)
    }

    @Test
    fun `a cancelled request does not become an error state`() = runTest(dispatcher) {
        val fake = FakeNewsRepository { _, _ -> throw CancellationException("stale request") }
        val viewModel = HomeViewModel(fake)

        runCurrent()

        assertEquals(HomeScreenState(NewsCategory.GENERAL, HomeUiState.Loading), viewModel.uiState.value)
    }

    @Test
    fun `switching category shows its own result and reuses completed pages`() = runTest(dispatcher) {
        val general = article("general")
        val technology = article("technology")
        val fake = FakeNewsRepository { category, _ ->
            val result = if (category == NewsCategory.GENERAL) general else technology
            NewsPageResult.Success(ArticlePage(listOf(result), 1))
        }
        val viewModel = HomeViewModel(fake)
        runCurrent()

        viewModel.selectCategory(NewsCategory.TECHNOLOGY)
        assertEquals(HomeScreenState(NewsCategory.TECHNOLOGY, HomeUiState.Loading), viewModel.uiState.value)
        runCurrent()
        assertEquals(HomeScreenState(NewsCategory.TECHNOLOGY, HomeUiState.Content(listOf(technology))), viewModel.uiState.value)

        viewModel.selectCategory(NewsCategory.GENERAL)
        assertEquals(HomeScreenState(NewsCategory.GENERAL, HomeUiState.Content(listOf(general))), viewModel.uiState.value)
        viewModel.selectCategory(NewsCategory.GENERAL)
        assertEquals(listOf(NewsCategory.GENERAL to 1, NewsCategory.TECHNOLOGY to 1), fake.requests)
    }

    @Test
    fun `a completed empty category is reused after switching away`() = runTest(dispatcher) {
        val fake = FakeNewsRepository { category, _ ->
            NewsPageResult.Success(
                if (category == NewsCategory.GENERAL) ArticlePage(emptyList(), 0)
                else ArticlePage(listOf(article("technology")), 1),
            )
        }
        val viewModel = HomeViewModel(fake)
        runCurrent()
        viewModel.selectCategory(NewsCategory.TECHNOLOGY)
        runCurrent()

        viewModel.selectCategory(NewsCategory.GENERAL)

        assertEquals(HomeScreenState(NewsCategory.GENERAL, HomeUiState.Empty), viewModel.uiState.value)
        assertEquals(2, fake.requests.size)
    }

    @Test
    fun `returning to completed category invalidates an unfinished other category`() = runTest(dispatcher) {
        val unfinishedTechnology = CompletableDeferred<NewsPageResult>()
        val general = article("general")
        val fake = FakeNewsRepository { category, _ ->
            when (category) {
                NewsCategory.GENERAL -> NewsPageResult.Success(ArticlePage(listOf(general), 1))
                NewsCategory.TECHNOLOGY -> withContext(NonCancellable) { unfinishedTechnology.await() }
                else -> error("Unexpected category: $category")
            }
        }
        val viewModel = HomeViewModel(fake)
        runCurrent()
        viewModel.selectCategory(NewsCategory.TECHNOLOGY)
        runCurrent()

        viewModel.selectCategory(NewsCategory.GENERAL)
        assertEquals(HomeScreenState(NewsCategory.GENERAL, HomeUiState.Content(listOf(general))), viewModel.uiState.value)
        unfinishedTechnology.complete(NewsPageResult.Success(ArticlePage(listOf(article("late")), 1)))
        runCurrent()
        assertEquals(HomeScreenState(NewsCategory.GENERAL, HomeUiState.Content(listOf(general))), viewModel.uiState.value)

        viewModel.selectCategory(NewsCategory.TECHNOLOGY)
        assertEquals(HomeScreenState(NewsCategory.TECHNOLOGY, HomeUiState.Loading), viewModel.uiState.value)
        runCurrent()
        assertEquals(2, fake.requests.count { it.first == NewsCategory.TECHNOLOGY })
    }

    @Test
    fun `retry after category error requests the selected category only`() = runTest(dispatcher) {
        var technologyAttempts = 0
        val fake = FakeNewsRepository { category, _ ->
            when (category) {
                NewsCategory.GENERAL -> NewsPageResult.Success(ArticlePage(listOf(article("general")), 1))
                NewsCategory.TECHNOLOGY -> {
                    if (++technologyAttempts == 1) NewsPageResult.Failure(NewsError.CONNECTION)
                    else NewsPageResult.Success(ArticlePage(listOf(article("technology")), 1))
                }
                else -> error("Unexpected category: $category")
            }
        }
        val viewModel = HomeViewModel(fake)
        runCurrent()
        viewModel.selectCategory(NewsCategory.TECHNOLOGY)
        runCurrent()
        assertEquals(HomeScreenState(NewsCategory.TECHNOLOGY, HomeUiState.Error(NewsError.CONNECTION)), viewModel.uiState.value)

        viewModel.retry()
        runCurrent()

        assertEquals(HomeScreenState(NewsCategory.TECHNOLOGY, HomeUiState.Content(listOf(article("technology")))), viewModel.uiState.value)
        assertEquals(
            listOf(NewsCategory.GENERAL to 1, NewsCategory.TECHNOLOGY to 1, NewsCategory.TECHNOLOGY to 1),
            fake.requests,
        )
    }

    @Test
    fun `A to B to A ignores old success failure and completion while latest request runs`() = runTest(dispatcher) {
        val firstA = CompletableDeferred<NewsPageResult>()
        val requestB = CompletableDeferred<NewsPageResult>()
        val latestA = CompletableDeferred<NewsPageResult>()
        var aRequests = 0
        val fake = FakeNewsRepository { category, _ ->
            when (category) {
                NewsCategory.GENERAL -> {
                    if (++aRequests == 1) withContext(NonCancellable) { firstA.await() }
                    else latestA.await()
                }
                NewsCategory.BUSINESS -> withContext(NonCancellable) { requestB.await() }
                else -> error("Unexpected category: $category")
            }
        }
        val viewModel = HomeViewModel(fake)
        runCurrent()
        viewModel.selectCategory(NewsCategory.BUSINESS)
        runCurrent()
        viewModel.selectCategory(NewsCategory.GENERAL)
        runCurrent()

        firstA.complete(NewsPageResult.Success(ArticlePage(listOf(article("stale")), 1)))
        requestB.complete(NewsPageResult.Failure(NewsError.RATE_LIMITED))
        runCurrent()
        assertEquals(HomeScreenState(NewsCategory.GENERAL, HomeUiState.Loading), viewModel.uiState.value)
        viewModel.retry()
        runCurrent()
        assertEquals(3, fake.requests.size)

        latestA.complete(NewsPageResult.Success(ArticlePage(listOf(article("latest")), 1)))
        runCurrent()
        assertEquals(HomeScreenState(NewsCategory.GENERAL, HomeUiState.Content(listOf(article("latest")))), viewModel.uiState.value)
        assertEquals(
            listOf(NewsCategory.GENERAL to 1, NewsCategory.BUSINESS to 1, NewsCategory.GENERAL to 1),
            fake.requests,
        )
    }

    @Test
    fun `stale failure cannot replace a newer success for the same category`() = runTest(dispatcher) {
        val firstA = CompletableDeferred<NewsPageResult>()
        var aRequests = 0
        val fake = FakeNewsRepository { category, _ ->
            when (category) {
                NewsCategory.GENERAL -> {
                    if (++aRequests == 1) withContext(NonCancellable) { firstA.await() }
                    else NewsPageResult.Success(ArticlePage(listOf(article("latest")), 1))
                }
                NewsCategory.SCIENCE -> NewsPageResult.Success(ArticlePage(listOf(article("science")), 1))
                else -> error("Unexpected category: $category")
            }
        }
        val viewModel = HomeViewModel(fake)
        runCurrent()
        viewModel.selectCategory(NewsCategory.SCIENCE)
        runCurrent()
        viewModel.selectCategory(NewsCategory.GENERAL)
        runCurrent()

        firstA.complete(NewsPageResult.Failure(NewsError.CONNECTION))
        runCurrent()

        assertEquals(HomeScreenState(NewsCategory.GENERAL, HomeUiState.Content(listOf(article("latest")))), viewModel.uiState.value)
    }

    @Test
    fun `refresh keeps loaded content visible until the new first page succeeds`() = runTest(dispatcher) {
        val original = article("original")
        val replacement = article("replacement")
        val refreshed = CompletableDeferred<NewsPageResult>()
        var attempts = 0
        val fake = FakeNewsRepository { _, _ ->
            if (++attempts == 1) NewsPageResult.Success(ArticlePage(listOf(original), 1))
            else refreshed.await()
        }
        val viewModel = HomeViewModel(fake)
        runCurrent()

        viewModel.refresh()
        assertEquals(
            HomeScreenState(NewsCategory.GENERAL, HomeUiState.Content(listOf(original)), isRefreshing = true),
            viewModel.uiState.value,
        )
        runCurrent()
        assertEquals(listOf(NewsCategory.GENERAL to 1, NewsCategory.GENERAL to 1), fake.requests)

        refreshed.complete(NewsPageResult.Success(ArticlePage(listOf(replacement), 1)))
        runCurrent()
        assertEquals(
            HomeScreenState(NewsCategory.GENERAL, HomeUiState.Content(listOf(replacement))),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `successful refresh emits feedback even when articles are unchanged`() = runTest(dispatcher) {
        val original = article("original")
        val fake = FakeNewsRepository { _, _ -> NewsPageResult.Success(ArticlePage(listOf(original), 1)) }
        val viewModel = HomeViewModel(fake)
        runCurrent()
        val successEvents = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.refreshSucceeded.collect { successEvents += it }
        }

        assertEquals(0, successEvents.size)
        viewModel.refresh()
        runCurrent()

        assertEquals(2, fake.requests.size)
        assertEquals(1, successEvents.size)
        assertEquals(HomeScreenState(NewsCategory.GENERAL, HomeUiState.Content(listOf(original))), viewModel.uiState.value)
    }

    @Test
    fun `refresh failure keeps content and exposes a nonblocking error`() = runTest(dispatcher) {
        val original = article("original")
        var attempts = 0
        val fake = FakeNewsRepository { _, _ ->
            if (++attempts == 1) NewsPageResult.Success(ArticlePage(listOf(original), 1))
            else NewsPageResult.Failure(NewsError.CONNECTION)
        }
        val viewModel = HomeViewModel(fake)
        runCurrent()

        viewModel.refresh()
        runCurrent()

        assertEquals(
            HomeScreenState(
                NewsCategory.GENERAL,
                HomeUiState.Content(listOf(original)),
                refreshError = NewsError.CONNECTION,
            ),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `retrying a failed refresh clears its old error while the list remains visible`() = runTest(dispatcher) {
        val original = article("original")
        val retry = CompletableDeferred<NewsPageResult>()
        var attempts = 0
        val fake = FakeNewsRepository { _, _ ->
            when (++attempts) {
                1 -> NewsPageResult.Success(ArticlePage(listOf(original), 1))
                2 -> NewsPageResult.Failure(NewsError.CONNECTION)
                else -> retry.await()
            }
        }
        val viewModel = HomeViewModel(fake)
        runCurrent()
        viewModel.refresh()
        runCurrent()

        viewModel.refresh()
        assertEquals(
            HomeScreenState(NewsCategory.GENERAL, HomeUiState.Content(listOf(original)), isRefreshing = true),
            viewModel.uiState.value,
        )
        runCurrent()
        assertEquals(3, fake.requests.size)

        retry.complete(NewsPageResult.Success(ArticlePage(listOf(article("new")), 1)))
        runCurrent()
        assertEquals(
            HomeScreenState(NewsCategory.GENERAL, HomeUiState.Content(listOf(article("new")))),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `successful empty refresh replaces old content with empty state`() = runTest(dispatcher) {
        var attempts = 0
        val fake = FakeNewsRepository { _, _ ->
            if (++attempts == 1) NewsPageResult.Success(ArticlePage(listOf(article("old")), 1))
            else NewsPageResult.Success(ArticlePage(emptyList(), 0))
        }
        val viewModel = HomeViewModel(fake)
        runCurrent()

        viewModel.refresh()
        runCurrent()

        assertEquals(HomeScreenState(NewsCategory.GENERAL, HomeUiState.Empty), viewModel.uiState.value)
        assertEquals(listOf(NewsCategory.GENERAL to 1, NewsCategory.GENERAL to 1), fake.requests)
    }

    @Test
    fun `initial load and active refresh reject duplicate first page requests`() = runTest(dispatcher) {
        val initial = CompletableDeferred<NewsPageResult>()
        val refreshed = CompletableDeferred<NewsPageResult>()
        var attempts = 0
        val fake = FakeNewsRepository { _, _ ->
            if (++attempts == 1) initial.await() else refreshed.await()
        }
        val viewModel = HomeViewModel(fake)
        runCurrent()

        viewModel.refresh()
        runCurrent()
        assertEquals(listOf(NewsCategory.GENERAL to 1), fake.requests)

        initial.complete(NewsPageResult.Success(ArticlePage(listOf(article("old")), 1)))
        runCurrent()
        viewModel.refresh()
        viewModel.refresh()
        runCurrent()
        assertEquals(listOf(NewsCategory.GENERAL to 1, NewsCategory.GENERAL to 1), fake.requests)

        refreshed.complete(NewsPageResult.Success(ArticlePage(listOf(article("new")), 1)))
        runCurrent()
        assertEquals(
            HomeScreenState(NewsCategory.GENERAL, HomeUiState.Content(listOf(article("new")))),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `switching category invalidates an unfinished refresh failure`() = runTest(dispatcher) {
        val staleRefresh = CompletableDeferred<NewsPageResult>()
        var technologyRequests = 0
        val fake = FakeNewsRepository { category, _ ->
            when (category) {
                NewsCategory.GENERAL -> NewsPageResult.Success(ArticlePage(listOf(article("general")), 1))
                NewsCategory.TECHNOLOGY -> {
                    if (++technologyRequests == 1) {
                        NewsPageResult.Success(ArticlePage(listOf(article("technology")), 1))
                    } else {
                        withContext(NonCancellable) { staleRefresh.await() }
                    }
                }
                else -> error("Unexpected category: $category")
            }
        }
        val viewModel = HomeViewModel(fake)
        runCurrent()
        viewModel.selectCategory(NewsCategory.TECHNOLOGY)
        runCurrent()
        viewModel.refresh()
        runCurrent()

        viewModel.selectCategory(NewsCategory.GENERAL)
        staleRefresh.complete(NewsPageResult.Failure(NewsError.CONNECTION))
        runCurrent()
        assertEquals(
            HomeScreenState(NewsCategory.GENERAL, HomeUiState.Content(listOf(article("general")))),
            viewModel.uiState.value,
        )

        viewModel.selectCategory(NewsCategory.TECHNOLOGY)
        assertEquals(
            HomeScreenState(NewsCategory.TECHNOLOGY, HomeUiState.Content(listOf(article("technology")))),
            viewModel.uiState.value,
        )
        assertEquals(2, technologyRequests)
    }

    private fun article(id: String) = Article(id, id, null, null, null, null, null, null)

    private class FakeNewsRepository(
        private val response: suspend (NewsCategory, Int) -> NewsPageResult,
    ) : NewsRepository {
        val requests = mutableListOf<Pair<NewsCategory, Int>>()

        override suspend fun getHeadlines(category: NewsCategory, page: Int): NewsPageResult {
            requests += category to page
            return response(category, page)
        }
    }
}
