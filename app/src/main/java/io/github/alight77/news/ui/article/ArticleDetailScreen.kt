package io.github.alight77.news.ui.article

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.alight77.news.R
import io.github.alight77.news.domain.model.Article
import io.github.alight77.news.ui.components.ArticleImage
import io.github.alight77.news.ui.components.ArticleMeta
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ArticleDetailScreen(
    contentPadding: PaddingValues,
    article: Article?,
    onNavigateUp: () -> Unit,
) {
    val context = LocalContext.current
    var openFailed by rememberSaveable(article?.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.detail_title)) },
            navigationIcon = {
                TextButton(onClick = onNavigateUp) {
                    Text(stringResource(R.string.navigate_up))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            if (article == null) {
                Text(
                    text = stringResource(R.string.detail_unavailable),
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                ArticleImage(article.imageUrl, modifier = Modifier.fillMaxWidth())
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(article.title, style = MaterialTheme.typography.headlineSmall)
                    ArticleMeta(article)
                    article.description?.let {
                        Text(
                            it,
                            modifier = Modifier.padding(top = 16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    article.contentPreview?.takeIf { it != article.description }?.let {
                        Text(
                            it,
                            modifier = Modifier.padding(top = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    val originalUrl = article.originalUrl
                        ?.takeIf { it.toHttpUrlOrNull() != null }
                    if (originalUrl == null) {
                        Text(
                            stringResource(R.string.original_unavailable),
                            modifier = Modifier.padding(top = 20.dp),
                        )
                    } else {
                        Text(
                            originalUrl,
                            modifier = Modifier.padding(top = 20.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(originalUrl))
                                    .addCategory(Intent.CATEGORY_BROWSABLE)
                                try {
                                    context.startActivity(intent)
                                    openFailed = false
                                } catch (_: ActivityNotFoundException) {
                                    openFailed = true
                                } catch (_: SecurityException) {
                                    openFailed = true
                                }
                            },
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text(stringResource(R.string.read_original))
                        }
                        if (openFailed) {
                            Text(
                                stringResource(R.string.original_open_failed),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}
