package io.github.alight77.news

import android.content.pm.ActivityInfo
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.SideEffect
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalContext
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
import io.github.alight77.news.domain.repository.NewsRepository
import io.github.alight77.news.ui.NewsApp
import io.github.alight77.news.ui.theme.NewsTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SearchStateRetentionUiTest {
    @get:Rule val composeRule = createComposeRule()

    private lateinit var activity: Activity

    @After
    fun resetOrientation() {
        if (::activity.isInitialized) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    @Test
    fun configurationChangeKeepsSearchInputAndResultsWithoutAnotherRequest() {
        val repository = FakeSearchRepository()
        setSearchContent(repository)
        enterSearchAndWaitForResult(repository)

        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        setSearchContent(repository)
        waitForResultToReturn()

        composeRule.onNodeWithText("Android 搜索结果").assertIsDisplayed()
        assertSearchInputRetained()
        assertEquals(listOf("Android"), repository.queries)
    }

    @Test
    fun bottomNavigationRoundTripKeepsSearchResultsWithoutAnotherRequest() {
        val repository = FakeSearchRepository()
        setSearchContent(repository)
        enterSearchAndWaitForResult(repository)

        composeRule.onNodeWithText("首页").performClick()
        composeRule.onNodeWithText("搜索").performClick()

        composeRule.onNodeWithText("Android 搜索结果").assertIsDisplayed()
        assertSearchInputRetained()
        assertEquals(listOf("Android"), repository.queries)
    }

    private fun setSearchContent(repository: FakeSearchRepository) {
        composeRule.setContent {
            val currentActivity = LocalContext.current.findActivity()
            SideEffect { activity = currentActivity }
            NewsTheme {
                NewsApp(repository)
            }
        }
    }

    private fun enterSearchAndWaitForResult(repository: FakeSearchRepository) {
        composeRule.onNodeWithText("搜索").performClick()
        composeRule.onNodeWithTag("search_query").performTextInput(" Android ")
        composeRule.waitUntil(3_000) { repository.queries.size == 1 }
        waitForResultToReturn()
    }

    private fun waitForResultToReturn() {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Android 搜索结果").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertSearchInputRetained() {
        composeRule.onNodeWithTag("search_query").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(" Android ")),
        )
    }
}

private class FakeSearchRepository : NewsRepository {
    val queries = mutableListOf<String>()

    override suspend fun getHeadlines(category: NewsCategory, page: Int): NewsPageResult =
        NewsPageResult.Success(ArticlePage(emptyList(), 0))

    override suspend fun search(query: String): NewsPageResult {
        queries += query
        return NewsPageResult.Success(
            ArticlePage(
                articles = listOf(Article("android", "$query 搜索结果", null, null, null, null, null, null)),
                rawArticleCount = 1,
            ),
        )
    }
}

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("Search test requires an Activity context.")
}
