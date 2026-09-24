package io.github.alight77.news.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.alight77.news.domain.model.NewsCategory
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NewsDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "news-database-migration-test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migrationFromV1PreservesFavoritesAndEmptyCacheRemainsKnown() = runBlocking {
        val favorite = FavoriteArticleEntity(
            articleId = "favorite-id",
            title = "已有收藏",
            description = "收藏摘要",
            contentPreview = "收藏预览",
            originalUrl = "https://example.com/favorite",
            imageUrl = "https://example.com/favorite.jpg",
            publishedAtEpochMillis = Instant.parse("2026-09-24T00:00:00Z").toEpochMilli(),
            sourceName = "收藏来源",
            savedAtEpochMillis = 1_727_136_000_000,
        )
        createVersionOneDatabase(favorite)

        val database = Room.databaseBuilder(context, NewsDatabase::class.java, databaseName)
            .addMigrations(NewsDatabase.MIGRATION_1_2)
            .build()
        try {
            assertEquals(favorite, database.favoriteArticleDao().getById(favorite.articleId))

            val cacheDao = database.homeFirstPageCacheDao()
            cacheDao.replace(
                categoryApiValue = NewsCategory.GENERAL.apiValue,
                fetchedAtEpochMillis = 1_727_136_100_000,
                articles = listOf(
                    HomeFirstPageArticleEntity(
                        categoryApiValue = NewsCategory.GENERAL.apiValue,
                        position = 0,
                        articleId = "cached-id",
                        title = "缓存新闻",
                        description = null,
                        contentPreview = null,
                        originalUrl = null,
                        imageUrl = null,
                        publishedAtEpochMillis = null,
                        sourceName = "缓存来源",
                    ),
                ),
            )
            cacheDao.replace(
                categoryApiValue = NewsCategory.GENERAL.apiValue,
                fetchedAtEpochMillis = 1_727_136_200_000,
                articles = emptyList(),
            )

            assertEquals(
                HomeFirstPageCacheMetadataEntity(
                    categoryApiValue = NewsCategory.GENERAL.apiValue,
                    fetchedAtEpochMillis = 1_727_136_200_000,
                ),
                cacheDao.getMetadata(NewsCategory.GENERAL.apiValue),
            )
            assertEquals(emptyList<HomeFirstPageArticleEntity>(), cacheDao.getArticles(NewsCategory.GENERAL.apiValue))
            assertNull(cacheDao.getMetadata(NewsCategory.TECHNOLOGY.apiValue))
        } finally {
            database.close()
        }
    }

    private fun createVersionOneDatabase(favorite: FavoriteArticleEntity) {
        val database = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        try {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS favorite_articles (
                    articleId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    description TEXT,
                    contentPreview TEXT,
                    originalUrl TEXT,
                    imageUrl TEXT,
                    publishedAtEpochMillis INTEGER,
                    sourceName TEXT,
                    savedAtEpochMillis INTEGER NOT NULL,
                    PRIMARY KEY(articleId)
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO favorite_articles (
                    articleId, title, description, contentPreview, originalUrl, imageUrl,
                    publishedAtEpochMillis, sourceName, savedAtEpochMillis
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    favorite.articleId,
                    favorite.title,
                    favorite.description,
                    favorite.contentPreview,
                    favorite.originalUrl,
                    favorite.imageUrl,
                    favorite.publishedAtEpochMillis,
                    favorite.sourceName,
                    favorite.savedAtEpochMillis,
                ),
            )
            database.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            database.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES(42, ?)",
                arrayOf("500be0a785bd8924bfb83897de51110f"),
            )
            database.version = 1
        } finally {
            database.close()
        }
    }
}
