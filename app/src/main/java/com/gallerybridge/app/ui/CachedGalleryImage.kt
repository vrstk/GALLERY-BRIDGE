package com.gallerybridge.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.gallerybridge.app.R
import com.gallerybridge.app.core.Media

/**
 * The same image request and cache keys are used by mosaic, tiles and viewer.
 * Failures are not cached as successful previews. Retry is user initiated,
 * avoiding endless requests on a slow or unavailable network.
 */
@Composable
internal fun CachedGalleryImage(
    media: Media,
    modifier: Modifier,
    contentScale: ContentScale,
    onAspectRatio: (Float) -> Unit = {}
) {
    val context = LocalContext.current
    var failed by remember(media.url) { mutableStateOf(false) }
    var retry by remember(media.url) { mutableIntStateOf(0) }
    val request = remember(context, media.url, retry) {
        GalleryImageRequests.forUrl(context, media.url)
    }
    val placeholder = painterResource(R.drawable.ic_gallery_bridge)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // A new composition key forces a fresh request when retry is tapped.
        key(retry) {
            AsyncImage(
                model = request,
                contentDescription = media.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                placeholder = placeholder,
                error = placeholder,
                onLoading = { failed = false },
                onSuccess = { result ->
                    failed = false
                    val size = result.painter.intrinsicSize
                    if (size.width > 0f && size.height > 0f) {
                        onAspectRatio(size.width / size.height)
                    }
                },
                onError = { failed = true }
            )
        }
        if (failed) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
            ) {
                TextButton(onClick = {
                    failed = false
                    retry += 1
                }) {
                    Text("Повторить", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
