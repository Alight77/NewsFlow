package io.github.alight77.news.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.github.alight77.news.domain.model.InvalidNewsDataException
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GNewsArticleMappingTest {
    private val adapter = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
        .adapter(GNewsResponseDto::class.java)

    @Test
    fun `parses a GNews response into an article page`() {
        val response = adapter.fromJson(
            """
            {
              "totalArticles": 42,
              "articles": [{
                "id": "news-1", "title": "  Headline  ",
                "description": "Summary", "content": "Preview only",
                "url": "https://example.com/story", "image": "https://example.com/image.jpg",
                "publishedAt": "2026-01-02T03:04:05Z", "lang": "zh",
                "source": {"name": "Publisher", "url": "https://example.com"},
                "unrecognizedField": true
              }]
            }
            """.trimIndent(),
        )!!

        val page = response.toArticlePage()
        val article = page.articles.single()

        assertEquals(42, response.totalArticles)
        assertEquals(1, page.rawArticleCount)
        assertEquals("news-1", article.id)
        assertEquals("Headline", article.title)
        assertEquals("Summary", article.description)
        assertEquals("Preview only", article.contentPreview)
        assertEquals("https://example.com/story", article.originalUrl)
        assertEquals("https://example.com/image.jpg", article.imageUrl)
        assertEquals(Instant.parse("2026-01-02T03:04:05Z"), article.publishedAt)
        assertEquals("Publisher", article.sourceName)
    }

    @Test
    fun `missing optional fields and invalid links do not discard a valid article`() {
        val response = adapter.fromJson(
            """
            {"articles": [{
              "id": "news-2", "title": "Headline",
              "url": "javascript:alert(1)", "image": "invalid image",
              "publishedAt": "not a timestamp",
              "source": {"url": "https://publisher.example"}
            }]}
            """.trimIndent(),
        )!!

        val article = response.toArticlePage().articles.single()

        assertNull(article.description)
        assertNull(article.contentPreview)
        assertNull(article.originalUrl)
        assertNull(article.imageUrl)
        assertNull(article.publishedAt)
        assertNull(article.sourceName)
    }

    @Test
    fun `filters invalid identifiers and titles while retaining raw page size and order`() {
        val response = GNewsResponseDto(
            articles = listOf(
                GNewsArticleDto(id = " ", title = "No ID"),
                GNewsArticleDto(id = "first", title = "First"),
                GNewsArticleDto(id = "missing-title"),
                GNewsArticleDto(id = "second", title = "Second"),
            ),
        )

        val page = response.toArticlePage()

        assertEquals(4, page.rawArticleCount)
        assertEquals(listOf("first", "second"), page.articles.map { it.id })
    }

    @Test
    fun `an empty raw list maps to an empty page`() {
        val page = GNewsResponseDto(articles = emptyList()).toArticlePage()

        assertEquals(0, page.rawArticleCount)
        assertTrue(page.articles.isEmpty())
    }

    @Test
    fun `duplicate article keys keep the first article in server order`() {
        val page = GNewsResponseDto(
            articles = listOf(
                GNewsArticleDto(id = "first", title = "Original"),
                GNewsArticleDto(id = "second", title = "Second"),
                GNewsArticleDto(id = "first", title = "Updated duplicate"),
            ),
        ).toArticlePage()

        assertEquals(3, page.rawArticleCount)
        assertEquals(listOf("Original", "Second"), page.articles.map { it.title })
    }

    @Test
    fun `a nonempty page containing only invalid articles is invalid data`() {
        val response = GNewsResponseDto(
            articles = listOf(GNewsArticleDto(id = "news-3", title = "  ")),
        )

        assertThrows(InvalidNewsDataException::class.java) { response.toArticlePage() }
    }

    @Test
    fun `a response without an articles list is invalid data`() {
        val response = adapter.fromJson("""{"totalArticles": 0}""")!!

        assertThrows(InvalidNewsDataException::class.java) { response.toArticlePage() }
    }
}
