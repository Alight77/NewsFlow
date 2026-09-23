package io.github.alight77.news.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface GNewsService {
    @GET("top-headlines")
    suspend fun getTopHeadlines(
        @Query("category") category: String,
        @Query("lang") lang: String,
        @Query("country") country: String,
        @Query("max") max: Int,
        @Query("page") page: Int,
    ): GNewsResponseDto

    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("lang") lang: String,
        @Query("country") country: String,
        @Query("max") max: Int,
        @Query("page") page: Int,
    ): GNewsResponseDto
}
