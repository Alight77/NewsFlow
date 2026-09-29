package io.github.alight77.news

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import io.github.alight77.news.domain.model.Article
import io.github.alight77.news.domain.model.ArticlePage
import io.github.alight77.news.domain.model.NewsCategory
import io.github.alight77.news.domain.model.NewsPageResult
import io.github.alight77.news.domain.repository.NewsRepository
import io.github.alight77.news.ui.NewsApp
import io.github.alight77.news.ui.theme.NewsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SearchUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun searchShowsResultsAndKeepsThemAfterOpeningAndLeavingDetail() {
        val article = Article("android-1", "Android 搜索结果", null, null, null, null, null, null)
        val repository = FakeSearchRepository { query, _ ->
            NewsPageResult.Success(ArticlePage(listOf(article.copy(title = "$query 搜索结果")), 1))
        }
        composeRule.setContent { NewsTheme { NewsApp(repository) } }

        composeRule.onNodeWithText("搜索").performClick()
        composeRule.onNodeWithTag("search_query").performTextInput(" Android ")
        composeRule.waitUntil(3_000) { repository.queries.size == 1 }

        composeRule.onNodeWithText("Android 搜索结果").assertIsDisplayed()
        composeRule.onNodeWithText("没有更多搜索结果").assertIsDisplayed()
        composeRule.onNodeWithText("Android 搜索结果").performClick()
        composeRule.onNodeWithText("新闻详情").assertIsDisplayed()
        composeRule.onNodeWithText("返回").performClick()
        composeRule.onNodeWithText("Android 搜索结果").assertIsDisplayed()
        assertEquals(listOf("Android"), repository.queries)
    }

    @Test
    fun blankSearchInputDoesNotRequestOrReplaceTheInitialState() {
        val repository = FakeSearchRepository { _, _ -> error("A blank query must not be searched") }
        composeRule.setContent { NewsTheme { NewsApp(repository) } }

        composeRule.onNodeWithText("搜索").performClick()
        composeRule.onNodeWithTag("search_query").performTextInput("   ")
        composeRule.waitForIdle()

        composeRule.onNodeWithText("输入关键词后自动搜索").assertIsDisplayed()
        composeRule.onNodeWithText("正在搜索…").assertDoesNotExist()
        assertEquals(emptyList<String>(), repository.queries)
    }

    @Test
    fun scrollingSearchResultsLoadsTheNextPageAndKeepsTheFirstPage() {
        val firstPage = (1..10).map { Article("android-$it", "Android $it", null, null, null, null, null, null) }
        val nextArticle = Article("android-11", "Android 11", null, null, null, null, null, null)
        val repository = FakeSearchRepository { _, page ->
            NewsPageResult.Success(if (page == 1) ArticlePage(firstPage, 10) else ArticlePage(listOf(nextArticle), 1))
        }
        composeRule.setContent { NewsTheme { NewsApp(repository) } }

        composeRule.onNodeWithText("搜索").performClick()
        composeRule.onNodeWithTag("search_query").performTextInput("Android")
        composeRule.waitUntil(3_000) { repository.requests.size == 1 }
        composeRule.onNodeWithTag("search_articles").performTouchInput { swipeUp() }
        composeRule.waitUntil(3_000) { repository.requests.size == 2 }
        composeRule.onNodeWithTag("search_articles").performScrollToNode(hasText("Android 11"))

        composeRule.onNodeWithText("Android 11").assertIsDisplayed()
        assertEquals(listOf("Android" to 1, "Android" to 2), repository.requests)
    }

    @Test
    fun aShortVisibleResultListStillAllowsLoadingTheNextRemotePage() {
        val repository = FakeSearchRepository { _, page ->
            val article = Article("android-$page", "Android $page", null, null, null, null, null, null)
            NewsPageResult.Success(ArticlePage(listOf(article), if (page == 1) 10 else 1))
        }
        composeRule.setContent { NewsTheme { NewsApp(repository) } }

        composeRule.onNodeWithText("搜索").performClick()
        composeRule.onNodeWithTag("search_query").performTextInput("Android")
        composeRule.waitUntil(3_000) { repository.requests.size == 1 }
        composeRule.onNodeWithText("加载更多搜索结果").performClick()
        composeRule.waitUntil(3_000) { repository.requests.size == 2 }

        composeRule.onNodeWithText("Android 2").assertIsDisplayed()
        assertEquals(listOf("Android" to 1, "Android" to 2), repository.requests)
    }

    private class FakeSearchRepository(
        private val searchResponse: suspend (String, Int) -> NewsPageResult,
    ) : NewsRepository {
        val queries = mutableListOf<String>()
        val requests = mutableListOf<Pair<String, Int>>()

        override suspend fun getHeadlines(category: NewsCategory, page: Int): NewsPageResult =
            NewsPageResult.Success(ArticlePage(emptyList(), 0))

        override suspend fun search(query: String, page: Int): NewsPageResult {
            queries += query
            requests += query to page
            return searchResponse(query, page)
        }
    }
}
