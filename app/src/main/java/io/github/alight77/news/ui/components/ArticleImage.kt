package io.github.alight77.news.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import io.github.alight77.news.R

@Composable
fun ArticleImage(imageUrl: String?, modifier: Modifier = Modifier) {
    val imageModifier = modifier.height(180.dp)
    if (imageUrl == null) {
        ImageUnavailable(imageModifier)
    } else {
        SubcomposeAsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = imageModifier,
            loading = { ImageUnavailable(Modifier.fillMaxWidth().height(180.dp)) },
            error = { ImageUnavailable(Modifier.fillMaxWidth().height(180.dp)) },
            success = { SubcomposeAsyncImageContent() },
        )
    }
}

@Composable
private fun ImageUnavailable(modifier: Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.image_unavailable),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
