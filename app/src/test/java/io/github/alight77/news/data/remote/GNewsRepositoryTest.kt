package io.github.alight77.news.data.remote

import io.github.alight77.news.domain.model.NewsCategory
import io.github.alight77.news.domain.model.NewsError
import io.github.alight77.news.domain.model.NewsPageResult
import io.github.alight77.news.domain.model.ArticlePage
import io.github.alight77.news.domain.repository.NewsRepository
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GNewsRepositoryTest {
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
    fun `requests the selected category and page with fixed GNews parameters`() = runBlocking {
        server.enqueue(MockResponse().setBody(validResponse))
        val repository: NewsRepository = networkRepository()

        val result = repository.getHeadlines(NewsCategory.TECHNOLOGY, page = 2)

        assertTrue(result is NewsPageResult.Success)
        val page = (result as NewsPageResult.Success).page
        assertEquals(1, page.rawArticleCount)
        assertEquals("news-1", page.articles.single().id)

        val request = server.takeRequest()
        assertEquals("/api/v4/top-headlines", request.requestUrl!!.encodedPath)
        assertEquals("technology", request.requestUrl!!.queryParameter("category"))
        assertEquals("zh", request.requestUrl!!.queryParameter("lang"))
        assertEquals("cn", request.requestUrl!!.queryParameter("country"))
        assertEquals("10", request.requestUrl!!.queryParameter("max"))
        assertEquals("2", request.requestUrl!!.queryParameter("page"))
        assertEquals("test-api-key", request.getHeader("X-Api-Key"))
    }

    @Test
    fun `searches the first page with fixed GNews parameters and a trimmed query`() = runBlocking {
        server.enqueue(MockResponse().setBody(validResponse))

        val result = networkRepository().search(" Android ")

        assertTrue(result is NewsPageResult.Success)
        val request = server.takeRequest()
        assertEquals("/api/v4/search", request.requestUrl!!.encodedPath)
        assertEquals("Android", request.requestUrl!!.queryParameter("q"))
        assertEquals("zh", request.requestUrl!!.queryParameter("lang"))
        assertEquals("cn", request.requestUrl!!.queryParameter("country"))
        assertEquals("10", request.requestUrl!!.queryParameter("max"))
        assertEquals("1", request.requestUrl!!.queryParameter("page"))
        assertEquals("test-api-key", request.getHeader("X-Api-Key"))
    }

    @Test
    fun `an empty remote page remains a successful empty page`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"totalArticles":0,"articles":[]}"""))

        val result = networkRepository().getHeadlines(NewsCategory.GENERAL, page = 1)

        assertEquals(NewsPageResult.Success(ArticlePage(emptyList(), 0)), result)
    }

    @Test
    fun `an all invalid remote page returns invalid data`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"articles":[{"id":"news-2","title":" "}]}"""))

        val result = networkRepository().getHeadlines(NewsCategory.GENERAL, page = 1)

        assertEquals(NewsPageResult.Failure(NewsError.INVALID_DATA), result)
    }

    @Test
    fun `maps known HTTP failures without exposing the server response`() = runBlocking {
        val cases = listOf(
            400 to NewsError.INVALID_REQUEST,
            401 to NewsError.AUTHENTICATION,
            403 to NewsError.QUOTA_EXCEEDED,
            429 to NewsError.RATE_LIMITED,
            503 to NewsError.UNKNOWN,
        )
        val repository = networkRepository()

        for ((status, error) in cases) {
            server.enqueue(MockResponse().setResponseCode(status).setBody("private server detail"))

            assertEquals(
                NewsPageResult.Failure(error),
                repository.getHeadlines(NewsCategory.GENERAL, page = 1),
            )
        }
    }

    @Test
    fun `maps malformed JSON to invalid data`() = runBlocking {
        server.enqueue(MockResponse().setBody("{"))

        val result = networkRepository().getHeadlines(NewsCategory.GENERAL, page = 1)

        assertEquals(NewsPageResult.Failure(NewsError.INVALID_DATA), result)
    }

    @Test
    fun `maps a connection failure without leaking its exception text`() = runBlocking {
        val repository = GNewsRepository(serviceThatThrows(IOException("private connection detail")))

        assertEquals(
            NewsPageResult.Failure(NewsError.CONNECTION),
            repository.getHeadlines(NewsCategory.GENERAL, page = 1),
        )
    }

    @Test
    fun `maps a request timeout separately from other connection failures`() = runBlocking {
        val repository = GNewsRepository(serviceThatThrows(SocketTimeoutException("private timeout detail")))

        assertEquals(
            NewsPageResult.Failure(NewsError.TIMEOUT),
            repository.getHeadlines(NewsCategory.GENERAL, page = 1),
        )
    }

    @Test
    fun `propagates cancellation instead of treating it as a news error`() {
        val repository = GNewsRepository(serviceThatThrows(CancellationException("stale request")))

        assertThrows(CancellationException::class.java) {
            runBlocking { repository.getHeadlines(NewsCategory.GENERAL, page = 1) }
        }
    }

    @Test
    fun `rejects an invalid page before making a request`() {
        val repository = networkRepository()

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.getHeadlines(NewsCategory.GENERAL, page = 0) }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `rejects a blank search query before making a request`() {
        val repository = networkRepository()

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.search("  ") }
        }
        assertEquals(0, server.requestCount)
    }

    private fun networkRepository(): GNewsRepository = GNewsRepository(
        GNewsNetworkClient.create(
            apiKey = "test-api-key",
            baseUrl = server.url("/api/v4/").toString(),
        ).create(GNewsService::class.java),
    )

    private fun serviceThatThrows(error: Exception): GNewsService = object : GNewsService {
        override suspend fun getTopHeadlines(
            category: String,
            lang: String,
            country: String,
            max: Int,
            page: Int,
        ): GNewsResponseDto = throw error

        override suspend fun search(
            query: String,
            lang: String,
            country: String,
            max: Int,
            page: Int,
        ): GNewsResponseDto = throw error
    }

    private val validResponse = """{"totalArticles":1,"articles":[{"id":"news-1","title":"Headline"}]}"""
}
