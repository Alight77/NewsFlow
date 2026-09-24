package io.github.alight77.news.ui.home

import android.content.res.Configuration
import androidx.annotation.StringRes
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.alight77.news.R
import io.github.alight77.news.domain.model.Article
import io.github.alight77.news.domain.model.NewsCategory
import io.github.alight77.news.domain.model.NewsError
import io.github.alight77.news.ui.components.ArticleImage
import io.github.alight77.news.ui.components.ArticleMeta
import io.github.alight77.news.ui.components.FavoriteToggleButton
import io.github.alight77.news.ui.components.messageRes
import io.github.alight77.news.ui.theme.NewsTheme

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    viewModel: HomeViewModel,
    favoriteArticleIds: Set<String>,
    pendingFavoriteArticleIds: Set<String>,
    onToggleFavorite: (Article) -> Unit,
    onOpenDetail: (Article) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val refreshSuccessMessage = stringResource(R.string.home_refresh_succeeded)
    LaunchedEffect(viewModel, refreshSuccessMessage) {
        viewModel.refreshSucceeded.collect {
            snackbarHostState.showSnackbar(refreshSuccessMessage, duration = SnackbarDuration.Short)
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        HomeScreenContent(
            contentPadding = contentPadding,
            uiState = uiState,
            onCategorySelected = viewModel::selectCategory,
            onRetry = viewModel::retry,
            onRefresh = viewModel::refresh,
            onLoadNextPage = viewModel::loadNextPage,
            onRetryNextPage = viewModel::retryNextPage,
            favoriteArticleIds = favoriteArticleIds,
            pendingFavoriteArticleIds = pendingFavoriteArticleIds,
            onToggleFavorite = onToggleFavorite,
            onOpenDetail = onOpenDetail,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(contentPadding),
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HomeScreenContent(
    contentPadding: PaddingValues,
    uiState: HomeScreenState,
    onCategorySelected: (NewsCategory) -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
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
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 12.dp),
        )
        ScrollableTabRow(
            selectedTabIndex = NewsCategory.entries.indexOf(uiState.selectedCategory),
            edgePadding = 12.dp,
        ) {
            NewsCategory.entries.forEach { category ->
                Tab(
                    selected = uiState.selectedCategory == category,
                    onClick = { onCategorySelected(category) },
                    text = { Text(stringResource(category.labelRes())) },
                )
            }
        }
        when (val state = uiState.pageState) {
            HomeUiState.Loading -> HomeMessage {
                CircularProgressIndicator()
                Text(stringResource(R.string.home_loading))
            }
            HomeUiState.Empty -> HomeMessage {
                Text(stringResource(R.string.home_empty))
                Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
            is HomeUiState.Error -> HomeMessage {
                Text(stringResource(state.error.messageRes()))
                Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
            is HomeUiState.Content -> {
                val listState = rememberLazyListState()
                val articleCount by rememberUpdatedState(state.articles.size)
                val loadNextPage by rememberUpdatedState(onLoadNextPage)
                LaunchedEffect(listState, uiState.selectedCategory) {
                    var requestedDuringScroll = false
                    snapshotFlow {
                        Triple(
                            listState.isScrollInProgress,
                            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1,
                            listState.layoutInfo.totalItemsCount,
                        )
                    }.collect { (scrolling, lastVisibleIndex, _) ->
                        if (!scrolling) requestedDuringScroll = false
                        else if (!requestedDuringScroll && lastVisibleIndex >= articleCount - 3) {
                            requestedDuringScroll = true
                            loadNextPage()
                        }
                    }
                }
                RefreshErrorBanner(uiState.refreshError, onRefresh)
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().testTag("home_articles"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.articles, key = Article::id) { article ->
                            ArticleCard(
                                article = article,
                                isFavorite = article.id in favoriteArticleIds,
                                isPendingFavorite = article.id in pendingFavoriteArticleIds,
                                onToggleFavorite = { onToggleFavorite(article) },
                                onClick = { onOpenDetail(article) },
                            )
                        }
                        if (uiState.appendState != HomeAppendState.Idle) {
                            item { AppendFooter(uiState.appendState, onRetryNextPage) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppendFooter(state: HomeAppendState, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (state) {
            HomeAppendState.Idle -> Unit
            HomeAppendState.Loading -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.home_loading_more))
            }
            is HomeAppendState.Error -> {
                Text(stringResource(R.string.home_append_failed, stringResource(state.error.messageRes())))
                Button(onClick = onRetry) { Text(stringResource(R.string.home_retry_append)) }
            }
            HomeAppendState.ManualContinue -> {
                Text(stringResource(R.string.home_no_new_articles))
                Button(onClick = onRetry) { Text(stringResource(R.string.home_continue_append)) }
            }
            HomeAppendState.EndReached -> Text(stringResource(R.string.home_end_reached))
        }
    }
}

@StringRes
private fun NewsCategory.labelRes(): Int = when (this) {
    NewsCategory.GENERAL -> R.string.category_general
    NewsCategory.TECHNOLOGY -> R.string.category_technology
    NewsCategory.BUSINESS -> R.string.category_business
    NewsCategory.SCIENCE -> R.string.category_science
    NewsCategory.HEALTH -> R.string.category_health
}

@Composable
private fun HomeMessage(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        content()
    }
}

@Composable
private fun RefreshErrorBanner(error: NewsError?, onRetry: () -> Unit) {
    if (error == null) return

    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_refresh_failed, stringResource(error.messageRes())),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.refresh_retry)) }
        }
    }
}

@Composable
private fun ArticleCard(
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

@Composable
private fun PreviewHome(
    pageState: HomeUiState,
    isRefreshing: Boolean = false,
    refreshError: NewsError? = null,
    appendState: HomeAppendState = HomeAppendState.Idle,
    favoriteArticleIds: Set<String> = emptySet(),
) {
    NewsTheme(dynamicColor = false) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            HomeScreenContent(
                contentPadding = PaddingValues(),
                uiState = HomeScreenState(NewsCategory.GENERAL, pageState, isRefreshing, refreshError, appendState),
                onCategorySelected = {},
                onRetry = {},
                onRefresh = {},
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

@Preview(name = "Home - content", showBackground = true)
@Preview(name = "Home - content dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeContentPreview() {
    PreviewHome(previewContent(), favoriteArticleIds = setOf("preview-1"))
}

@Preview(name = "Home - refreshing", showBackground = true)
@Composable
private fun HomeRefreshingPreview() {
    PreviewHome(previewContent(), isRefreshing = true)
}

@Preview(name = "Home - refresh failed", showBackground = true)
@Preview(name = "Home - refresh failed dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeRefreshFailedPreview() {
    PreviewHome(previewContent(), refreshError = NewsError.CONNECTION)
}

@Preview(name = "Home - loading more", showBackground = true)
@Composable
private fun HomeAppendLoadingPreview() {
    PreviewHome(previewContent(), appendState = HomeAppendState.Loading)
}

@Preview(name = "Home - load more failed", showBackground = true)
@Preview(name = "Home - load more failed dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeAppendErrorPreview() {
    PreviewHome(previewContent(), appendState = HomeAppendState.Error(NewsError.CONNECTION))
}

@Preview(name = "Home - continue after duplicates", showBackground = true)
@Composable
private fun HomeAppendManualPreview() {
    PreviewHome(previewContent(), appendState = HomeAppendState.ManualContinue)
}

@Preview(name = "Home - end reached", showBackground = true)
@Composable
private fun HomeAppendEndPreview() {
    PreviewHome(previewContent(), appendState = HomeAppendState.EndReached)
}

@Preview(name = "Home - loading", showBackground = true)
@Composable
private fun HomeLoadingPreview() {
    PreviewHome(HomeUiState.Loading)
}

@Preview(name = "Home - empty", showBackground = true)
@Composable
private fun HomeEmptyPreview() {
    PreviewHome(HomeUiState.Empty)
}

@Preview(name = "Home - error", showBackground = true)
@Composable
private fun HomeErrorPreview() {
    PreviewHome(HomeUiState.Error(NewsError.CONNECTION))
}

private fun previewContent() = HomeUiState.Content(
    listOf(
        Article(
            id = "preview-1",
            title = "示例新闻标题：用于检查较长标题的换行效果",
            description = "这是一段用于预览首页文章卡片布局的摘要。",
            contentPreview = null,
            originalUrl = null,
            imageUrl = null,
            publishedAt = null,
            sourceName = "示例来源",
        ),
    ),
)
