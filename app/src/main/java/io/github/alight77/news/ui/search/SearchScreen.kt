package io.github.alight77.news.ui.search

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.alight77.news.R
import io.github.alight77.news.domain.model.Article
import io.github.alight77.news.domain.model.NewsError
import io.github.alight77.news.ui.components.ArticleImage
import io.github.alight77.news.ui.components.ArticleMeta
import io.github.alight77.news.ui.components.FavoriteToggleButton
import io.github.alight77.news.ui.components.messageRes
import io.github.alight77.news.ui.theme.NewsTheme

@Composable
fun SearchScreen(
    contentPadding: PaddingValues,
    viewModel: SearchViewModel,
    favoriteArticleIds: Set<String>,
    pendingFavoriteArticleIds: Set<String>,
    onToggleFavorite: (Article) -> Unit,
    onOpenDetail: (Article) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(viewModel) { viewModel.onSearchVisible() }
    SearchScreenContent(
        contentPadding = contentPadding,
        uiState = uiState,
        onInputChanged = viewModel::updateQuery,
        onRetry = viewModel::retry,
        onLoadNextPage = viewModel::loadNextPage,
        onRetryNextPage = viewModel::retryNextPage,
        favoriteArticleIds = favoriteArticleIds,
        pendingFavoriteArticleIds = pendingFavoriteArticleIds,
        onToggleFavorite = onToggleFavorite,
        onOpenDetail = onOpenDetail,
    )
}

@Composable
private fun SearchScreenContent(
    contentPadding: PaddingValues,
    uiState: SearchScreenState,
    onInputChanged: (String) -> Unit,
    onRetry: () -> Unit,
    onLoadNextPage: () -> Unit,
    onRetryNextPage: () -> Unit,
    favoriteArticleIds: Set<String>,
    pendingFavoriteArticleIds: Set<String>,
    onToggleFavorite: (Article) -> Unit,
    onOpenDetail: (Article) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        Text(
            text = stringResource(R.string.search_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 12.dp),
        )
        OutlinedTextField(
            value = uiState.input,
            onValueChange = onInputChanged,
            label = { Text(stringResource(R.string.search_input_label)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .testTag("search_query"),
        )
        when (val state = uiState.resultState) {
            SearchUiState.Initial -> SearchMessage { Text(stringResource(R.string.search_initial)) }
            SearchUiState.Loading -> SearchMessage {
                CircularProgressIndicator()
                Text(stringResource(R.string.search_loading))
            }
            SearchUiState.Empty -> SearchMessage { Text(stringResource(R.string.search_empty)) }
            is SearchUiState.Error -> SearchMessage {
                Text(stringResource(state.error.messageRes()))
                Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
            is SearchUiState.Content -> {
                val listState = rememberLazyListState()
                val articleCount by rememberUpdatedState(state.articles.size)
                val loadNextPage by rememberUpdatedState(onLoadNextPage)
                LaunchedEffect(listState, uiState.normalizedQuery) {
                    var requestedDuringScroll = false
                    snapshotFlow {
                        Pair(
                            listState.isScrollInProgress,
                            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1,
                        )
                    }.collect { (scrolling, lastVisibleIndex) ->
                        if (!scrolling) requestedDuringScroll = false
                        else if (!requestedDuringScroll && lastVisibleIndex >= articleCount - 2) {
                            requestedDuringScroll = true
                            loadNextPage()
                        }
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("search_articles"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.articles, key = Article::id) { article ->
                        SearchArticleCard(
                            article = article,
                            isFavorite = article.id in favoriteArticleIds,
                            isPendingFavorite = article.id in pendingFavoriteArticleIds,
                            onToggleFavorite = { onToggleFavorite(article) },
                            onClick = { onOpenDetail(article) },
                        )
                    }
                    item { SearchAppendFooter(uiState.appendState, onLoadNextPage, onRetryNextPage) }
                }
            }
        }
    }
}

@Composable
private fun SearchAppendFooter(state: SearchAppendState, onLoadNextPage: () -> Unit, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).testTag("search_append"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (state) {
            SearchAppendState.Idle -> Button(onClick = onLoadNextPage) {
                Text(stringResource(R.string.search_load_more))
            }
            SearchAppendState.Loading -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.search_loading_more))
            }
            is SearchAppendState.Error -> {
                Text(stringResource(R.string.search_append_failed, stringResource(state.error.messageRes())))
                Button(onClick = onRetry) { Text(stringResource(R.string.search_retry_append)) }
            }
            SearchAppendState.ManualContinue -> {
                Text(stringResource(R.string.search_no_new_articles))
                Button(onClick = onRetry) { Text(stringResource(R.string.search_continue_append)) }
            }
            SearchAppendState.EndReached -> Text(stringResource(R.string.search_end_reached))
        }
    }
}

@Composable
private fun ColumnScope.SearchMessage(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(24.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun SearchArticleCard(
    article: Article,
    isFavorite: Boolean,
    isPendingFavorite: Boolean,
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
                        isFavorite = isFavorite,
                        isPending = isPendingFavorite,
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

@Preview(name = "Search initial", showBackground = true)
@Preview(name = "Search initial dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SearchInitialPreview() {
    PreviewSearch(SearchScreenState())
}

@Preview(name = "Search loading", showBackground = true)
@Composable
private fun SearchLoadingPreview() {
    PreviewSearch(SearchScreenState("Android", "Android", SearchUiState.Loading))
}

@Preview(name = "Search results", showBackground = true)
@Composable
private fun SearchContentPreview() {
    PreviewSearch(
        SearchScreenState(
            "Android",
            "Android",
            SearchUiState.Content(listOf(previewArticle("android"))),
            SearchAppendState.EndReached,
        ),
        favoriteArticleIds = setOf("android"),
    )
}

@Preview(name = "Search loading more", showBackground = true)
@Composable
private fun SearchAppendLoadingPreview() {
    PreviewSearch(
        SearchScreenState(
            "Android", "Android", SearchUiState.Content(listOf(previewArticle("android"))), SearchAppendState.Loading,
        ),
    )
}

@Preview(name = "Search load more failed", showBackground = true)
@Preview(name = "Search load more failed dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SearchAppendErrorPreview() {
    PreviewSearch(
        SearchScreenState(
            "Android", "Android", SearchUiState.Content(listOf(previewArticle("android"))),
            SearchAppendState.Error(NewsError.CONNECTION),
        ),
    )
}

@Preview(name = "Search load more", showBackground = true)
@Composable
private fun SearchAppendIdlePreview() {
    PreviewSearch(SearchScreenState("Android", "Android", SearchUiState.Content(listOf(previewArticle("android")))))
}

@Preview(name = "Search continue after duplicates", showBackground = true)
@Composable
private fun SearchAppendManualPreview() {
    PreviewSearch(
        SearchScreenState(
            "Android", "Android", SearchUiState.Content(listOf(previewArticle("android"))),
            SearchAppendState.ManualContinue,
        ),
    )
}

@Preview(name = "Search empty", showBackground = true)
@Composable
private fun SearchEmptyPreview() {
    PreviewSearch(SearchScreenState("Android", "Android", SearchUiState.Empty))
}

@Preview(name = "Search error", showBackground = true)
@Composable
private fun SearchErrorPreview() {
    PreviewSearch(SearchScreenState("Android", "Android", SearchUiState.Error(NewsError.CONNECTION)))
}

@Composable
private fun PreviewSearch(
    uiState: SearchScreenState,
    favoriteArticleIds: Set<String> = emptySet(),
) {
    NewsTheme(dynamicColor = false) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            SearchScreenContent(
                contentPadding = PaddingValues(),
                uiState = uiState,
                onInputChanged = {},
                onRetry = {},
                onLoadNextPage = {},
                onRetryNextPage = {},
                favoriteArticleIds = favoriteArticleIds,
                pendingFavoriteArticleIds = emptySet(),
                onToggleFavorite = {},
                onOpenDetail = {},
            )
        }
    }
}

private fun previewArticle(id: String) = Article(
    id = id,
    title = "Android 开发示例新闻",
    description = "用于预览搜索结果文章卡片的静态摘要。",
    contentPreview = null,
    originalUrl = null,
    imageUrl = null,
    publishedAt = null,
    sourceName = "NewsFlow",
)
