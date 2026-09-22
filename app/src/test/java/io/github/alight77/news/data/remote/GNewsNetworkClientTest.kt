package io.github.alight77.news.data.remote

import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.GET

class GNewsNetworkClientTest {
    private val server = MockWebServer()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `adds the API key as an X Api Key header`() {
        server.enqueue(MockResponse().setBody("{}"))

        val result = invokeCreate(
            apiKey = "test-api-key",
            baseUrl = server.url("/").toString(),
        )

        assertTrue("The network client should be creatable with a nonblank API key", result.isSuccess)
        val response = result.getOrThrow()
            .create(TestHeadlinesService::class.java)
            .getTopHeadlines()
            .execute()
        response.body()?.close()

        val request = server.takeRequest()
        assertEquals("test-api-key", request.getHeader("X-Api-Key"))
        assertFalse(request.requestUrl!!.queryParameterNames.contains("apikey"))
    }

    @Test
    fun `rejects a blank API key before creating a client`() {
        val result = invokeCreate(
            apiKey = "   ",
            baseUrl = server.url("/").toString(),
        )

        val error = result.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `creates the default client from local build configuration`() {
        val result = runCatching { GNewsNetworkClient.create() }

        assertTrue("The default client should use the locally injected API key", result.isSuccess)
    }

    private fun invokeCreate(
        apiKey: String,
        baseUrl: String,
    ): Result<Retrofit> = runCatching { GNewsNetworkClient.create(apiKey, baseUrl) }

    private interface TestHeadlinesService {
        @GET("top-headlines")
        fun getTopHeadlines(): Call<ResponseBody>
    }
}
