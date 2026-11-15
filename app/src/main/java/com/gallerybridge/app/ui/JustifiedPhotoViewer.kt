package com.gallerybridge.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.gallerybridge.app.R
import com.gallerybridge.app.core.JustifiedRows
import com.gallerybridge.app.core.Media

@Composable
private fun GalleryPhoto(
    media: Media,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    onAspectRatio: (Float) -> Unit = {}
) {
    val context = LocalContext.current
    val request = remember(context, media.url) {
        ImageRequest.Builder(context)
            .data(media.url)
            .apply {
                if (media.url.startsWith("https://")) {
                    httpHeaders(NetworkHeaders.Builder()
                        .set("User-Agent", "GalleryBridge/0.1").build())
                }
            }.build()
    }
    val placeholder = painterResource(R.drawable.ic_gallery_bridge)
    AsyncImage(
        model = request,
        contentDescription = media.name,
        modifier = modifier,
        contentScale = contentScale,
        placeholder = placeholder,
        error = placeholder,
        onSuccess = { result ->
            val intrinsic = result.painter.intrinsicSize
            if (intrinsic.width > 0f && intrinsic.height > 0f) {
                onAspectRatio(intrinsic.width / intrinsic.height)
            }
        }
    )
}

@Composable
internal fun JustifiedPhotoRow(
    row: JustifiedRows.Row,
    selected: Set<String>,
    enabled: Boolean,
    spacing: Dp,
    onOpen: (Media) -> Unit,
    onSelection: (Media, Boolean) -> Unit,
    onAspectRatio: (Media, Float) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
        row.photos.forEachIndexed { index, media ->
            val checked = media.url in selected
            Box(
                Modifier.width(row.widths[index].dp)
                    .height(row.height.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                GalleryPhoto(
                    media = media,
                    modifier = Modifier.fillMaxSize().clickable { onOpen(media) },
                    onAspectRatio = { onAspectRatio(media, it) }
                )
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    color = if (checked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    shape = MaterialTheme.shapes.small
                ) {
                    IconButton(
                        onClick = { onSelection(media, !checked) },
                        enabled = enabled,
                        modifier = Modifier.size(34.dp)
                    ) {
                        if (checked) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = "Снять выделение: ${media.name}",
                                modifier = Modifier.size(21.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Surface(
                                modifier = Modifier.size(19.dp),
                                shape = MaterialTheme.shapes.extraSmall,
                                color = Color.Transparent,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.5.dp, MaterialTheme.colorScheme.onSurface
                                )
                            ) {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun FullscreenPhotoViewer(
    photos: List<Media>,
    initialIndex: Int,
    selected: Set<String>,
    onSelection: (Media, Boolean) -> Unit,
    onClose: () -> Unit
) {
    if (photos.isEmpty()) return
    val pager = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, photos.lastIndex),
        pageCount = { photos.size }
    )
    var currentZoom by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(pager.currentPage) { currentZoom = 1f }
    BackHandler(onBack = onClose)
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Column(
            Modifier.fillMaxSize().background(Color.Black)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "Закрыть фото", tint = Color.White)
                }
                Text(
                    "${pager.currentPage + 1} / ${photos.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                val current = photos[pager.currentPage.coerceIn(0, photos.lastIndex)]
                Checkbox(
                    checked = current.url in selected,
                    onCheckedChange = { onSelection(current, it) }
                )
            }
            HorizontalPager(
                state = pager,
                userScrollEnabled = currentZoom <= 1.01f,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { page ->
                val media = photos[page]
                var zoom by remember(media.url) { mutableFloatStateOf(1f) }
                var panX by remember(media.url) { mutableFloatStateOf(0f) }
                var panY by remember(media.url) { mutableFloatStateOf(0f) }
                val transform = rememberTransformableState { scale, offset, _ ->
                    zoom = (zoom * scale).coerceIn(1f, 5f)
                    if (zoom > 1.01f) {
                        panX += offset.x
                        panY += offset.y
                    } else {
                        panX = 0f
                        panY = 0f
                    }
                    if (page == pager.currentPage) currentZoom = zoom
                }
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val maxX = with(density) { maxWidth.toPx() } * (zoom - 1f) / 2f
                    val maxY = with(density) { maxHeight.toPx() } * (zoom - 1f) / 2f
                    Box(
                        modifier = Modifier.fillMaxSize().clipToBounds(),
                        contentAlignment = Alignment.Center
                    ) {
                        GalleryPhoto(
                            media = media,
                            modifier = Modifier.fillMaxSize()
                                .pointerInput(media.url) {
                                    detectTapGestures(onDoubleTap = {
                                        zoom = if (zoom > 1f) 1f else 2.5f
                                        if (zoom == 1f) { panX = 0f; panY = 0f }
                                        currentZoom = zoom
                                    })
                                }
                                // At 1x scale leave one-finger horizontal drags to
                                // HorizontalPager. Pinch-zoom still works; after zooming
                                // past 1x, this image handles panning instead.
                                .transformable(
                                    state = transform,
                                    canPan = { zoom > 1.01f }
                                )
                                .graphicsLayer {
                                    scaleX = zoom
                                    scaleY = zoom
                                    translationX = panX.coerceIn(-maxX, maxX)
                                    translationY = panY.coerceIn(-maxY, maxY)
                                    clip = true
                                }
                        )
                    }
                }
            }
            Text(
                photos[pager.currentPage.coerceIn(0, photos.lastIndex)].name,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            )
        }
    }
}
