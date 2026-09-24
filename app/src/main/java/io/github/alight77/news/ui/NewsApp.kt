package io.github.alight77.news.ui

import android.content.res.Configuration
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.alight77.news.R
import io.github.alight77.news.domain.model.Article
import io.github.alight77.news.domain.repository.FavoriteRepository
import io.github.alight77.news.domain.repository.NewsRepository
import io.github.alight77.news.ui.article.ArticleDetailScreen
import io.github.alight77.news.ui.article.ArticleSessionViewModel
import io.github.alight77.news.ui.favorites.FavoritesScreen
import io.github.alight77.news.ui.favorites.FavoritesViewModel
import io.github.alight77.news.ui.home.HomeScreen
import io.github.alight77.news.ui.home.HomeViewModel
import io.github.alight77.news.ui.search.SearchScreen
import io.github.alight77.news.ui.search.SearchViewModel
import io.github.alight77.news.ui.theme.NewsTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Composable
fun NewsApp(repository: NewsRepository) {
    NewsApp(repository, EmptyFavoriteRepository)
}

@Composable
fun NewsApp(
    repository: NewsRepository,
    favoriteRepository: FavoriteRepository,
) {
    val navController = rememberNavController()
    val articleSession: ArticleSessionViewModel = viewModel()
    val homeViewModel: HomeViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(repository) as T
    })
    val searchViewModel: SearchViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SearchViewModel(repository) as T
    })
    val favoritesViewModel: FavoritesViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = FavoritesViewModel(favoriteRepository) as T
    })
    val favoritesUiState by favoritesViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val favoriteUpdateFailed = stringResource(R.string.favorite_update_failed)
    LaunchedEffect(favoritesViewModel, favoriteUpdateFailed) {
        favoritesViewModel.events.collect {
            snackbarHostState.showSnackbar(
                message = favoriteUpdateFailed,
                duration = SnackbarDuration.Short,
            )
        }
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val selectedDestination = bottomNavigationItems.firstOrNull { item ->
        currentDestination?.hierarchy?.any { it.route == item.destination.route } == true
    }?.destination
    val openArticle: (Article) -> Unit = { article ->
        articleSession.open(article)
        navController.navigate("article_detail/${Uri.encode(article.id)}")
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (currentDestination?.route != NewsDestination.Detail.route) {
                NewsBottomNavigation(
                    selectedDestination = selectedDestination,
                    onDestinationSelected = { destination ->
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NewsDestination.Home.route,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(NewsDestination.Home.route) {
                HomeScreen(
                    contentPadding = innerPadding,
                    viewModel = homeViewModel,
                    favoriteArticleIds = favoritesUiState.favoriteArticleIds,
                    pendingFavoriteArticleIds = favoritesUiState.pendingArticleIds,
                    onToggleFavorite = favoritesViewModel::toggle,
                    onOpenDetail = openArticle,
                )
            }
            composable(NewsDestination.Search.route) {
                SearchScreen(
                    contentPadding = innerPadding,
                    viewModel = searchViewModel,
                    favoriteArticleIds = favoritesUiState.favoriteArticleIds,
                    pendingFavoriteArticleIds = favoritesUiState.pendingArticleIds,
                    onToggleFavorite = favoritesViewModel::toggle,
                    onOpenDetail = openArticle,
                )
            }
            composable(NewsDestination.Favorites.route) {
                FavoritesScreen(
                    contentPadding = innerPadding,
                    uiState = favoritesUiState,
                    onToggleFavorite = favoritesViewModel::toggle,
                    onOpenDetail = openArticle,
                )
            }
            composable(
                route = NewsDestination.Detail.route,
                arguments = listOf(navArgument("articleId") { type = NavType.StringType }),
            ) { entry ->
                val articleId = entry.arguments?.getString("articleId").orEmpty()
                ArticleDetailScreen(
                    contentPadding = innerPadding,
                    article = articleSession.articleForDetail(articleId),
                    favoriteArticleIds = favoritesUiState.favoriteArticleIds,
                    pendingFavoriteArticleIds = favoritesUiState.pendingArticleIds,
                    onToggleFavorite = favoritesViewModel::toggle,
                    onNavigateUp = navController::navigateUp,
                )
            }
        }
    }
}

@Composable
private fun NewsBottomNavigation(
    selectedDestination: NewsDestination?,
    onDestinationSelected: (NewsDestination) -> Unit,
) {
    NavigationBar {
        bottomNavigationItems.forEach { item ->
            val selected = selectedDestination == item.destination
            NavigationBarItem(
                selected = selected,
                onClick = { onDestinationSelected(item.destination) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(stringResource(item.destination.labelRes)) },
            )
        }
    }
}

@Preview(name = "Bottom navigation", showBackground = true)
@Preview(name = "Bottom navigation dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NewsBottomNavigationPreview() {
    NewsTheme(dynamicColor = false) {
        NewsBottomNavigation(
            selectedDestination = NewsDestination.Home,
            onDestinationSelected = {},
        )
    }
}

private data class BottomNavigationItem(
    val destination: NewsDestination,
    val icon: ImageVector,
)

private val bottomNavigationItems = listOf(
    BottomNavigationItem(NewsDestination.Home, Icons.Filled.Home),
    BottomNavigationItem(NewsDestination.Search, Icons.Filled.Search),
    BottomNavigationItem(NewsDestination.Favorites, Icons.Filled.Favorite),
)

private sealed class NewsDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
) {
    data object Home : NewsDestination("home", R.string.nav_home)
    data object Search : NewsDestination("search", R.string.nav_search)
    data object Favorites : NewsDestination("favorites", R.string.nav_favorites)
    data object Detail : NewsDestination("article_detail/{articleId}", R.string.detail_title)
}

private object EmptyFavoriteRepository : FavoriteRepository {
    override fun observeFavorites(): Flow<List<Article>> = flowOf(emptyList())

    override fun observeFavoriteArticleIds(): Flow<Set<String>> = flowOf(emptySet())

    override suspend fun isFavorite(articleId: String): Boolean = false

    override suspend fun save(article: Article) = Unit

    override suspend fun remove(articleId: String) = Unit
}
