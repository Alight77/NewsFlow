package io.github.alight77.news

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.alight77.news.domain.model.Article
import io.github.alight77.news.domain.model.ArticlePage
import io.github.alight77.news.domain.model.NewsCategory
import io.github.alight77.news.domain.model.NewsError
import io.github.alight77.news.domain.model.NewsPageResult
import io.github.alight77.news.domain.repository.CachedHomeFirstPage
import io.github.alight77.news.domain.repository.FavoriteRepository
import io.github.alight77.news.domain.repository.HomeFirstPageCache
import io.github.alight77.news.domain.repository.NewsRepository
import io.github.alight77.news.ui.NewsApp
import io.github.alight77.news.ui.theme.NewsTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeCacheUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun freshHomeCacheDisplaysWithoutRequestingNetwork() {
        val article = Article("cached-id", "缓存首页", null, null, null, null, null, "缓存来源")
        val repository = object : NewsRepository {
            var requestCount = 0

            override suspend fun getHeadlines(category: NewsCategory, page: Int): NewsPageResult {
                requestCount++
                return NewsPageResult.Failure(NewsError.CONNECTION)
            }
        }
        val cache = object : HomeFirstPageCache {
            override suspend fun read(category: NewsCategory): CachedHomeFirstPage? =
                CachedHomeFirstPage(listOf(article), System.currentTimeMillis())

            override suspend fun replace(
                category: NewsCategory,
                articles: List<Article>,
                fetchedAtEpochMillis: Long,
            ) = Unit
        }
        composeRule.setContent {
            NewsTheme {
                NewsApp(repository, EmptyFavoriteRepository, cache)
            }
        }

        composeRule.onNodeWithText(article.title).assertIsDisplayed()
        assertEquals(0, repository.requestCount)
    }

    @Test
    fun expiredEmptyHomeCacheShowsRefreshFailureAndCanRetry() {
        val fresh = Article("fresh-id", "重试后的首页", null, null, null, null, null, "网络来源")
        val repository = object : NewsRepository {
            var requestCount = 0

            override suspend fun getHeadlines(category: NewsCategory, page: Int): NewsPageResult =
                if (++requestCount == 1) NewsPageResult.Failure(NewsError.CONNECTION)
                else NewsPageResult.Success(ArticlePage(listOf(fresh), 1))
        }
        val cache = object : HomeFirstPageCache {
            override suspend fun read(category: NewsCategory): CachedHomeFirstPage =
                CachedHomeFirstPage(emptyList(), fetchedAtEpochMillis = 0)

            override suspend fun replace(
                category: NewsCategory,
                articles: List<Article>,
                fetchedAtEpochMillis: Long,
            ) = Unit
        }
        composeRule.setContent {
            NewsTheme {
                NewsApp(repository, EmptyFavoriteRepository, cache)
            }
        }

        composeRule.onNodeWithText("暂无新闻。").assertIsDisplayed()
        composeRule.onNodeWithText("刷新失败：网络连接失败，请检查网络后重试。").assertIsDisplayed()
        composeRule.onNodeWithText("重试刷新").performClick()

        composeRule.onNodeWithText(fresh.title).assertIsDisplayed()
        assertEquals(2, repository.requestCount)
    }

    private object EmptyFavoriteRepository : FavoriteRepository {
        override fun observeFavorites(): Flow<List<Article>> = flowOf(emptyList())

        override fun observeFavoriteArticleIds(): Flow<Set<String>> = flowOf(emptySet())

        override suspend fun isFavorite(articleId: String): Boolean = false

        override suspend fun save(article: Article) = Unit

        override suspend fun remove(articleId: String) = Unit
    }
}
