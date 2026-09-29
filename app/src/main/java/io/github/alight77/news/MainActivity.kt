package io.github.alight77.news

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.alight77.news.data.remote.GNewsNetworkClient
import io.github.alight77.news.data.remote.GNewsRepository
import io.github.alight77.news.data.remote.GNewsService
import io.github.alight77.news.domain.model.NewsCategory
import io.github.alight77.news.domain.model.NewsError
import io.github.alight77.news.domain.model.NewsPageResult
import io.github.alight77.news.domain.repository.NewsRepository
import io.github.alight77.news.ui.NewsApp
import io.github.alight77.news.ui.theme.NewsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository: NewsRepository = if (BuildConfig.GNEWS_API_KEY.isBlank()) {
            object : NewsRepository {
                override suspend fun getHeadlines(category: NewsCategory, page: Int): NewsPageResult =
                    NewsPageResult.Failure(NewsError.AUTHENTICATION)

                override suspend fun search(query: String, page: Int): NewsPageResult =
                    NewsPageResult.Failure(NewsError.AUTHENTICATION)
            }
        } else {
            GNewsRepository(GNewsNetworkClient.create().create(GNewsService::class.java))
        }
        val application = application as NewsApplication
        setContent {
            NewsTheme {
                NewsApp(repository, application.favoriteRepository, application.homeFirstPageCache)
            }
        }
    }
}
