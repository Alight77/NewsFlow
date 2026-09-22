package io.github.alight77.news.ui.search

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.alight77.news.R
import io.github.alight77.news.ui.components.PlaceholderScreen

@Composable
fun SearchScreen(
    contentPadding: PaddingValues,
    onOpenDetail: () -> Unit,
) {
    PlaceholderScreen(
        contentPadding = contentPadding,
        title = stringResource(R.string.search_title),
        message = stringResource(R.string.search_placeholder),
        onOpenDetail = onOpenDetail,
    )
}
