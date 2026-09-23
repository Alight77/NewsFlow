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
}
