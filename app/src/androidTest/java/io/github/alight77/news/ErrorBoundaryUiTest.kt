package io.github.alight77.news

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.alight77.news.domain.model.Article
import io.github.alight77.news.domain.model.ArticlePage
import io.github.alight77.news.domain.model.NewsCategory
import io.github.alight77.news.domain.model.NewsError
import io.github.alight77.news.domain.model.NewsPageResult
import io.github.alight77.news.domain.repository.NewsRepository
import io.github.alight77.news.ui.NewsApp
import io.github.alight77.news.ui.components.ArticleImage
import io.github.alight77.news.ui.theme.NewsTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ErrorBoundaryUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun failedImageRequestShowsTheUnavailablePlaceholder() {
        composeRule.setContent {
            NewsTheme { ArticleImage("https://127.0.0.1:1/missing-image.jpg") }
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("image_load_failed").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("image_load_failed").assertIsDisplayed()
        composeRule.onNodeWithText("图片暂不可用").assertIsDisplayed()
    }

    @Test
    fun invalidHomeDataShowsSafeErrorAndOptionalFieldsRemainReadableAfterRetry() {
        val requests = AtomicInteger()
        val article = Article("minimal", "可阅读的新闻", null, null, null, null, null, null)
        val repository = object : NewsRepository {
            override suspend fun getHeadlines(category: NewsCategory, page: Int): NewsPageResult {
                assertEquals(NewsCategory.GENERAL, category)
                assertEquals(1, page)
                return if (requests.incrementAndGet() == 1) {
                    NewsPageResult.Failure(NewsError.INVALID_DATA)
                } else {
                    NewsPageResult.Success(ArticlePage(listOf(article), 1))
                }
            }
        }
        composeRule.setContent { NewsTheme { NewsApp(repository) } }

        composeRule.onNodeWithText("暂时无法读取新闻数据。").assertIsDisplayed()
        composeRule.onNodeWithText("重新加载").performClick()
        composeRule.onNodeWithText(article.title).assertIsDisplayed().performClick()

        composeRule.onNodeWithText(article.title).assertIsDisplayed()
        composeRule.onNodeWithText("图片暂不可用").assertIsDisplayed()
        composeRule.onNodeWithText("未知来源").assertIsDisplayed()
        composeRule.onNodeWithText("时间未知").assertIsDisplayed()
        composeRule.onNodeWithText("原文链接不可用。").assertIsDisplayed()
        composeRule.onNodeWithText("阅读原文").assertDoesNotExist()
        assertEquals(2, requests.get())
    }

    @Test
    fun searchRateLimitAndTimeoutShowFriendlyErrorsAndAllowRetry() {
        val searchAttempts = AtomicInteger()
        val article = Article("recovered", "恢复的搜索结果", null, null, null, null, null, null)
        val repository = object : NewsRepository {
            override suspend fun getHeadlines(category: NewsCategory, page: Int): NewsPageResult =
                NewsPageResult.Success(ArticlePage(emptyList(), 0))

            override suspend fun search(query: String, page: Int): NewsPageResult {
                assertEquals("Android", query)
                return when (searchAttempts.incrementAndGet()) {
                    1 -> NewsPageResult.Failure(NewsError.RATE_LIMITED)
                    2 -> NewsPageResult.Failure(NewsError.TIMEOUT)
                    else -> NewsPageResult.Success(ArticlePage(listOf(article), 1))
                }
            }
        }
        composeRule.setContent { NewsTheme { NewsApp(repository) } }

        composeRule.onNodeWithText("搜索").performClick()
        composeRule.onNodeWithTag("search_query").performTextInput(" Android ")
        composeRule.waitUntil(3_000) { searchAttempts.get() == 1 }
        composeRule.onNodeWithText("请求过于频繁，请稍后重试。").assertIsDisplayed()
        composeRule.onNodeWithText("重新加载").performClick()
        composeRule.waitUntil(3_000) { searchAttempts.get() == 2 }
        composeRule.onNodeWithText("请求超时，请稍后重试。").assertIsDisplayed()
        composeRule.onNodeWithText("重新加载").performClick()
        composeRule.waitUntil(3_000) { searchAttempts.get() == 3 }
        composeRule.onNodeWithText(article.title).assertIsDisplayed()
        assertEquals(3, searchAttempts.get())
    }
}
