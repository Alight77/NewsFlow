package io.github.alight77.news.ui.search

import io.github.alight77.news.domain.model.Article
import io.github.alight77.news.domain.model.ArticlePage
import io.github.alight77.news.domain.model.NewsCategory
import io.github.alight77.news.domain.model.NewsError
import io.github.alight77.news.domain.model.NewsPageResult
import io.github.alight77.news.domain.repository.NewsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
class SearchViewModelTest {
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
    fun `a nonblank query trims input and waits 400 milliseconds before its first request`() = runTest(dispatcher) {
        val article = article("android")
        val fake = FakeNewsRepository { NewsPageResult.Success(ArticlePage(listOf(article), 1)) }
        val viewModel = SearchViewModel(fake)

        viewModel.updateQuery(" Android ")

        assertEquals(
            SearchScreenState(" Android ", "Android", SearchUiState.Loading),
            viewModel.uiState.value,
        )
        advanceTimeBy(399)
        runCurrent()
        assertEquals(emptyList<String>(), fake.queries)

        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf("Android"), fake.queries)
        assertEquals(SearchScreenState(" Android ", "Android", SearchUiState.Content(listOf(article))), viewModel.uiState.value)
    }

    @Test
    fun `blank input clears old content immediately and never calls search`() = runTest(dispatcher) {
        val pending = CompletableDeferred<NewsPageResult>()
        val fake = FakeNewsRepository { withContext(NonCancellable) { pending.await() } }
        val viewModel = SearchViewModel(fake)

        viewModel.updateQuery("Android")
        advanceTimeBy(400)
        runCurrent()
        viewModel.updateQuery("   ")
        pending.complete(NewsPageResult.Success(ArticlePage(listOf(article("stale")), 1)))
        runCurrent()

        assertEquals(SearchScreenState(input = "   "), viewModel.uiState.value)
        assertEquals(listOf("Android"), fake.queries)
    }

    @Test
    fun `only the newest query may replace the search state`() = runTest(dispatcher) {
        val android = CompletableDeferred<NewsPageResult>()
        val kotlin = CompletableDeferred<NewsPageResult>()
        val fake = FakeNewsRepository { query ->
            withContext(NonCancellable) {
                when (query) {
                    "Android" -> android.await()
                    "Kotlin" -> kotlin.await()
                    else -> error("Unexpected query: $query")
                }
            }
        }
        val viewModel = SearchViewModel(fake)

        viewModel.updateQuery("Android")
        advanceTimeBy(400)
        runCurrent()
        viewModel.updateQuery("Kotlin")
        kotlin.complete(NewsPageResult.Success(ArticlePage(listOf(article("kotlin")), 1)))
        advanceTimeBy(400)
        runCurrent()
        android.complete(NewsPageResult.Failure(NewsError.CONNECTION))
        runCurrent()

        assertEquals(SearchScreenState("Kotlin", "Kotlin", SearchUiState.Content(listOf(article("kotlin")))), viewModel.uiState.value)
        assertEquals(listOf("Android", "Kotlin"), fake.queries)
    }

    @Test
    fun `the same normalized query does not start another automatic request`() = runTest(dispatcher) {
        val fake = FakeNewsRepository { NewsPageResult.Success(ArticlePage(listOf(article("android")), 1)) }
        val viewModel = SearchViewModel(fake)

        viewModel.updateQuery("Android")
        advanceTimeBy(400)
        runCurrent()
        viewModel.updateQuery(" Android ")
        advanceTimeBy(400)
        runCurrent()

        assertEquals(listOf("Android"), fake.queries)
        assertEquals(" Android ", viewModel.uiState.value.input)
    }

    @Test
    fun `retry bypasses automatic deduplication and debounce after an error`() = runTest(dispatcher) {
        val article = article("android")
        var attempts = 0
        val fake = FakeNewsRepository {
            if (++attempts == 1) NewsPageResult.Failure(NewsError.TIMEOUT)
            else NewsPageResult.Success(ArticlePage(listOf(article), 1))
        }
        val viewModel = SearchViewModel(fake)

        viewModel.updateQuery("Android")
        advanceTimeBy(400)
        runCurrent()
        viewModel.retry()
        runCurrent()

        assertEquals(listOf("Android", "Android"), fake.queries)
        assertEquals(SearchScreenState("Android", "Android", SearchUiState.Content(listOf(article))), viewModel.uiState.value)
    }

    @Test
    fun `an empty successful page maps to the empty state`() = runTest(dispatcher) {
        val fake = FakeNewsRepository { NewsPageResult.Success(ArticlePage(emptyList(), 0)) }
        val viewModel = SearchViewModel(fake)

        viewModel.updateQuery("Android")
        advanceTimeBy(400)
        runCurrent()

        assertEquals(SearchScreenState("Android", "Android", SearchUiState.Empty), viewModel.uiState.value)
    }

    private fun article(id: String) = Article(id, id, null, null, null, null, null, null)

    private class FakeNewsRepository(
        private val response: suspend (String) -> NewsPageResult,
    ) : NewsRepository {
        val queries = mutableListOf<String>()

        override suspend fun getHeadlines(category: NewsCategory, page: Int): NewsPageResult =
            error("Headlines are not expected in SearchViewModelTest")

        override suspend fun search(query: String): NewsPageResult {
            queries += query
            return response(query)
        }
    }
}
