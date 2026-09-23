package io.github.alight77.news.ui.article

import io.github.alight77.news.domain.model.Article
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArticleSessionViewModelTest {
    @Test
    fun `detail resolves the opened article by key and retains its snapshot`() {
        val first = Article("first", "First", null, null, null, null, null, null)
        val second = Article("second", "Second", null, null, null, null, null, null)
        val session = ArticleSessionViewModel()

        session.open(first)
        assertEquals(first, session.articleForDetail("first"))
        assertNull(session.articleForDetail("missing"))

        session.open(second)
        assertEquals(first, session.articleForDetail("first"))
        assertEquals(second, session.articleForDetail("second"))
    }
}
