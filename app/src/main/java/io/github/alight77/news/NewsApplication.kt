package io.github.alight77.news

import android.app.Application
import androidx.room.Room
import io.github.alight77.news.data.local.NewsDatabase
import io.github.alight77.news.data.local.RoomFavoriteRepository
import io.github.alight77.news.data.local.RoomHomeFirstPageCache
import io.github.alight77.news.domain.repository.FavoriteRepository
import io.github.alight77.news.domain.repository.HomeFirstPageCache

class NewsApplication : Application() {
    private val database: NewsDatabase by lazy {
        Room.databaseBuilder(
            this,
            NewsDatabase::class.java,
            "news-flow.db",
        ).addMigrations(NewsDatabase.MIGRATION_1_2).build()
    }

    val favoriteRepository: FavoriteRepository by lazy {
        RoomFavoriteRepository(database.favoriteArticleDao())
    }

    val homeFirstPageCache: HomeFirstPageCache by lazy {
        RoomHomeFirstPageCache(database.homeFirstPageCacheDao())
    }
}
