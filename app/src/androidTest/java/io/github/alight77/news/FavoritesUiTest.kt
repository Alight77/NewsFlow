package io.github.alight77.news

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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

    @Test
    fun oneArticleUsesTheSameFavoriteStateAcrossHomeSearchDetailAndFavorites() {
        val article = Article("shared-id", "跨页收藏新闻", null, null, null, null, null, null)
        val favoriteRepository = FakeFavoriteRepository()
        val newsRepository = object : NewsRepository {
            override suspend fun getHeadlines(category: NewsCategory, page: Int): NewsPageResult =
                NewsPageResult.Success(ArticlePage(listOf(article), 1))

            override suspend fun search(query: String): NewsPageResult =
                NewsPageResult.Success(ArticlePage(listOf(article), 1))
        }
        composeRule.setContent { NewsTheme { NewsApp(newsRepository, favoriteRepository) } }

        composeRule.onNodeWithTag("favorite_action_shared-id").performClick()
        composeRule.waitUntil(3_000) { article.id in favoriteRepository.favoriteIds.value }
        composeRule.onNodeWithTag("favorite_action_shared-id")
            .assertContentDescriptionEquals("取消收藏")

        composeRule.onNodeWithText("搜索").performClick()
        composeRule.onNodeWithTag("search_query").performTextInput("shared")
        composeRule.waitUntil(3_000) {
            composeRule.onAllNodesWithText(article.title).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("favorite_action_shared-id")
            .assertContentDescriptionEquals("取消收藏")
        composeRule.onNodeWithText(article.title).performClick()
        composeRule.onNodeWithText("新闻详情").assertIsDisplayed()
        composeRule.onNodeWithTag("favorite_action_shared-id")
            .assertContentDescriptionEquals("取消收藏")
        composeRule.onNodeWithText("返回").performClick()

        composeRule.onNodeWithTag("favorite_action_shared-id").performClick()
        composeRule.waitUntil(3_000) { article.id !in favoriteRepository.favoriteIds.value }
        composeRule.onNodeWithTag("favorite_action_shared-id")
            .assertContentDescriptionEquals("收藏新闻")
        composeRule.onNodeWithText("收藏").performClick()
        composeRule.onNodeWithText("还没有收藏新闻。").assertIsDisplayed()
        composeRule.onNodeWithText("首页").performClick()
        composeRule.onNodeWithTag("favorite_action_shared-id")
            .assertContentDescriptionEquals("收藏新闻")
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
