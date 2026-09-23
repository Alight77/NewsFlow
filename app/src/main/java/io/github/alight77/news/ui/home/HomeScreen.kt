package io.github.alight77.news.ui.home

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.alight77.news.R
import io.github.alight77.news.domain.model.Article
import io.github.alight77.news.domain.model.NewsCategory
import io.github.alight77.news.domain.model.NewsError
import io.github.alight77.news.ui.components.ArticleImage
import io.github.alight77.news.ui.components.ArticleMeta
import io.github.alight77.news.ui.components.messageRes
import io.github.alight77.news.ui.theme.NewsTheme

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    viewModel: HomeViewModel,
    onOpenDetail: (Article) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    HomeScreenContent(
        contentPadding = contentPadding,
        uiState = uiState,
        onCategorySelected = viewModel::selectCategory,
        onRetry = viewModel::retry,
        onOpenDetail = onOpenDetail,
    )
}

@Composable
private fun HomeScreenContent(
    contentPadding: PaddingValues,
    uiState: HomeScreenState,
    onCategorySelected: (NewsCategory) -> Unit,
    onRetry: () -> Unit,
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
            is HomeUiState.Content -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.articles, key = Article::id) { article ->
                    ArticleCard(article = article, onClick = { onOpenDetail(article) })
                }
            }
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
private fun ArticleCard(article: Article, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column {
            ArticleImage(article.imageUrl, modifier = Modifier.fillMaxWidth())
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(article.title, style = MaterialTheme.typography.titleMedium)
                ArticleMeta(article)
                article.description?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                }
            }
        }
    }
}

@Composable
private fun PreviewHome(pageState: HomeUiState) {
    NewsTheme(dynamicColor = false) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            HomeScreenContent(
                contentPadding = PaddingValues(),
                uiState = HomeScreenState(NewsCategory.GENERAL, pageState),
                onCategorySelected = {},
                onRetry = {},
                onOpenDetail = {},
            )
        }
    }
}

@Preview(name = "Home - content", showBackground = true)
@Preview(name = "Home - content dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeContentPreview() {
    PreviewHome(
        HomeUiState.Content(
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
        ),
    )
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
