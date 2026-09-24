package io.github.alight77.news

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import io.github.alight77.news.domain.model.Article
import io.github.alight77.news.ui.components.ArticleMeta
import io.github.alight77.news.ui.theme.NewsTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ArticleMetaUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun longSourceKeepsTheDateReadableInANarrowCard() {
        val publishedAt = Instant.parse("2026-09-23T12:00:00Z")
        val source = "这是一家名称很长的示例新闻来源（用于检查窄屏元数据换行）"
        val article = Article("meta", "标题", null, null, null, null, publishedAt, source)
        val date = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withZone(ZoneId.systemDefault())
            .format(publishedAt)

        composeRule.setContent {
            NewsTheme {
                Box(Modifier.width(280.dp)) { ArticleMeta(article) }
            }
        }

        composeRule.onNodeWithText(source).assertIsDisplayed()
        val dateWidth = composeRule.onNodeWithText(date).fetchSemanticsNode().boundsInRoot.width
        assertTrue("Date was squeezed into a vertical character column: $dateWidth px", dateWidth > 200f)
    }
}
