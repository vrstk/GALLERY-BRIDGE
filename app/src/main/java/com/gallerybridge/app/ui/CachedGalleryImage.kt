package com.gallerybridge.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.gallerybridge.app.core.Media

private enum class PreviewState { LOADING, READY, FAILED }

/** Image loading and retries are independent of file selection and transfer. */
@Composable
internal fun CachedGalleryImage(
    media: Media,
    modifier: Modifier,
    contentScale: ContentScale,
    onAspectRatio: (Float) -> Unit = {}
) {
    val context = LocalContext.current
    var state by remember(media.url) { mutableStateOf(PreviewState.LOADING) }
    var retry by remember(media.url) { mutableIntStateOf(0) }
    val request = remember(context, media.url) {
        GalleryImageRequests.forUrl(context, media.url)
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Recreate the image node on retry while keeping the same cache key.
        key(media.url, retry) {
            AsyncImage(
                model = request,
                contentDescription = media.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                onLoading = { state = PreviewState.LOADING },
                onSuccess = { result ->
                    state = PreviewState.READY
                    val size = result.painter.intrinsicSize
                    if (size.width > 0f && size.height > 0f) {
                        onAspectRatio(size.width / size.height)
                    }
                },
                onError = { state = PreviewState.FAILED }
            )
        }
        when (state) {
            PreviewState.LOADING -> CircularProgressIndicator(
                modifier = Modifier.size(22.dp)
                    .semantics { contentDescription = "Загрузка изображения: ${media.name}" },
                strokeWidth = 2.dp
            )
            PreviewState.FAILED -> FilledTonalIconButton(onClick = {
                state = PreviewState.LOADING
                retry += 1
            }) {
                Icon(Icons.Filled.Refresh,
                    contentDescription = "Повторить загрузку изображения: ${media.name}")
            }
            PreviewState.READY -> Unit
        }
    }
}
