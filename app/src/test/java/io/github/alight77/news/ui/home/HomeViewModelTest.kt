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
        val fake = FakeNewsRepository { _, _ -> NewsPageResult.Success(ArticlePage(listOf(article), 10)) }
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
            else NewsPageResult.Success(ArticlePage(listOf(article), 10))
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
            NewsPageResult.Success(ArticlePage(listOf(result), 10))
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
                else ArticlePage(listOf(article("technology")), 10),
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
                NewsCategory.GENERAL -> NewsPageResult.Success(ArticlePage(listOf(general), 10))
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
        unfinishedTechnology.complete(NewsPageResult.Success(ArticlePage(listOf(article("late")), 10)))
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
                NewsCategory.GENERAL -> NewsPageResult.Success(ArticlePage(listOf(article("general")), 10))
                NewsCategory.TECHNOLOGY -> {
                    if (++technologyAttempts == 1) NewsPageResult.Failure(NewsError.CONNECTION)
                    else NewsPageResult.Success(ArticlePage(listOf(article("technology")), 10))
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

        firstA.complete(NewsPageResult.Success(ArticlePage(listOf(article("stale")), 10)))
        requestB.complete(NewsPageResult.Failure(NewsError.RATE_LIMITED))
        runCurrent()
        assertEquals(HomeScreenState(NewsCategory.GENERAL, HomeUiState.Loading), viewModel.uiState.value)
        viewModel.retry()
        runCurrent()
        assertEquals(3, fake.requests.size)

        latestA.complete(NewsPageResult.Success(ArticlePage(listOf(article("latest")), 10)))
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
                    else NewsPageResult.Success(ArticlePage(listOf(article("latest")), 10))
                }
                NewsCategory.SCIENCE -> NewsPageResult.Success(ArticlePage(listOf(article("science")), 10))
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
            if (++attempts == 1) NewsPageResult.Success(ArticlePage(listOf(original), 10))
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

        refreshed.complete(NewsPageResult.Success(ArticlePage(listOf(replacement), 10)))
        runCurrent()
        assertEquals(
            HomeScreenState(NewsCategory.GENERAL, HomeUiState.Content(listOf(replacement))),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `successful refresh emits feedback even when articles are unchanged`() = runTest(dispatcher) {
        val original = article("original")
        val fake = FakeNewsRepository { _, _ -> NewsPageResult.Success(ArticlePage(listOf(original), 10)) }
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
            if (++attempts == 1) NewsPageResult.Success(ArticlePage(listOf(original), 10))
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
                1 -> NewsPageResult.Success(ArticlePage(listOf(original), 10))
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

        retry.complete(NewsPageResult.Success(ArticlePage(listOf(article("new")), 10)))
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
            if (++attempts == 1) NewsPageResult.Success(ArticlePage(listOf(article("old")), 10))
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

        initial.complete(NewsPageResult.Success(ArticlePage(listOf(article("old")), 10)))
        runCurrent()
        viewModel.refresh()
        viewModel.refresh()
        runCurrent()
        assertEquals(listOf(NewsCategory.GENERAL to 1, NewsCategory.GENERAL to 1), fake.requests)

        refreshed.complete(NewsPageResult.Success(ArticlePage(listOf(article("new")), 10)))
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
                NewsCategory.GENERAL -> NewsPageResult.Success(ArticlePage(listOf(article("general")), 10))
                NewsCategory.TECHNOLOGY -> {
                    if (++technologyRequests == 1) {
                        NewsPageResult.Success(ArticlePage(listOf(article("technology")), 10))
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

    @Test
    fun `next page keeps existing articles visible and ignores duplicate triggers`() = runTest(dispatcher) {
        val pendingPage = CompletableDeferred<NewsPageResult>()
        val fake = FakeNewsRepository { _, page ->
            if (page == 1) NewsPageResult.Success(ArticlePage(listOf(article("first")), 10))
            else pendingPage.await()
        }
        val viewModel = HomeViewModel(fake)
        runCurrent()

        viewModel.loadNextPage()
        viewModel.loadNextPage()
        assertEquals(
            HomeScreenState(
                NewsCategory.GENERAL,
                HomeUiState.Content(listOf(article("first"))),
                appendState = HomeAppendState.Loading,
            ),
            viewModel.uiState.value,
        )
        runCurrent()
        assertEquals(listOf(NewsCategory.GENERAL to 1, NewsCategory.GENERAL to 2), fake.requests)

        pendingPage.complete(NewsPageResult.Success(ArticlePage(listOf(article("second")), 1)))
        runCurrent()
        assertEquals(
            HomeScreenState(
                NewsCategory.GENERAL,
                HomeUiState.Content(listOf(article("first"), article("second"))),
                appendState = HomeAppendState.EndReached,
            ),
            viewModel.uiState.value,
        )
        viewModel.loadNextPage()
        runCurrent()
        assertEquals(2, fake.requests.size)
    }

    @Test
    fun `failed append keeps the list and explicit retry requests the same page`() = runTest(dispatcher) {
        var secondPageAttempts = 0
        val fake = FakeNewsRepository { _, page ->
            when (page) {
                1 -> NewsPageResult.Success(ArticlePage(listOf(article("first")), 10))
                2 -> if (++secondPageAttempts == 1) NewsPageResult.Failure(NewsError.CONNECTION)
                    else NewsPageResult.Success(ArticlePage(listOf(article("second")), 1))
                else -> error("Unexpected page $page")
            }
        }
        val viewModel = HomeViewModel(fake)
        runCurrent()

        viewModel.loadNextPage()
        runCurrent()
        assertEquals(HomeAppendState.Error(NewsError.CONNECTION), viewModel.uiState.value.appendState)
        assertEquals(HomeUiState.Content(listOf(article("first"))), viewModel.uiState.value.pageState)
        viewModel.loadNextPage()
        runCurrent()
        assertEquals(2, fake.requests.size)

        viewModel.retryNextPage()
        runCurrent()
        assertEquals(listOf(1, 2, 2), fake.requests.map { it.second })
        assertEquals(
            HomeUiState.Content(listOf(article("first"), article("second"))),
            viewModel.uiState.value.pageState,
        )
        assertEquals(HomeAppendState.EndReached, viewModel.uiState.value.appendState)
    }

    @Test
    fun `duplicate-only full page requires manual continuation without ending pagination`() = runTest(dispatcher) {
        val fake = FakeNewsRepository { _, page ->
            when (page) {
                1, 2 -> NewsPageResult.Success(ArticlePage(listOf(article("first")), 10))
                3 -> NewsPageResult.Success(ArticlePage(listOf(article("third")), 1))
                else -> error("Unexpected page $page")
            }
        }
        val viewModel = HomeViewModel(fake)
        runCurrent()

        viewModel.loadNextPage()
        runCurrent()
        assertEquals(HomeUiState.Content(listOf(article("first"))), viewModel.uiState.value.pageState)
        assertEquals(HomeAppendState.ManualContinue, viewModel.uiState.value.appendState)
        viewModel.loadNextPage()
        runCurrent()
        assertEquals(listOf(1, 2), fake.requests.map { it.second })

        viewModel.retryNextPage()
        runCurrent()
        assertEquals(listOf(1, 2, 3), fake.requests.map { it.second })
        assertEquals(
            HomeUiState.Content(listOf(article("first"), article("third"))),
            viewModel.uiState.value.pageState,
        )
    }

    @Test
    fun `refresh after append replaces old pages and restarts at page two`() = runTest(dispatcher) {
        var firstPageAttempts = 0
        var secondPageAttempts = 0
        val fake = FakeNewsRepository { _, page ->
            when (page) {
                1 -> if (++firstPageAttempts == 1) NewsPageResult.Success(ArticlePage(listOf(article("old")), 10))
                    else NewsPageResult.Success(ArticlePage(listOf(article("fresh")), 10))
                2 -> if (++secondPageAttempts == 1) NewsPageResult.Success(ArticlePage(listOf(article("old-page-two")), 10))
                    else NewsPageResult.Success(ArticlePage(listOf(article("fresh-page-two")), 1))
                else -> error("Unexpected page $page")
            }
        }
        val viewModel = HomeViewModel(fake)
        runCurrent()
        viewModel.loadNextPage()
        runCurrent()

        viewModel.refresh()
        runCurrent()
        assertEquals(HomeUiState.Content(listOf(article("fresh"))), viewModel.uiState.value.pageState)
        viewModel.loadNextPage()
        runCurrent()
        assertEquals(listOf(1, 2, 1, 2), fake.requests.map { it.second })
        assertEquals(
            HomeUiState.Content(listOf(article("fresh"), article("fresh-page-two"))),
            viewModel.uiState.value.pageState,
        )
    }

    @Test
    fun `refresh invalidates a noncancellable old append result`() = runTest(dispatcher) {
        val staleAppend = CompletableDeferred<NewsPageResult>()
        var firstPageAttempts = 0
        val fake = FakeNewsRepository { _, page ->
            when (page) {
                1 -> if (++firstPageAttempts == 1) NewsPageResult.Success(ArticlePage(listOf(article("old")), 10))
                    else NewsPageResult.Success(ArticlePage(listOf(article("fresh")), 10))
                2 -> withContext(NonCancellable) { staleAppend.await() }
                else -> error("Unexpected page $page")
            }
        }
        val viewModel = HomeViewModel(fake)
        runCurrent()
        viewModel.loadNextPage()
        runCurrent()

        viewModel.refresh()
        assertEquals(HomeUiState.Content(listOf(article("old"))), viewModel.uiState.value.pageState)
        assertEquals(true, viewModel.uiState.value.isRefreshing)
        runCurrent()
        staleAppend.complete(NewsPageResult.Success(ArticlePage(listOf(article("stale")), 10)))
        runCurrent()

        assertEquals(HomeUiState.Content(listOf(article("fresh"))), viewModel.uiState.value.pageState)
        assertEquals(HomeAppendState.Idle, viewModel.uiState.value.appendState)
        assertEquals(listOf(1, 2, 1), fake.requests.map { it.second })
    }

    @Test
    fun `failed refresh during append restores completed list and its next page`() = runTest(dispatcher) {
        val staleAppend = CompletableDeferred<NewsPageResult>()
        var secondPageAttempts = 0
        var firstPageAttempts = 0
        val fake = FakeNewsRepository { _, page ->
            when (page) {
                1 -> if (++firstPageAttempts == 1) NewsPageResult.Success(ArticlePage(listOf(article("first")), 10))
                    else NewsPageResult.Failure(NewsError.TIMEOUT)
                2 -> if (++secondPageAttempts == 1) withContext(NonCancellable) { staleAppend.await() }
                    else NewsPageResult.Success(ArticlePage(listOf(article("second")), 1))
                else -> error("Unexpected page $page")
            }
        }
        val viewModel = HomeViewModel(fake)
        runCurrent()
        viewModel.loadNextPage()
        runCurrent()

        viewModel.refresh()
        runCurrent()
        staleAppend.complete(NewsPageResult.Failure(NewsError.CONNECTION))
        runCurrent()
        assertEquals(HomeUiState.Content(listOf(article("first"))), viewModel.uiState.value.pageState)
        assertEquals(NewsError.TIMEOUT, viewModel.uiState.value.refreshError)
        assertEquals(HomeAppendState.Idle, viewModel.uiState.value.appendState)

        viewModel.loadNextPage()
        runCurrent()
        assertEquals(listOf(1, 2, 1, 2), fake.requests.map { it.second })
        assertEquals(HomeUiState.Content(listOf(article("first"), article("second"))), viewModel.uiState.value.pageState)
    }

    @Test
    fun `category switch restores loaded articles and successful page progress`() = runTest(dispatcher) {
        val fake = FakeNewsRepository { category, page ->
            when (category to page) {
                NewsCategory.GENERAL to 1 -> NewsPageResult.Success(ArticlePage(listOf(article("general-one")), 10))
                NewsCategory.GENERAL to 2 -> NewsPageResult.Success(ArticlePage(listOf(article("general-two")), 10))
                NewsCategory.GENERAL to 3 -> NewsPageResult.Success(ArticlePage(listOf(article("general-three")), 1))
                NewsCategory.TECHNOLOGY to 1 -> NewsPageResult.Success(ArticlePage(listOf(article("technology")), 1))
                else -> error("Unexpected request: $category/$page")
            }
        }
        val viewModel = HomeViewModel(fake)
        runCurrent()
        viewModel.loadNextPage()
        runCurrent()

        viewModel.selectCategory(NewsCategory.TECHNOLOGY)
        runCurrent()
        viewModel.selectCategory(NewsCategory.GENERAL)
        assertEquals(
            HomeUiState.Content(listOf(article("general-one"), article("general-two"))),
            viewModel.uiState.value.pageState,
        )
        viewModel.loadNextPage()
        runCurrent()
        assertEquals(listOf(1, 2, 1, 3), fake.requests.map { it.second })
    }

    @Test
    fun `a short first page and the thousandth article limit stop requests`() = runTest(dispatcher) {
        val shortPage = FakeNewsRepository { _, page ->
            if (page == 1) NewsPageResult.Success(ArticlePage(listOf(article("only")), 1))
            else error("Unexpected page $page")
        }
        val shortViewModel = HomeViewModel(shortPage)
        runCurrent()
        assertEquals(HomeAppendState.EndReached, shortViewModel.uiState.value.appendState)
        shortViewModel.loadNextPage()
        runCurrent()
        assertEquals(1, shortPage.requests.size)

        val fullPages = FakeNewsRepository { _, page ->
            NewsPageResult.Success(ArticlePage(listOf(article("article-$page")), 10))
        }
        val fullViewModel = HomeViewModel(fullPages)
        runCurrent()
        repeat(99) {
            fullViewModel.loadNextPage()
            runCurrent()
        }
        assertEquals(HomeAppendState.EndReached, fullViewModel.uiState.value.appendState)
        fullViewModel.loadNextPage()
        runCurrent()
        assertEquals(100, fullPages.requests.size)
        assertEquals(100, (fullViewModel.uiState.value.pageState as HomeUiState.Content).articles.size)
    }

    @Test
    fun `partial duplicate page preserves first occurrence order`() = runTest(dispatcher) {
        val fake = FakeNewsRepository { _, page ->
            if (page == 1) NewsPageResult.Success(ArticlePage(listOf(article("a"), article("b")), 10))
            else NewsPageResult.Success(ArticlePage(listOf(article("b"), article("c"), article("a"), article("d")), 4))
        }
        val viewModel = HomeViewModel(fake)
        runCurrent()

        viewModel.loadNextPage()
        runCurrent()
        assertEquals(
            listOf("a", "b", "c", "d"),
            (viewModel.uiState.value.pageState as HomeUiState.Content).articles.map { it.id },
        )
        assertEquals(HomeAppendState.EndReached, viewModel.uiState.value.appendState)
    }

    @Test
    fun `failed refresh after two completed pages retains the third page cursor`() = runTest(dispatcher) {
        var firstPageAttempts = 0
        val fake = FakeNewsRepository { _, page ->
            when (page) {
                1 -> if (++firstPageAttempts == 1) NewsPageResult.Success(ArticlePage(listOf(article("one")), 10))
                    else NewsPageResult.Failure(NewsError.CONNECTION)
                2 -> NewsPageResult.Success(ArticlePage(listOf(article("two")), 10))
                3 -> NewsPageResult.Success(ArticlePage(listOf(article("three")), 1))
                else -> error("Unexpected page $page")
            }
        }
        val viewModel = HomeViewModel(fake)
        runCurrent()
        viewModel.loadNextPage()
        runCurrent()

        viewModel.refresh()
        runCurrent()
        assertEquals(NewsError.CONNECTION, viewModel.uiState.value.refreshError)
        assertEquals(HomeUiState.Content(listOf(article("one"), article("two"))), viewModel.uiState.value.pageState)
        viewModel.loadNextPage()
        runCurrent()
        assertEquals(listOf(1, 2, 1, 3), fake.requests.map { it.second })
        assertEquals(
            HomeUiState.Content(listOf(article("one"), article("two"), article("three"))),
            viewModel.uiState.value.pageState,
        )
    }

    @Test
    fun `empty refresh clears every previously appended article`() = runTest(dispatcher) {
        var firstPageAttempts = 0
        val fake = FakeNewsRepository { _, page ->
            when (page) {
                1 -> if (++firstPageAttempts == 1) NewsPageResult.Success(ArticlePage(listOf(article("one")), 10))
                    else NewsPageResult.Success(ArticlePage(emptyList(), 0))
                2 -> NewsPageResult.Success(ArticlePage(listOf(article("two")), 10))
                else -> error("Unexpected page $page")
            }
        }
        val viewModel = HomeViewModel(fake)
        runCurrent()
        viewModel.loadNextPage()
        runCurrent()

        viewModel.refresh()
        runCurrent()

        assertEquals(HomeUiState.Empty, viewModel.uiState.value.pageState)
        assertEquals(HomeAppendState.Idle, viewModel.uiState.value.appendState)
        assertEquals(listOf(1, 2, 1), fake.requests.map { it.second })
    }

    @Test
    fun `late failure from cancelled append cannot restore an old footer`() = runTest(dispatcher) {
        val staleAppend = CompletableDeferred<NewsPageResult>()
        var firstPageAttempts = 0
        val fake = FakeNewsRepository { _, page ->
            when (page) {
                1 -> if (++firstPageAttempts == 1) NewsPageResult.Success(ArticlePage(listOf(article("old")), 10))
                    else NewsPageResult.Success(ArticlePage(listOf(article("fresh")), 1))
                2 -> withContext(NonCancellable) { staleAppend.await() }
                else -> error("Unexpected page $page")
            }
        }
        val viewModel = HomeViewModel(fake)
        runCurrent()
        viewModel.loadNextPage()
        runCurrent()

        viewModel.refresh()
        runCurrent()
        staleAppend.complete(NewsPageResult.Failure(NewsError.TIMEOUT))
        runCurrent()
        assertEquals(HomeUiState.Content(listOf(article("fresh"))), viewModel.uiState.value.pageState)
        assertEquals(HomeAppendState.EndReached, viewModel.uiState.value.appendState)
        assertEquals(null, viewModel.uiState.value.refreshError)
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
