package io.github.alight77.news.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FavoriteArticleEntity::class,
        HomeFirstPageArticleEntity::class,
        HomeFirstPageCacheMetadataEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class NewsDatabase : RoomDatabase() {
    abstract fun favoriteArticleDao(): FavoriteArticleDao

    abstract fun homeFirstPageCacheDao(): HomeFirstPageCacheDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS home_first_page_articles (
                        categoryApiValue TEXT NOT NULL,
                        position INTEGER NOT NULL,
                        articleId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT,
                        contentPreview TEXT,
                        originalUrl TEXT,
                        imageUrl TEXT,
                        publishedAtEpochMillis INTEGER,
                        sourceName TEXT,
                        PRIMARY KEY(categoryApiValue, position)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS home_first_page_cache_metadata (
                        categoryApiValue TEXT NOT NULL,
                        fetchedAtEpochMillis INTEGER NOT NULL,
                        PRIMARY KEY(categoryApiValue)
                    )
                    """.trimIndent(),
                )
            }
        }
    }
}
