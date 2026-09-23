package io.github.alight77.news.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.alight77.news.R
import io.github.alight77.news.domain.model.Article
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ArticleMeta(article: Article) {
    val source = article.sourceName ?: stringResource(R.string.unknown_source)
    val date = article.publishedAt?.let {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withZone(ZoneId.systemDefault())
            .format(it)
    } ?: stringResource(R.string.unknown_time)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(source, style = MaterialTheme.typography.bodySmall)
        Text("·", style = MaterialTheme.typography.bodySmall)
        Text(date, style = MaterialTheme.typography.bodySmall)
    }
}
