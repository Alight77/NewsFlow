package io.github.alight77.news

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import io.github.alight77.news.domain.model.Article
import io.github.alight77.news.domain.model.ArticlePage
import io.github.alight77.news.domain.model.NewsCategory
import io.github.alight77.news.domain.model.NewsError
import io.github.alight77.news.domain.model.NewsPageResult
import io.github.alight77.news.domain.repository.NewsRepository
import io.github.alight77.news.ui.NewsApp
import io.github.alight77.news.ui.theme.NewsTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeCategoryUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun switchingTabsAndBottomNavigationRetainsTheSelectedCategoryAndItsContent() {
        val requests = mutableListOf<NewsCategory>()
        val repository = object : NewsRepository {
            override suspend fun getHeadlines(category: NewsCategory, page: Int): NewsPageResult {
                requests += category
                val article = Article(category.apiValue, "${category.apiValue} story", null, null, null, null, null, null)
                return NewsPageResult.Success(ArticlePage(listOf(article), 1))
            }
        }
        composeRule.setContent { NewsTheme { NewsApp(repository) } }

        composeRule.onNodeWithText("general story").assertIsDisplayed()
        composeRule.onNodeWithText("科技").performClick()
        composeRule.onNodeWithText("technology story").assertIsDisplayed()
        composeRule.onNodeWithText("general story").assertDoesNotExist()

        composeRule.onNodeWithText("搜索").performClick()
        composeRule.onNodeWithText("搜索功能将在后续阶段接入。").assertIsDisplayed()
        composeRule.onNodeWithText("首页").performClick()
        composeRule.onNodeWithText("technology story").assertIsDisplayed()
        composeRule.onNodeWithText("综合").performClick()
        composeRule.onNodeWithText("general story").assertIsDisplayed()

        composeRule.runOnIdle {
            assertEquals(listOf(NewsCategory.GENERAL, NewsCategory.TECHNOLOGY), requests)
        }
    }

    @Test
    fun retryUsesTheCategoryWhoseErrorIsVisible() {
        val requests = mutableListOf<NewsCategory>()
        var technologyAttempts = 0
        val repository = object : NewsRepository {
            override suspend fun getHeadlines(category: NewsCategory, page: Int): NewsPageResult {
                requests += category
                if (category == NewsCategory.TECHNOLOGY && ++technologyAttempts == 1) {
                    return NewsPageResult.Failure(NewsError.CONNECTION)
                }
                val article = Article(category.apiValue, "${category.apiValue} story", null, null, null, null, null, null)
                return NewsPageResult.Success(ArticlePage(listOf(article), 1))
            }
        }
        composeRule.setContent { NewsTheme { NewsApp(repository) } }

        composeRule.onNodeWithText("科技").performClick()
        composeRule.onNodeWithText("网络连接失败，请检查网络后重试。").assertIsDisplayed()
        composeRule.onNodeWithText("重新加载").performClick()
        composeRule.onNodeWithText("technology story").assertIsDisplayed()

        composeRule.runOnIdle {
            assertEquals(
                listOf(NewsCategory.GENERAL, NewsCategory.TECHNOLOGY, NewsCategory.TECHNOLOGY),
                requests,
            )
        }
    }

    @Test
    fun pullingTheListRefreshesTheCurrentCategoryWithoutHidingItsArticles() {
        val requests = mutableListOf<Pair<NewsCategory, Int>>()
        val refreshed = CompletableDeferred<NewsPageResult>()
        val repository = object : NewsRepository {
            override suspend fun getHeadlines(category: NewsCategory, page: Int): NewsPageResult {
                requests += category to page
                val article = if (requests.size == 1) Article("old", "original story", null, null, null, null, null, null)
                else return refreshed.await()
                return NewsPageResult.Success(ArticlePage(listOf(article), 1))
            }
        }
        composeRule.setContent { NewsTheme { NewsApp(repository) } }
        composeRule.onNodeWithText("original story").assertIsDisplayed()

        composeRule.onNodeWithTag("home_articles").performTouchInput { swipeDown() }
        composeRule.waitUntil(5_000) { requests.size == 2 }
        composeRule.onNodeWithText("original story").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(listOf(NewsCategory.GENERAL to 1, NewsCategory.GENERAL to 1), requests)
        }

        refreshed.complete(
            NewsPageResult.Success(
                ArticlePage(listOf(Article("new", "replacement story", null, null, null, null, null, null)), 1),
            ),
        )
        composeRule.onNodeWithText("replacement story").assertIsDisplayed()
    }

    @Test
    fun successfulRefreshShowsFeedbackWhenTheArticlesAreUnchanged() {
        val article = Article("same", "unchanged story", null, null, null, null, null, null)
        var requests = 0
        val refreshed = CompletableDeferred<NewsPageResult>()
        val repository = object : NewsRepository {
            override suspend fun getHeadlines(category: NewsCategory, page: Int): NewsPageResult {
                requests++
                return if (requests == 1) NewsPageResult.Success(ArticlePage(listOf(article), 1))
                else refreshed.await()
            }
        }
        composeRule.setContent { NewsTheme { NewsApp(repository) } }
        composeRule.onNodeWithText("unchanged story").assertIsDisplayed()

        composeRule.onNodeWithTag("home_articles").performTouchInput { swipeDown() }
        composeRule.waitUntil(5_000) { requests == 2 }
        refreshed.complete(NewsPageResult.Success(ArticlePage(listOf(article), 1)))

        composeRule.onNodeWithText("unchanged story").assertIsDisplayed()
        composeRule.onNodeWithText("已刷新").assertIsDisplayed()
    }

    @Test
    fun failedPullRefreshShowsRetryWithoutReplacingTheCurrentList() {
        var attempts = 0
        val repository = object : NewsRepository {
            override suspend fun getHeadlines(category: NewsCategory, page: Int): NewsPageResult =
                when (++attempts) {
                    1 -> NewsPageResult.Success(
                        ArticlePage(listOf(Article("old", "original story", null, null, null, null, null, null)), 1),
                    )
                    2 -> NewsPageResult.Failure(NewsError.CONNECTION)
                    else -> NewsPageResult.Success(
                        ArticlePage(listOf(Article("new", "replacement story", null, null, null, null, null, null)), 1),
                    )
                }
        }
        composeRule.setContent { NewsTheme { NewsApp(repository) } }
        composeRule.onNodeWithText("original story").assertIsDisplayed()

        composeRule.onNodeWithTag("home_articles").performTouchInput { swipeDown() }
        composeRule.onNodeWithText("original story").assertIsDisplayed()
        composeRule.onNodeWithText("刷新失败：网络连接失败，请检查网络后重试。").assertIsDisplayed()
        composeRule.onNodeWithText("重试刷新").performClick()
        composeRule.onNodeWithText("replacement story").assertIsDisplayed()
    }
}
