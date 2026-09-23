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
import io.github.alight77.news.domain.repository.NewsRepository
import io.github.alight77.news.ui.NewsApp
import io.github.alight77.news.ui.theme.NewsTheme
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class Phase2UiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun loadingIsVisibleUntilTheFirstPageCompletes() {
        val response = CompletableDeferred<NewsPageResult>()
        val repository = object : NewsRepository {
            override suspend fun getHeadlines(category: NewsCategory, page: Int): NewsPageResult =
                response.await()
        }
        composeRule.setContent { NewsTheme { NewsApp(repository) } }

        composeRule.onNodeWithText("正在加载新闻…").assertIsDisplayed()
        response.complete(NewsPageResult.Success(ArticlePage(emptyList(), 0)))
        composeRule.onNodeWithText("暂无新闻。").assertIsDisplayed()
    }

    @Test
    fun articleDetailShowsTheOpenedArticleAndReturnsToTheSameHomeList() {
        val article = Article("first/with?reserved#key", "Test headline", null, null, null, null, null, null)
        val repository = FakeNewsRepository(
            listOf(NewsPageResult.Success(ArticlePage(listOf(article), 1))),
        )
        composeRule.setContent { NewsTheme { NewsApp(repository) } }

        composeRule.onNodeWithText("Test headline").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("新闻详情").assertIsDisplayed()
        composeRule.onNodeWithText("Test headline").assertIsDisplayed()
        composeRule.onNodeWithText("原文链接不可用。").assertIsDisplayed()
        composeRule.onNodeWithText("阅读原文").assertDoesNotExist()

        composeRule.onNodeWithText("返回").performClick()
        composeRule.onNodeWithText("Test headline").assertIsDisplayed()
        assertEquals(1, repository.requests.get())
    }

    @Test
    fun emptyFirstPageShowsReloadAction() {
        val repository = FakeNewsRepository(
            listOf(NewsPageResult.Success(ArticlePage(emptyList(), 0))),
        )
        composeRule.setContent { NewsTheme { NewsApp(repository) } }

        composeRule.onNodeWithText("暂无新闻。").assertIsDisplayed()
        composeRule.onNodeWithText("重新加载").assertIsDisplayed()
    }

    @Test
    fun firstPageErrorCanRetryAndShowContent() {
        val article = Article("recovered", "Recovered headline", null, null, null, null, null, null)
        val repository = FakeNewsRepository(
            listOf(
                NewsPageResult.Failure(NewsError.CONNECTION),
                NewsPageResult.Success(ArticlePage(listOf(article), 1)),
            ),
        )
        composeRule.setContent { NewsTheme { NewsApp(repository) } }

        composeRule.onNodeWithText("网络连接失败，请检查网络后重试。").assertIsDisplayed()
        composeRule.onNodeWithText("重新加载").performClick()
        composeRule.onNodeWithText("Recovered headline").assertIsDisplayed()
        assertEquals(2, repository.requests.get())
    }

    private class FakeNewsRepository(private val results: List<NewsPageResult>) : NewsRepository {
        val requests = AtomicInteger()

        override suspend fun getHeadlines(category: NewsCategory, page: Int): NewsPageResult {
            require(category == NewsCategory.GENERAL && page == 1)
            val index = requests.getAndIncrement().coerceAtMost(results.lastIndex)
            return results[index]
        }
    }
}
