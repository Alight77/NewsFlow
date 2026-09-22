package io.github.alight77.news.ui.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.alight77.news.R
import io.github.alight77.news.ui.components.PlaceholderScreen

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onOpenDetail: () -> Unit,
) {
    PlaceholderScreen(
        contentPadding = contentPadding,
        title = stringResource(R.string.home_title),
        message = stringResource(R.string.home_placeholder),
        onOpenDetail = onOpenDetail,
    )
}
