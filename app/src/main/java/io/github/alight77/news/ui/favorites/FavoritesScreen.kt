package io.github.alight77.news.ui.favorites

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.alight77.news.R
import io.github.alight77.news.ui.components.PlaceholderScreen

@Composable
fun FavoritesScreen(
    contentPadding: PaddingValues,
) {
    PlaceholderScreen(
        contentPadding = contentPadding,
        title = stringResource(R.string.favorites_title),
        message = stringResource(R.string.favorites_placeholder),
    )
}
