package io.github.alight77.news

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
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
        composeRule.onNodeWithText("输入关键词后自动搜索").assertIsDisplayed()
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

    @Test
    fun scrollingNearTheBottomAppendsArticlesThatCanOpenDetail() {
        val requests = mutableListOf<Int>()
        val nextPage = CompletableDeferred<NewsPageResult>()
        val firstPage = (1..10).map { number ->
            Article("first-$number", "first page story $number", null, null, null, null, null, null)
        }
        val repository = object : NewsRepository {
            override suspend fun getHeadlines(category: NewsCategory, page: Int): NewsPageResult {
                requests += page
                return if (page == 1) NewsPageResult.Success(ArticlePage(firstPage, 10)) else nextPage.await()
            }
        }
        composeRule.setContent { NewsTheme { NewsApp(repository) } }
        composeRule.onNodeWithText("first page story 1").assertIsDisplayed()

        composeRule.onNodeWithTag("home_articles").performTouchInput { swipeUp() }
        composeRule.waitUntil(5_000) { requests.size == 2 }
        composeRule.onNodeWithTag("home_articles").performScrollToNode(hasText("正在加载更多…"))
        composeRule.onNodeWithText("正在加载更多…").assertIsDisplayed()
        assertEquals(listOf(1, 2), requests)

        nextPage.complete(
            NewsPageResult.Success(
                ArticlePage(listOf(Article("second", "page two story", null, null, null, null, null, null)), 1),
            ),
        )
        composeRule.onNodeWithTag("home_articles").performScrollToNode(hasText("page two story"))
        composeRule.onNodeWithText("page two story").performClick()
        composeRule.onNodeWithText("新闻详情").assertIsDisplayed()
    }

    @Test
    fun failedAppendKeepsArticlesAndWaitsForExplicitRetry() {
        val requests = mutableListOf<Int>()
        val firstPage = (1..10).map { number ->
            Article("first-$number", "first page story $number", null, null, null, null, null, null)
        }
        val repository = object : NewsRepository {
            override suspend fun getHeadlines(category: NewsCategory, page: Int): NewsPageResult {
                requests += page
                return when (requests.size) {
                    1 -> NewsPageResult.Success(ArticlePage(firstPage, 10))
                    2 -> NewsPageResult.Failure(NewsError.CONNECTION)
                    else -> NewsPageResult.Success(
                        ArticlePage(listOf(Article("next", "retried page story", null, null, null, null, null, null)), 1),
                    )
                }
            }
        }
        composeRule.setContent { NewsTheme { NewsApp(repository) } }
        composeRule.onNodeWithText("first page story 1").assertIsDisplayed()

        composeRule.onNodeWithTag("home_articles").performTouchInput { swipeUp() }
        composeRule.waitUntil(5_000) { requests.size == 2 }
        composeRule.onNodeWithTag("home_articles").performScrollToNode(hasText("重试加载"))
        composeRule.onNodeWithText("加载更多失败：网络连接失败，请检查网络后重试。").assertIsDisplayed()
        assertEquals(listOf(1, 2), requests)

        composeRule.onNodeWithText("重试加载").performClick()
        composeRule.waitUntil(5_000) { requests.size == 3 }
        composeRule.onNodeWithTag("home_articles").performScrollToNode(hasText("retried page story"))
        composeRule.onNodeWithText("retried page story").assertIsDisplayed()
        assertEquals(listOf(1, 2, 2), requests)
    }

    @Test
    fun aShortVisibleListCanStillRequestItsNextRawPage() {
        val requests = mutableListOf<Int>()
        val repository = object : NewsRepository {
            override suspend fun getHeadlines(category: NewsCategory, page: Int): NewsPageResult {
                requests += page
                return if (page == 1) NewsPageResult.Success(
                    ArticlePage(listOf(Article("first", "only visible story", null, null, null, null, null, null)), 10),
                ) else NewsPageResult.Success(
                    ArticlePage(listOf(Article("second", "next visible story", null, null, null, null, null, null)), 1),
                )
            }
        }
        composeRule.setContent { NewsTheme { NewsApp(repository) } }
        composeRule.onNodeWithText("only visible story").assertIsDisplayed()

        composeRule.onNodeWithTag("home_articles").performTouchInput { swipeUp() }
        composeRule.waitUntil(5_000) { requests.size == 2 }
        composeRule.onNodeWithText("next visible story").assertIsDisplayed()
        assertEquals(listOf(1, 2), requests)
    }

    @Test
    fun duplicateOnlyPageOffersManualContinuation() {
        val requests = mutableListOf<Int>()
        val original = Article("first", "original story", null, null, null, null, null, null)
        val repository = object : NewsRepository {
            override suspend fun getHeadlines(category: NewsCategory, page: Int): NewsPageResult {
                requests += page
                return when (page) {
                    1, 2 -> NewsPageResult.Success(ArticlePage(listOf(original), 10))
                    3 -> NewsPageResult.Success(
                        ArticlePage(listOf(Article("third", "third page story", null, null, null, null, null, null)), 1),
                    )
                    else -> error("Unexpected page $page")
                }
            }
        }
        composeRule.setContent { NewsTheme { NewsApp(repository) } }
        composeRule.onNodeWithText("original story").assertIsDisplayed()

        composeRule.onNodeWithTag("home_articles").performTouchInput { swipeUp() }
        composeRule.waitUntil(5_000) { requests.size == 2 }
        composeRule.onNodeWithText("本页暂无新文章").assertIsDisplayed()
        assertEquals(listOf(1, 2), requests)

        composeRule.onNodeWithText("加载下一页").performClick()
        composeRule.onNodeWithText("third page story").assertIsDisplayed()
        assertEquals(listOf(1, 2, 3), requests)
    }

    @Test
    fun refreshingAfterAppendReplacesTheVisibleSessionList() {
        val requests = mutableListOf<Int>()
        val refreshed = CompletableDeferred<NewsPageResult>()
        val firstPage = (1..10).map { number ->
            Article("old-$number", "old story $number", null, null, null, null, null, null)
        }
        val repository = object : NewsRepository {
            override suspend fun getHeadlines(category: NewsCategory, page: Int): NewsPageResult {
                requests += page
                return when (requests.size) {
                    1 -> NewsPageResult.Success(ArticlePage(firstPage, 10))
                    2 -> NewsPageResult.Success(
                        ArticlePage(listOf(Article("old-next", "old page two", null, null, null, null, null, null)), 1),
                    )
                    else -> refreshed.await()
                }
            }
        }
        composeRule.setContent { NewsTheme { NewsApp(repository) } }
        composeRule.onNodeWithText("old story 1").assertIsDisplayed()
        composeRule.onNodeWithTag("home_articles").performTouchInput { swipeUp() }
        composeRule.waitUntil(5_000) { requests.size == 2 }
        composeRule.onNodeWithTag("home_articles").performScrollToNode(hasText("old page two"))
        composeRule.onNodeWithText("old page two").assertIsDisplayed()

        composeRule.onNodeWithTag("home_articles").performScrollToIndex(0)
        composeRule.onNodeWithTag("home_articles").performTouchInput { swipeDown() }
        composeRule.waitUntil(5_000) { requests.size == 3 }
        composeRule.onNodeWithText("old story 1").assertIsDisplayed()

        refreshed.complete(
            NewsPageResult.Success(
                ArticlePage(listOf(Article("fresh", "fresh first page", null, null, null, null, null, null)), 1),
            ),
        )
        composeRule.onNodeWithText("fresh first page").assertIsDisplayed()
        composeRule.onNodeWithText("old story 1").assertDoesNotExist()
        assertEquals(listOf(1, 2, 1), requests)
    }

    @Test
    fun failedRefreshAfterAppendKeepsArticlesAndAllowsTheNextPage() {
        val requests = mutableListOf<Int>()
        var firstPageAttempts = 0
        var refreshFinished = false
        val pendingOldAppend = CompletableDeferred<NewsPageResult>()
        val firstPage = (1..10).map { number ->
            Article("first-$number", "first story $number", null, null, null, null, null, null)
        }
        val repository = object : NewsRepository {
            override suspend fun getHeadlines(category: NewsCategory, page: Int): NewsPageResult {
                requests += page
                return when (page) {
                    1 -> if (++firstPageAttempts == 1) NewsPageResult.Success(ArticlePage(firstPage, 10))
                        else {
                            refreshFinished = true
                            NewsPageResult.Failure(NewsError.CONNECTION)
                        }
                    2 -> NewsPageResult.Success(
                        ArticlePage(listOf(Article("second", "second page story", null, null, null, null, null, null)), 10),
                    )
                    3 -> if (refreshFinished) NewsPageResult.Success(
                        ArticlePage(listOf(Article("third", "third page story", null, null, null, null, null, null)), 1),
                    ) else pendingOldAppend.await()
                    else -> error("Unexpected page $page")
                }
            }
        }
        composeRule.setContent { NewsTheme { NewsApp(repository) } }
        composeRule.onNodeWithText("first story 1").assertIsDisplayed()
        composeRule.onNodeWithTag("home_articles").performTouchInput { swipeUp() }
        composeRule.waitUntil(5_000) { requests.size == 2 }

        composeRule.onNodeWithTag("home_articles").performScrollToIndex(0)
        composeRule.onNodeWithText("first story 1").assertIsDisplayed()
        composeRule.onNodeWithTag("home_articles").performTouchInput { swipeDown() }
        composeRule.waitUntil(5_000) { refreshFinished }
        composeRule.onNodeWithText("刷新失败：网络连接失败，请检查网络后重试。").assertIsDisplayed()
        composeRule.onNodeWithText("first story 1").assertIsDisplayed()

        val priorThirdPageAttempts = requests.count { it == 3 }
        composeRule.onNodeWithTag("home_articles").performTouchInput { swipeUp() }
        composeRule.waitUntil(5_000) { requests.count { it == 3 } > priorThirdPageAttempts }
        composeRule.onNodeWithTag("home_articles").performScrollToNode(hasText("second page story"))
        composeRule.onNodeWithText("second page story").assertIsDisplayed()
        composeRule.onNodeWithTag("home_articles").performScrollToNode(hasText("third page story"))
        composeRule.onNodeWithText("third page story").assertIsDisplayed()
        assertEquals(2, requests.count { it == 1 })
        assertEquals(3, requests.last())
    }
}
