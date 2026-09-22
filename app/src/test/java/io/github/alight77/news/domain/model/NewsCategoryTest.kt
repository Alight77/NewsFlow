package io.github.alight77.news.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NewsCategoryTest {
    @Test
    fun `only the supported home categories can be sent to GNews`() {
        assertEquals(
            listOf("general", "technology", "business", "science", "health"),
            NewsCategory.entries.map { it.apiValue },
        )
    }
}
