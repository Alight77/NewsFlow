package io.github.alight77.news

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import io.github.alight77.news.domain.model.Article
import io.github.alight77.news.domain.model.ArticlePage
import io.github.alight77.news.domain.model.NewsCategory
import io.github.alight77.news.domain.model.NewsPageResult
import io.github.alight77.news.domain.repository.NewsRepository
import io.github.alight77.news.ui.NewsApp
import io.github.alight77.news.ui.article.ArticleDetailScreen
import io.github.alight77.news.ui.home.HomeViewModel
import io.github.alight77.news.ui.theme.NewsTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LifecycleUiTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var activity: Activity

    @After
    fun resetOrientation() {
        if (::activity.isInitialized && !activity.isDestroyed) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    @Test
    fun homeAndDetailRetainTheirContextAcrossRotationAndSourceRefresh() {
        val requests = mutableListOf<NewsCategory>()
        val technologyRequests = AtomicInteger()
        val repository = object : NewsRepository {
            override suspend fun getHeadlines(category: NewsCategory, page: Int): NewsPageResult {
                assertEquals(1, page)
                requests += category
                val article = when (category) {
                    NewsCategory.GENERAL -> Article("general", "综合新闻", null, null, null, null, null, null)
                    NewsCategory.TECHNOLOGY -> {
                        val title = if (technologyRequests.incrementAndGet() == 1) {
                            "科技旧文章"
                        } else {
                            "科技更新文章"
                        }
                        Article("technology", title, null, null, null, null, null, null)
                    }
                    else -> error("Unexpected category: $category")
                }
                return NewsPageResult.Success(ArticlePage(listOf(article), 1))
            }
        }
        setNewsContent(repository)

        composeRule.onNodeWithText("科技").performClick()
        composeRule.onNodeWithText("科技旧文章").assertIsDisplayed()
        rotateAndReattach(repository, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        composeRule.onNodeWithText("科技旧文章").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("新闻详情").assertIsDisplayed()

        rotateAndReattach(repository, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        composeRule.onNodeWithText("新闻详情").assertIsDisplayed()
        composeRule.onNodeWithText("科技旧文章").assertIsDisplayed()

        composeRule.runOnIdle {
            ViewModelProvider(activity as ComponentActivity)[HomeViewModel::class.java].refresh()
        }
        composeRule.waitUntil(5_000) { technologyRequests.get() == 2 }
        composeRule.onNodeWithText("科技旧文章").assertIsDisplayed()
        composeRule.onNodeWithText("科技更新文章").assertDoesNotExist()
        composeRule.onNodeWithText("返回").performClick()
        composeRule.onNodeWithText("科技更新文章").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(
                listOf(NewsCategory.GENERAL, NewsCategory.TECHNOLOGY, NewsCategory.TECHNOLOGY),
                requests,
            )
        }
    }

    @Test
    fun backgroundRoundTripRetainsSearchAndHomeWithoutNewRequests() {
        val homeRequests = AtomicInteger()
        val searchRequests = AtomicInteger()
        val repository = object : NewsRepository {
            override suspend fun getHeadlines(category: NewsCategory, page: Int): NewsPageResult {
                homeRequests.incrementAndGet()
                return NewsPageResult.Success(
                    ArticlePage(listOf(Article("home", "首页新闻", null, null, null, null, null, null)), 1),
                )
            }

            override suspend fun search(query: String): NewsPageResult {
                searchRequests.incrementAndGet()
                return NewsPageResult.Success(
                    ArticlePage(listOf(Article("search", "$query 搜索结果", null, null, null, null, null, null)), 1),
                )
            }
        }
        setNewsContent(repository)

        composeRule.onNodeWithText("首页新闻").assertIsDisplayed()
        composeRule.onNodeWithText("搜索").performClick()
        composeRule.onNodeWithTag("search_query").performTextInput("Android")
        composeRule.waitUntil(3_000) { searchRequests.get() == 1 }
        composeRule.onNodeWithText("Android 搜索结果").assertIsDisplayed()

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.onNodeWithText("Android 搜索结果").assertIsDisplayed()
        composeRule.onNodeWithText("首页").performClick()
        composeRule.onNodeWithText("首页新闻").assertIsDisplayed()
        assertEquals(1, homeRequests.get())
        assertEquals(1, searchRequests.get())
    }

    @Test
    fun unavailableDetailShowsReturnAction() {
        val backClicks = AtomicInteger()
        composeRule.setContent {
            NewsTheme {
                ArticleDetailScreen(
                    contentPadding = PaddingValues(),
                    article = null,
                    favoriteArticleIds = emptySet(),
                    pendingFavoriteArticleIds = emptySet(),
                    onToggleFavorite = {},
                    onNavigateUp = { backClicks.incrementAndGet() },
                )
            }
        }

        composeRule.onNodeWithText("内容暂不可用，请返回列表。").assertIsDisplayed()
        composeRule.onNodeWithText("返回").performClick()
        assertEquals(1, backClicks.get())
    }

    @Test
    fun validOriginalLinkOpensAnExternalBrowsableUrl() {
        var launchedIntent: Intent? = null
        val recordingContext = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun startActivity(intent: Intent) {
                launchedIntent = intent
            }
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalContext provides recordingContext) {
                NewsTheme { originalLinkDetail() }
            }
        }

        composeRule.onNodeWithText("阅读原文").performScrollTo().performClick()
        assertEquals(Intent.ACTION_VIEW, launchedIntent?.action)
        assertEquals("https://example.com/story", launchedIntent?.dataString)
        assertTrue(launchedIntent?.categories?.contains(Intent.CATEGORY_BROWSABLE) == true)
    }

    @Test
    fun missingBrowserShowsFriendlyOriginalLinkError() {
        val failingContext = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun startActivity(intent: Intent) {
                throw ActivityNotFoundException()
            }
        }
        composeRule.setContent {
            CompositionLocalProvider(LocalContext provides failingContext) {
                NewsTheme { originalLinkDetail() }
            }
        }

        composeRule.onNodeWithText("阅读原文").performScrollTo().performClick()
        composeRule.onNodeWithText("无法打开原文，请检查设备上的浏览器。").assertIsDisplayed()
    }

    @Composable
    private fun originalLinkDetail() {
        ArticleDetailScreen(
            contentPadding = PaddingValues(),
            article = Article(
                "original", "有原文的新闻", null, null, "https://example.com/story", null, null, null,
            ),
            favoriteArticleIds = emptySet(),
            pendingFavoriteArticleIds = emptySet(),
            onToggleFavorite = {},
            onNavigateUp = {},
        )
    }

    private fun setNewsContent(repository: NewsRepository) {
        composeRule.setContent {
            val currentActivity = LocalContext.current.findActivity()
            SideEffect { activity = currentActivity }
            NewsTheme { NewsApp(repository) }
        }
    }

    private fun rotateAndReattach(repository: NewsRepository, orientation: Int) {
        val previousActivity = activity
        composeRule.runOnIdle { previousActivity.requestedOrientation = orientation }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        setNewsContent(repository)
        composeRule.waitUntil(5_000) { activity !== previousActivity }
        assertNotSame(previousActivity, activity)
    }
}

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("Lifecycle test requires an Activity context.")
}
