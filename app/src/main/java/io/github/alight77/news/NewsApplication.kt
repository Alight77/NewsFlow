package io.github.alight77.news

import android.app.Application
import androidx.room.Room
import io.github.alight77.news.data.local.NewsDatabase
import io.github.alight77.news.data.local.RoomFavoriteRepository
import io.github.alight77.news.domain.repository.FavoriteRepository

class NewsApplication : Application() {
    val favoriteRepository: FavoriteRepository by lazy {
        val database = Room.databaseBuilder(
            this,
            NewsDatabase::class.java,
            "news-flow.db",
        ).build()
        RoomFavoriteRepository(database.favoriteArticleDao())
    }
}
