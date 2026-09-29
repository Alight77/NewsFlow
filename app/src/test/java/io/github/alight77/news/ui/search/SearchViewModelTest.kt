package io.github.alight77.news.ui.search

import androidx.lifecycle.SavedStateHandle
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
        val fake = FakeNewsRepository { _, _ -> NewsPageResult.Success(ArticlePage(listOf(article), 1)) }
        val viewModel = SearchViewModel(fake, SavedStateHandle())

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
        assertEquals(
            SearchScreenState(" Android ", "Android", SearchUiState.Content(listOf(article)), SearchAppendState.EndReached),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `restored query searches once only when the screen becomes visible`() = runTest(dispatcher) {
        val savedState = SavedStateHandle(mapOf("search_input" to " Android "))
        val fake = FakeNewsRepository { _, _ -> NewsPageResult.Success(ArticlePage(listOf(article("android")), 1)) }
        val viewModel = SearchViewModel(fake, savedState)
        runCurrent()

        assertEquals(" Android ", viewModel.uiState.value.input)
        assertEquals(emptyList<String>(), fake.queries)

        viewModel.onSearchVisible()
        viewModel.onSearchVisible()
        runCurrent()
        viewModel.onSearchVisible()

        assertEquals(listOf("Android"), fake.queries)
        assertEquals(SearchUiState.Content(listOf(article("android"))), viewModel.uiState.value.resultState)
    }

    @Test
    fun `query edits and clearing are saved for a new view model`() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        val fake = FakeNewsRepository { _, _ -> NewsPageResult.Success(ArticlePage(emptyList(), 0)) }
        val viewModel = SearchViewModel(fake, savedState)

        viewModel.updateQuery("Android")
        viewModel.updateQuery(" Android ")
        assertEquals(" Android ", savedState.get<String>("search_input"))

        val restored = SearchViewModel(fake, savedState)
        assertEquals(" Android ", restored.uiState.value.input)
        assertEquals("Android", restored.uiState.value.normalizedQuery)

        viewModel.updateQuery("   ")
        assertEquals("   ", savedState.get<String>("search_input"))
        assertEquals("", SearchViewModel(fake, savedState).uiState.value.normalizedQuery)
    }

    @Test
    fun `blank input clears old content immediately and never calls search`() = runTest(dispatcher) {
        val pending = CompletableDeferred<NewsPageResult>()
        val fake = FakeNewsRepository { _, _ -> withContext(NonCancellable) { pending.await() } }
        val viewModel = SearchViewModel(fake, SavedStateHandle())

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
        val fake = FakeNewsRepository { query, _ ->
            withContext(NonCancellable) {
                when (query) {
                    "Android" -> android.await()
                    "Kotlin" -> kotlin.await()
                    else -> error("Unexpected query: $query")
                }
            }
        }
        val viewModel = SearchViewModel(fake, SavedStateHandle())

        viewModel.updateQuery("Android")
        advanceTimeBy(400)
        runCurrent()
        viewModel.updateQuery("Kotlin")
        kotlin.complete(NewsPageResult.Success(ArticlePage(listOf(article("kotlin")), 1)))
        advanceTimeBy(400)
        runCurrent()
        android.complete(NewsPageResult.Failure(NewsError.CONNECTION))
        runCurrent()

        assertEquals(
            SearchScreenState("Kotlin", "Kotlin", SearchUiState.Content(listOf(article("kotlin"))), SearchAppendState.EndReached),
            viewModel.uiState.value,
        )
        assertEquals(listOf("Android", "Kotlin"), fake.queries)
    }

    @Test
    fun `the same normalized query does not start another automatic request`() = runTest(dispatcher) {
        val fake = FakeNewsRepository { _, _ -> NewsPageResult.Success(ArticlePage(listOf(article("android")), 1)) }
        val viewModel = SearchViewModel(fake, SavedStateHandle())

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
        val fake = FakeNewsRepository { _, _ ->
            if (++attempts == 1) NewsPageResult.Failure(NewsError.TIMEOUT)
            else NewsPageResult.Success(ArticlePage(listOf(article), 1))
        }
        val viewModel = SearchViewModel(fake, SavedStateHandle())

        viewModel.updateQuery("Android")
        advanceTimeBy(400)
        runCurrent()
        viewModel.retry()
        runCurrent()

        assertEquals(listOf("Android", "Android"), fake.queries)
        assertEquals(
            SearchScreenState("Android", "Android", SearchUiState.Content(listOf(article)), SearchAppendState.EndReached),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `an empty successful page maps to the empty state`() = runTest(dispatcher) {
        val fake = FakeNewsRepository { _, _ -> NewsPageResult.Success(ArticlePage(emptyList(), 0)) }
        val viewModel = SearchViewModel(fake, SavedStateHandle())

        viewModel.updateQuery("Android")
        advanceTimeBy(400)
        runCurrent()

        assertEquals(SearchScreenState("Android", "Android", SearchUiState.Empty), viewModel.uiState.value)
    }

    @Test
    fun `a full first page appends the next page without losing existing results`() = runTest(dispatcher) {
        val first = article("first")
        val second = article("second")
        val fake = FakeNewsRepository { _, page ->
            if (page == 1) NewsPageResult.Success(ArticlePage(listOf(first), 10))
            else NewsPageResult.Success(ArticlePage(listOf(second), 1))
        }
        val viewModel = SearchViewModel(fake, SavedStateHandle())

        viewModel.updateQuery("Android")
        advanceTimeBy(400)
        runCurrent()
        viewModel.loadNextPage()
        runCurrent()

        assertEquals(listOf("Android" to 1, "Android" to 2), fake.requests)
        assertEquals(
            SearchScreenState("Android", "Android", SearchUiState.Content(listOf(first, second)), SearchAppendState.EndReached),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `append failure retains results and retries the same page only once`() = runTest(dispatcher) {
        val first = article("first")
        val pending = CompletableDeferred<NewsPageResult>()
        val fake = FakeNewsRepository { _, page ->
            if (page == 1) NewsPageResult.Success(ArticlePage(listOf(first), 10))
            else withContext(NonCancellable) { pending.await() }
        }
        val viewModel = SearchViewModel(fake, SavedStateHandle())

        viewModel.updateQuery("Android")
        advanceTimeBy(400)
        runCurrent()
        viewModel.loadNextPage()
        runCurrent()
        viewModel.loadNextPage()
        viewModel.retryNextPage()
        pending.complete(NewsPageResult.Failure(NewsError.TIMEOUT))
        runCurrent()

        assertEquals(listOf("Android" to 1, "Android" to 2), fake.requests)
        assertEquals(
            SearchScreenState("Android", "Android", SearchUiState.Content(listOf(first)), SearchAppendState.Error(NewsError.TIMEOUT)),
            viewModel.uiState.value,
        )
        viewModel.retryNextPage()
        runCurrent()
        assertEquals(listOf("Android" to 1, "Android" to 2, "Android" to 2), fake.requests)
    }

    @Test
    fun `a full duplicate page pauses automatic loading until manual continuation`() = runTest(dispatcher) {
        val first = article("first")
        val fake = FakeNewsRepository { _, page ->
            when (page) {
                1, 2 -> NewsPageResult.Success(ArticlePage(listOf(first), 10))
                else -> NewsPageResult.Success(ArticlePage(listOf(article("third")), 1))
            }
        }
        val viewModel = SearchViewModel(fake, SavedStateHandle())

        viewModel.updateQuery("Android")
        advanceTimeBy(400)
        runCurrent()
        viewModel.loadNextPage()
        runCurrent()
        viewModel.loadNextPage()
        runCurrent()

        assertEquals(SearchAppendState.ManualContinue, viewModel.uiState.value.appendState)
        assertEquals(listOf("Android" to 1, "Android" to 2), fake.requests)
        viewModel.retryNextPage()
        runCurrent()
        assertEquals(listOf("Android" to 1, "Android" to 2, "Android" to 3), fake.requests)
    }

    @Test
    fun `a late append response cannot replace results for a new query`() = runTest(dispatcher) {
        val pending = CompletableDeferred<NewsPageResult>()
        val fake = FakeNewsRepository { query, page ->
            when {
                query == "Android" && page == 1 -> NewsPageResult.Success(ArticlePage(listOf(article("android")), 10))
                query == "Android" -> withContext(NonCancellable) { pending.await() }
                else -> NewsPageResult.Success(ArticlePage(listOf(article("kotlin")), 1))
            }
        }
        val viewModel = SearchViewModel(fake, SavedStateHandle())

        viewModel.updateQuery("Android")
        advanceTimeBy(400)
        runCurrent()
        viewModel.loadNextPage()
        runCurrent()
        viewModel.updateQuery("Kotlin")
        advanceTimeBy(400)
        runCurrent()
        pending.complete(NewsPageResult.Success(ArticlePage(listOf(article("stale")), 1)))
        runCurrent()

        assertEquals("Kotlin", viewModel.uiState.value.normalizedQuery)
        assertEquals(SearchUiState.Content(listOf(article("kotlin"))), viewModel.uiState.value.resultState)
    }

    private fun article(id: String) = Article(id, id, null, null, null, null, null, null)

    private class FakeNewsRepository(
        private val response: suspend (String, Int) -> NewsPageResult,
    ) : NewsRepository {
        val queries = mutableListOf<String>()
        val requests = mutableListOf<Pair<String, Int>>()

        override suspend fun getHeadlines(category: NewsCategory, page: Int): NewsPageResult =
            error("Headlines are not expected in SearchViewModelTest")

        override suspend fun search(query: String, page: Int): NewsPageResult {
            queries += query
            requests += query to page
            return response(query, page)
        }
    }
}
