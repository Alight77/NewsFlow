package io.github.alight77.news.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.github.alight77.news.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object GNewsNetworkClient {
    fun create(): Retrofit = create(
        apiKey = BuildConfig.GNEWS_API_KEY,
        baseUrl = GNEWS_BASE_URL,
    )

    fun create(
        apiKey: String,
        baseUrl: String,
    ): Retrofit {
        require(apiKey.isNotBlank()) { "GNews API key must not be blank." }

        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                    .newBuilder()
                    .header(API_KEY_HEADER, apiKey)
                    .build()
                chain.proceed(request)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    private const val GNEWS_BASE_URL = "https://gnews.io/api/v4/"
    private const val API_KEY_HEADER = "X-Api-Key"
}
