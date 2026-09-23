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
import kotlinx.coroutines.test.StandardTestDispatcher
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
        val fake = FakeNewsRepository { NewsPageResult.Success(ArticlePage(listOf(article), 1)) }
        val viewModel = HomeViewModel(fake)

        assertEquals(HomeUiState.Loading, viewModel.uiState.value)
        runCurrent()

        assertEquals(HomeUiState.Content(listOf(article)), viewModel.uiState.value)
        assertEquals(listOf(NewsCategory.GENERAL to 1), fake.requests)
    }

    @Test
    fun `an empty first page moves to empty state`() = runTest(dispatcher) {
        val fake = FakeNewsRepository { NewsPageResult.Success(ArticlePage(emptyList(), 0)) }
        val viewModel = HomeViewModel(fake)

        runCurrent()

        assertEquals(HomeUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `a failed first page exposes only a business error`() = runTest(dispatcher) {
        val fake = FakeNewsRepository { NewsPageResult.Failure(NewsError.RATE_LIMITED) }
        val viewModel = HomeViewModel(fake)

        runCurrent()

        assertEquals(HomeUiState.Error(NewsError.RATE_LIMITED), viewModel.uiState.value)
    }

    @Test
    fun `retry repeats the failed first page and recovers to content`() = runTest(dispatcher) {
        val article = Article("news-1", "Headline", null, null, null, null, null, null)
        var attempt = 0
        val fake = FakeNewsRepository {
            if (++attempt == 1) NewsPageResult.Failure(NewsError.CONNECTION)
            else NewsPageResult.Success(ArticlePage(listOf(article), 1))
        }
        val viewModel = HomeViewModel(fake)
        runCurrent()
        assertEquals(HomeUiState.Error(NewsError.CONNECTION), viewModel.uiState.value)

        viewModel.retry()
        assertEquals(HomeUiState.Loading, viewModel.uiState.value)
        runCurrent()

        assertEquals(HomeUiState.Content(listOf(article)), viewModel.uiState.value)
        assertEquals(listOf(NewsCategory.GENERAL to 1, NewsCategory.GENERAL to 1), fake.requests)
    }

    @Test
    fun `repeated retry during an active first page request does not start another request`() = runTest(dispatcher) {
        val response = CompletableDeferred<NewsPageResult>()
        val fake = FakeNewsRepository { response.await() }
        val viewModel = HomeViewModel(fake)
        runCurrent()

        viewModel.retry()
        viewModel.retry()
        runCurrent()
        assertEquals(listOf(NewsCategory.GENERAL to 1), fake.requests)

        response.complete(NewsPageResult.Success(ArticlePage(emptyList(), 0)))
        runCurrent()
        assertEquals(HomeUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `a cancelled request does not become an error state`() = runTest(dispatcher) {
        val fake = FakeNewsRepository { throw CancellationException("stale request") }
        val viewModel = HomeViewModel(fake)

        runCurrent()

        assertEquals(HomeUiState.Loading, viewModel.uiState.value)
    }

    private class FakeNewsRepository(
        private val response: suspend () -> NewsPageResult,
    ) : NewsRepository {
        val requests = mutableListOf<Pair<NewsCategory, Int>>()

        override suspend fun getHeadlines(category: NewsCategory, page: Int): NewsPageResult {
            requests += category to page
            return response()
        }
    }
}
