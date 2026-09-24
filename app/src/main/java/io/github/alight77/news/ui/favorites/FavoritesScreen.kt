package io.github.alight77.news.ui.favorites

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.alight77.news.R
import io.github.alight77.news.domain.model.Article
import io.github.alight77.news.ui.components.ArticleImage
import io.github.alight77.news.ui.components.ArticleMeta
import io.github.alight77.news.ui.components.FavoriteToggleButton
import io.github.alight77.news.ui.theme.NewsTheme

@Composable
fun FavoritesScreen(
    contentPadding: PaddingValues,
    uiState: FavoritesUiState,
    onToggleFavorite: (Article) -> Unit,
    onOpenDetail: (Article) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        Text(
            text = stringResource(R.string.favorites_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 12.dp),
        )
        if (uiState.favorites.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.favorites_empty))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("favorite_articles"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.favorites, key = Article::id) { article ->
                    FavoriteArticleCard(
                        article = article,
                        isPending = article.id in uiState.pendingArticleIds,
                        onToggleFavorite = { onToggleFavorite(article) },
                        onClick = { onOpenDetail(article) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteArticleCard(
    article: Article,
    isPending: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column {
            ArticleImage(article.imageUrl, modifier = Modifier.fillMaxWidth())
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        article.title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    FavoriteToggleButton(
                        isFavorite = true,
                        isPending = isPending,
                        onClick = onToggleFavorite,
                        modifier = Modifier.testTag("favorite_action_${article.id}"),
                    )
                }
                ArticleMeta(article)
                article.description?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                }
            }
        }
    }
}

@Preview(name = "Favorites - empty", showBackground = true)
@Preview(name = "Favorites - empty dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FavoritesEmptyPreview() {
    PreviewFavorites(FavoritesUiState())
}

@Preview(name = "Favorites - content", showBackground = true)
@Composable
private fun FavoritesContentPreview() {
    PreviewFavorites(FavoritesUiState(favorites = listOf(previewArticle())))
}

@Composable
private fun PreviewFavorites(uiState: FavoritesUiState) {
    NewsTheme(dynamicColor = false) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            FavoritesScreen(
                contentPadding = PaddingValues(),
                uiState = uiState,
                onToggleFavorite = {},
                onOpenDetail = {},
            )
        }
    }
}

private fun previewArticle() = Article(
    id = "favorite-preview",
    title = "示例收藏新闻：用于检查收藏列表的标题和操作入口",
    description = "这是保存在本地的文章展示快照。",
    contentPreview = null,
    originalUrl = null,
    imageUrl = null,
    publishedAt = null,
    sourceName = "示例来源",
)
