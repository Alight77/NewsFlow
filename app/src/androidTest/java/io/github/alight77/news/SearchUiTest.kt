package io.github.alight77.news

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
    fun searchShowsTheFirstBatchAndKeepsItAfterOpeningAndLeavingDetail() {
        val article = Article("android-1", "Android 搜索结果", null, null, null, null, null, null)
        val repository = FakeSearchRepository { query ->
            NewsPageResult.Success(ArticlePage(listOf(article.copy(title = "$query 搜索结果")), 1))
        }
        composeRule.setContent { NewsTheme { NewsApp(repository) } }

        composeRule.onNodeWithText("搜索").performClick()
        composeRule.onNodeWithTag("search_query").performTextInput(" Android ")
        composeRule.waitUntil(3_000) { repository.queries.size == 1 }

        composeRule.onNodeWithText("Android 搜索结果").assertIsDisplayed()
        composeRule.onNodeWithText("仅显示首批搜索结果").assertIsDisplayed()
        composeRule.onNodeWithText("Android 搜索结果").performClick()
        composeRule.onNodeWithText("新闻详情").assertIsDisplayed()
        composeRule.onNodeWithText("返回").performClick()
        composeRule.onNodeWithText("Android 搜索结果").assertIsDisplayed()
        assertEquals(listOf("Android"), repository.queries)
    }

    @Test
    fun blankSearchInputDoesNotRequestOrReplaceTheInitialState() {
        val repository = FakeSearchRepository { error("A blank query must not be searched") }
        composeRule.setContent { NewsTheme { NewsApp(repository) } }

        composeRule.onNodeWithText("搜索").performClick()
        composeRule.onNodeWithTag("search_query").performTextInput("   ")
        composeRule.waitForIdle()

        composeRule.onNodeWithText("输入关键词后自动搜索").assertIsDisplayed()
        composeRule.onNodeWithText("正在搜索…").assertDoesNotExist()
        assertEquals(emptyList<String>(), repository.queries)
    }

    private class FakeSearchRepository(
        private val searchResponse: suspend (String) -> NewsPageResult,
    ) : NewsRepository {
        val queries = mutableListOf<String>()

        override suspend fun getHeadlines(category: NewsCategory, page: Int): NewsPageResult =
            NewsPageResult.Success(ArticlePage(emptyList(), 0))

        override suspend fun search(query: String): NewsPageResult {
            queries += query
            return searchResponse(query)
        }
    }
}
