package io.github.alight77.news.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoriteArticleDaoTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val databaseName = "favorite-article-test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun favoriteSnapshotCanBeReadAfterDatabaseIsReopened() = runBlocking {
        val expected = FavoriteArticleEntity(
            articleId = "article-id",
            title = "收藏新闻",
            description = "文章摘要",
            contentPreview = "文章内容预览",
            originalUrl = "https://example.com/article",
            imageUrl = "https://example.com/image.jpg",
            publishedAtEpochMillis = Instant.parse("2026-09-24T00:00:00Z").toEpochMilli(),
            sourceName = "示例来源",
            savedAtEpochMillis = 1_727_136_000_000,
        )

        withDatabase { database ->
            database.favoriteArticleDao().upsert(expected)
        }

        withDatabase { database ->
            assertEquals(expected, database.favoriteArticleDao().getById(expected.articleId))
        }
    }

    @Test
    fun removingFavoriteDeletesOnlyItsSnapshot() = runBlocking {
        val first = favorite(articleId = "first", title = "保留的收藏")
        val second = favorite(articleId = "second", title = "移除的收藏")

        withDatabase { database ->
            database.favoriteArticleDao().upsert(first)
            database.favoriteArticleDao().upsert(second)
            database.favoriteArticleDao().deleteById(second.articleId)
        }

        withDatabase { database ->
            assertEquals(first, database.favoriteArticleDao().getById(first.articleId))
            assertNull(database.favoriteArticleDao().getById(second.articleId))
        }
    }

    private fun openDatabase(): NewsDatabase = Room.databaseBuilder(
        context,
        NewsDatabase::class.java,
        databaseName,
    ).build()

    private suspend fun <T> withDatabase(block: suspend (NewsDatabase) -> T): T {
        val database = openDatabase()
        return try {
            block(database)
        } finally {
            database.close()
        }
    }

    private fun favorite(articleId: String, title: String) = FavoriteArticleEntity(
        articleId = articleId,
        title = title,
        description = null,
        contentPreview = null,
        originalUrl = null,
        imageUrl = null,
        publishedAtEpochMillis = null,
        sourceName = null,
        savedAtEpochMillis = 1_727_136_000_000,
    )
}
