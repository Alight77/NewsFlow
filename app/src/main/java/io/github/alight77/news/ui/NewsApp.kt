package io.github.alight77.news.ui

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.alight77.news.R
import io.github.alight77.news.domain.repository.NewsRepository
import io.github.alight77.news.ui.article.ArticleDetailScreen
import io.github.alight77.news.ui.article.ArticleSessionViewModel
import io.github.alight77.news.ui.favorites.FavoritesScreen
import io.github.alight77.news.ui.home.HomeScreen
import io.github.alight77.news.ui.home.HomeViewModel
import io.github.alight77.news.ui.search.SearchScreen

@Composable
fun NewsApp(repository: NewsRepository) {
    val navController = rememberNavController()
    val articleSession: ArticleSessionViewModel = viewModel()
    val homeViewModel: HomeViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(repository) as T
    })
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (currentDestination?.route != NewsDestination.Detail.route) {
                NewsBottomNavigation(
                    currentDestination = currentDestination,
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
                    onOpenDetail = { article ->
                        articleSession.open(article)
                        navController.navigate("article_detail/${Uri.encode(article.id)}")
                    },
                )
            }
            composable(NewsDestination.Search.route) {
                SearchScreen(contentPadding = innerPadding)
            }
            composable(NewsDestination.Favorites.route) {
                FavoritesScreen(contentPadding = innerPadding)
            }
            composable(
                route = NewsDestination.Detail.route,
                arguments = listOf(navArgument("articleId") { type = NavType.StringType }),
            ) { entry ->
                val articleId = entry.arguments?.getString("articleId").orEmpty()
                ArticleDetailScreen(
                    contentPadding = innerPadding,
                    article = articleSession.articleForDetail(articleId),
                    onNavigateUp = navController::navigateUp,
                )
            }
        }
    }
}

@Composable
private fun NewsBottomNavigation(
    currentDestination: NavDestination?,
    onDestinationSelected: (NewsDestination) -> Unit,
) {
    NavigationBar {
        NewsDestination.topLevel.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any {
                it.route == destination.route
            } == true
            NavigationBarItem(
                selected = selected,
                onClick = { onDestinationSelected(destination) },
                icon = { Text(destination.marker) },
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
    }
}

private sealed class NewsDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val marker: String,
) {
    data object Home : NewsDestination("home", R.string.nav_home, "首")
    data object Search : NewsDestination("search", R.string.nav_search, "搜")
    data object Favorites : NewsDestination("favorites", R.string.nav_favorites, "藏")
    data object Detail : NewsDestination("article_detail/{articleId}", R.string.detail_title, "")

    companion object {
        val topLevel = listOf(Home, Search, Favorites)
    }
}
