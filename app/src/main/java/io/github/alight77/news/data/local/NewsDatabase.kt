package io.github.alight77.news.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [FavoriteArticleEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class NewsDatabase : RoomDatabase() {
    abstract fun favoriteArticleDao(): FavoriteArticleDao
}
