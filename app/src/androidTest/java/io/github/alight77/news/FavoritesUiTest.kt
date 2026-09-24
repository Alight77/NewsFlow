package io.github.alight77.news

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.alight77.news.domain.model.Article
import io.github.alight77.news.domain.model.ArticlePage
import io.github.alight77.news.domain.model.NewsCategory
import io.github.alight77.news.domain.model.NewsPageResult
import io.github.alight77.news.domain.repository.FavoriteRepository
import io.github.alight77.news.domain.repository.NewsRepository
import io.github.alight77.news.ui.NewsApp
import io.github.alight77.news.ui.theme.NewsTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class FavoritesUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun homeFavoriteAppearsInFavoritesAndDetailKeepsItsSnapshotAfterRemoval() {
        val article = Article("article-id", "收藏新闻", null, null, null, null, null, null)
        val favoriteRepository = FakeFavoriteRepository()
        val newsRepository = object : NewsRepository {
            override suspend fun getHeadlines(category: NewsCategory, page: Int): NewsPageResult =
                NewsPageResult.Success(ArticlePage(listOf(article), 1))
        }
        composeRule.setContent {
            NewsTheme {
                NewsApp(newsRepository, favoriteRepository)
            }
        }

        composeRule.onNodeWithTag("favorite_action_article-id").performClick()
        composeRule.waitUntil(3_000) { article.id in favoriteRepository.favoriteIds.value }

        composeRule.onNodeWithText("收藏").performClick()
        composeRule.onNodeWithText(article.title).assertIsDisplayed().performClick()
        composeRule.onNodeWithText("新闻详情").assertIsDisplayed()

        composeRule.onNodeWithTag("favorite_action_article-id").performClick()
        composeRule.waitUntil(3_000) { article.id !in favoriteRepository.favoriteIds.value }

        composeRule.onNodeWithText(article.title).assertIsDisplayed()
        composeRule.onNodeWithText("返回").performClick()
        composeRule.onNodeWithText("还没有收藏新闻。").assertIsDisplayed()
    }

    private class FakeFavoriteRepository : FavoriteRepository {
        private val favorites = MutableStateFlow(emptyList<Article>())
        val favoriteIds = MutableStateFlow(emptySet<String>())

        override fun observeFavorites(): Flow<List<Article>> = favorites

        override fun observeFavoriteArticleIds(): Flow<Set<String>> = favoriteIds

        override suspend fun isFavorite(articleId: String): Boolean = articleId in favoriteIds.value

        override suspend fun save(article: Article) {
            favorites.value = listOf(article) + favorites.value.filterNot { it.id == article.id }
            favoriteIds.value = favorites.value.mapTo(linkedSetOf(), Article::id)
        }

        override suspend fun remove(articleId: String) {
            favorites.value = favorites.value.filterNot { it.id == articleId }
            favoriteIds.value = favorites.value.mapTo(linkedSetOf(), Article::id)
        }
    }
}
