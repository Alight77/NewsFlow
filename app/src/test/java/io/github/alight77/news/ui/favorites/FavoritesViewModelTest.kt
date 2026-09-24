package io.github.alight77.news.ui.favorites

import io.github.alight77.news.domain.model.Article
import io.github.alight77.news.domain.repository.FavoriteRepository
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
class FavoritesViewModelTest {
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
    fun `duplicate toggle waits for one save and then exposes the stored favorite`() = runTest(dispatcher) {
        val saveGate = CompletableDeferred<Unit>()
        val repository = FakeFavoriteRepository().apply {
            saveBehavior = { saveGate.await() }
        }
        val viewModel = FavoritesViewModel(repository)
        val article = article("article-id")
        runCurrent()

        viewModel.toggle(article)
        viewModel.toggle(article)
        runCurrent()

        assertEquals(listOf(article), repository.savedArticles)
        assertEquals(setOf(article.id), viewModel.uiState.value.pendingArticleIds)
        assertEquals(emptySet<String>(), viewModel.uiState.value.favoriteArticleIds)

        saveGate.complete(Unit)
        runCurrent()

        assertEquals(setOf(article.id), viewModel.uiState.value.favoriteArticleIds)
        assertEquals(listOf(article), viewModel.uiState.value.favorites)
        assertEquals(emptySet<String>(), viewModel.uiState.value.pendingArticleIds)
    }

    @Test
    fun `failed save keeps the article unfavorited and emits a failure event`() = runTest(dispatcher) {
        val repository = FakeFavoriteRepository().apply {
            saveBehavior = { throw IOException("storage unavailable") }
        }
        val viewModel = FavoritesViewModel(repository)
        val article = article("article-id")
        val event = async { viewModel.events.first() }
        runCurrent()

        viewModel.toggle(article)
        runCurrent()

        assertEquals(FavoriteEvent.MutationFailed(article.id), event.await())
        assertEquals(emptySet<String>(), viewModel.uiState.value.favoriteArticleIds)
        assertEquals(emptySet<String>(), viewModel.uiState.value.pendingArticleIds)
    }

    @Test
    fun `toggling an existing favorite removes only that article`() = runTest(dispatcher) {
        val first = article("first")
        val second = article("second")
        val repository = FakeFavoriteRepository(listOf(first, second))
        val viewModel = FavoritesViewModel(repository)
        runCurrent()

        viewModel.toggle(first)
        runCurrent()

        assertEquals(listOf(first.id), repository.removedArticleIds)
        assertEquals(setOf(second.id), viewModel.uiState.value.favoriteArticleIds)
        assertEquals(listOf(second), viewModel.uiState.value.favorites)
    }

    private fun article(id: String) = Article(
        id = id,
        title = "Title $id",
        description = null,
        contentPreview = null,
        originalUrl = null,
        imageUrl = null,
        publishedAt = null,
        sourceName = null,
    )

    private class FakeFavoriteRepository(initialFavorites: List<Article> = emptyList()) : FavoriteRepository {
        private val favoriteArticles = MutableStateFlow(initialFavorites)
        private val favoriteIds = MutableStateFlow(initialFavorites.mapTo(linkedSetOf(), Article::id))
        val savedArticles = mutableListOf<Article>()
        val removedArticleIds = mutableListOf<String>()
        var saveBehavior: suspend (Article) -> Unit = {}

        override fun observeFavorites(): Flow<List<Article>> = favoriteArticles

        override fun observeFavoriteArticleIds(): Flow<Set<String>> = favoriteIds

        override suspend fun isFavorite(articleId: String): Boolean = articleId in favoriteIds.value

        override suspend fun save(article: Article) {
            savedArticles += article
            saveBehavior(article)
            favoriteArticles.value = listOf(article) + favoriteArticles.value.filterNot { it.id == article.id }
            favoriteIds.value = favoriteArticles.value.mapTo(linkedSetOf(), Article::id)
        }

        override suspend fun remove(articleId: String) {
            removedArticleIds += articleId
            favoriteArticles.value = favoriteArticles.value.filterNot { it.id == articleId }
            favoriteIds.value = favoriteArticles.value.mapTo(linkedSetOf(), Article::id)
        }
    }
}
