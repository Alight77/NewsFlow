package io.github.alight77.news.data.remote

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import io.github.alight77.news.domain.model.InvalidNewsDataException
import io.github.alight77.news.domain.model.NewsCategory
import io.github.alight77.news.domain.model.NewsError
import io.github.alight77.news.domain.model.NewsPageResult
import io.github.alight77.news.domain.repository.NewsRepository
import java.io.EOFException
import java.io.IOException
import java.util.concurrent.CancellationException
import retrofit2.HttpException

class GNewsRepository(private val service: GNewsService) : NewsRepository {
    override suspend fun getHeadlines(category: NewsCategory, page: Int): NewsPageResult {
        require(page >= 1) { "Page must be positive." }

        return try {
            val response = service.getTopHeadlines(
                category = category.apiValue,
                lang = LANGUAGE,
                country = COUNTRY,
                max = PAGE_SIZE,
                page = page,
            )
            NewsPageResult.Success(response.toArticlePage())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (http: HttpException) {
            NewsPageResult.Failure(
                when (http.code()) {
                    400 -> NewsError.INVALID_REQUEST
                    401 -> NewsError.AUTHENTICATION
                    403 -> NewsError.QUOTA_EXCEEDED
                    429 -> NewsError.RATE_LIMITED
                    else -> NewsError.UNKNOWN
                },
            )
        } catch (invalidData: InvalidNewsDataException) {
            NewsPageResult.Failure(NewsError.INVALID_DATA)
        } catch (invalidJson: JsonDataException) {
            NewsPageResult.Failure(NewsError.INVALID_DATA)
        } catch (invalidJson: JsonEncodingException) {
            NewsPageResult.Failure(NewsError.INVALID_DATA)
        } catch (invalidJson: EOFException) {
            NewsPageResult.Failure(NewsError.INVALID_DATA)
        } catch (connection: IOException) {
            NewsPageResult.Failure(NewsError.CONNECTION)
        } catch (unknown: Exception) {
            NewsPageResult.Failure(NewsError.UNKNOWN)
        }
    }

    private companion object {
        const val LANGUAGE = "zh"
        const val COUNTRY = "cn"
        const val PAGE_SIZE = 10
    }
}
