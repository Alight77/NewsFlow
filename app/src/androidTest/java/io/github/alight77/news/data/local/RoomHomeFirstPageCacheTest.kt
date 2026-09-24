package io.github.alight77.news.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.alight77.news.domain.model.Article
import io.github.alight77.news.domain.model.NewsCategory
import io.github.alight77.news.domain.repository.CachedHomeFirstPage
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomHomeFirstPageCacheTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val databaseName = "room-home-first-page-cache-test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun cachePreservesArticleOrderFieldsAndSuccessfulEmptyResults() = runBlocking {
        val first = article("first", "第一篇")
        val second = article("second", "第二篇")
        withCache { cache ->
            cache.replace(
                category = NewsCategory.GENERAL,
                articles = listOf(first, second),
                fetchedAtEpochMillis = 1_727_136_000_000,
            )

            assertEquals(
                CachedHomeFirstPage(
                    articles = listOf(first, second),
                    fetchedAtEpochMillis = 1_727_136_000_000,
                ),
                cache.read(NewsCategory.GENERAL),
            )

            cache.replace(
                category = NewsCategory.GENERAL,
                articles = emptyList(),
                fetchedAtEpochMillis = 1_727_136_100_000,
            )
            assertEquals(
                CachedHomeFirstPage(
                    articles = emptyList(),
                    fetchedAtEpochMillis = 1_727_136_100_000,
                ),
                cache.read(NewsCategory.GENERAL),
            )
            assertNull(cache.read(NewsCategory.TECHNOLOGY))
        }
    }

    private fun article(id: String, title: String) = Article(
        id = id,
        title = title,
        description = "摘要 $id",
        contentPreview = "预览 $id",
        originalUrl = "https://example.com/$id",
        imageUrl = "https://example.com/$id.jpg",
        publishedAt = Instant.parse("2026-09-24T00:00:00Z"),
        sourceName = "来源 $id",
    )

    private suspend fun withCache(block: suspend (RoomHomeFirstPageCache) -> Unit) {
        val database = Room.databaseBuilder(context, NewsDatabase::class.java, databaseName).build()
        try {
            block(RoomHomeFirstPageCache(database.homeFirstPageCacheDao()))
        } finally {
            database.close()
        }
    }
}
