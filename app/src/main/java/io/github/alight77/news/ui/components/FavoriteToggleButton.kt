package io.github.alight77.news.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.alight77.news.R

@Composable
fun FavoriteToggleButton(
    isFavorite: Boolean,
    isPending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(
        if (isPending) R.string.favorite_updating
        else if (isFavorite) R.string.favorite_remove
        else R.string.favorite_add,
    )
    IconButton(
        onClick = onClick,
        enabled = !isPending,
        modifier = modifier.semantics { contentDescription = description },
    ) {
        if (isPending) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(
                text = if (isFavorite) "♥" else "♡",
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}
