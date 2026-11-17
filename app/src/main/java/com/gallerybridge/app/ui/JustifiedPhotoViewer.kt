package com.gallerybridge.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.text.style.TextOverflow
import com.gallerybridge.app.core.JustifiedRows
import com.gallerybridge.app.core.Media
import kotlin.math.max

@Composable
private fun GalleryPhoto(
    media: Media,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    onAspectRatio: (Float) -> Unit = {}
) {
    CachedGalleryImage(
        media = media,
        modifier = modifier,
        contentScale = contentScale,
        onAspectRatio = onAspectRatio
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

/**
 * Fullscreen edge-to-edge viewer: a photo always uses the full viewport, while
 * compact translucent controls float over it instead of shrinking its bounds.
 * Transform state and current pager page survive a normal Activity recreation.
 */
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
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    var zoomPhoto by rememberSaveable {
        mutableStateOf(photos[initialIndex.coerceIn(0, photos.lastIndex)].url)
    }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var panX by rememberSaveable { mutableFloatStateOf(0f) }
    var panY by rememberSaveable { mutableFloatStateOf(0f) }
    var activeAspectRatio by remember { mutableFloatStateOf(0f) }
    var viewerSize by remember { mutableStateOf(IntSize.Zero) }

    // Switching pictures resets the transform, but rotation restores it for the
    // current picture. A stored transform must never disable swiping on a new one.
    LaunchedEffect(pager.currentPage, photos) {
        val newPhoto = photos[pager.currentPage.coerceIn(0, photos.lastIndex)].url
        if (zoomPhoto != newPhoto) {
            zoomPhoto = newPhoto
            zoom = 1f
            panX = 0f
            panY = 0f
            activeAspectRatio = 0f
        }
    }
    BackHandler(onBack = onClose)
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            onDispose {
                // Never leave the host Activity with hidden system bars.
                window?.let {
                    WindowCompat.getInsetsController(it, view).show(
                        WindowInsetsCompat.Type.systemBars()
                    )
                }
            }
        }
        SideEffect {
            window?.let {
                val controller = WindowCompat.getInsetsController(it, view)
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (controlsVisible) controller.show(WindowInsetsCompat.Type.systemBars())
                else controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }

        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .onSizeChanged { viewerSize = it }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = {
                            zoom = if (zoom > 1.01f) 1f
                                else PhotoZoomMath.doubleTapScale(
                                    viewerSize.width.toFloat(),
                                    viewerSize.height.toFloat(),
                                    activeAspectRatio
                                )
                            panX = 0f
                            panY = 0f
                        }
                    )
                }
                .testTag("fullscreen-photo-viewer")
        ) {
            HorizontalPager(
                state = pager,
                key = { photos[it].url },
                userScrollEnabled = zoom <= 1.01f,
                modifier = Modifier.fillMaxSize().testTag("fullscreen-photo-pager")
            ) { page ->
                val media = photos[page]
                var ratio by rememberSaveable(media.url) { mutableFloatStateOf(0f) }
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val boundsWidth = constraints.maxWidth.toFloat()
                    val boundsHeight = constraints.maxHeight.toFloat()
                    val isCurrent = page == pager.currentPage
                    val currentScale = if (isCurrent) zoom else 1f
                    val bounds = PhotoZoomMath.bounds(
                        boundsWidth, boundsHeight, ratio, currentScale
                    )
                    val transform = rememberTransformableState { zoomChange, offset, _ ->
                        if (isCurrent) {
                            val candidate = (zoom * zoomChange).coerceIn(1f, PhotoZoomMath.MAX_SCALE)
                            val next = PhotoZoomMath.bounds(
                                boundsWidth, boundsHeight, ratio, candidate
                            )
                            zoom = candidate
                            if (candidate > 1.01f) {
                                panX = (panX + offset.x).coerceIn(-next.x, next.x)
                                panY = (panY + offset.y).coerceIn(-next.y, next.y)
                            } else {
                                panX = 0f
                                panY = 0f
                            }
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxSize().clipToBounds()
                            .testTag(if (isCurrent) "fullscreen-photo-image"
                                else "fullscreen-photo-image-other")
                            .transformable(
                                state = transform,
                                canPan = { isCurrent && zoom > 1.01f }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        GalleryPhoto(
                            media = media,
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                scaleX = currentScale
                                scaleY = currentScale
                                translationX = if (isCurrent) panX.coerceIn(-bounds.x, bounds.x)
                                    else 0f
                                translationY = if (isCurrent) panY.coerceIn(-bounds.y, bounds.y)
                                    else 0f
                            },
                            onAspectRatio = { measured ->
                                if (measured > 0f && measured.isFinite() && ratio != measured) {
                                    ratio = measured
                                    if (isCurrent) activeAspectRatio = measured
                                }
                            }
                        )
                    }
                }
            }

            if (controlsVisible) {
                Row(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
                        .testTag("fullscreen-photo-controls-top")
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.52f), Color.Transparent)
                            )
                        )
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "Закрыть фото",
                            tint = Color.White)
                    }
                    Text(
                        "${pager.currentPage + 1} / ${photos.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                            .testTag("fullscreen-photo-position")
                    )
                    val current = photos[pager.currentPage.coerceIn(0, photos.lastIndex)]
                    Checkbox(
                        checked = current.url in selected,
                        onCheckedChange = { onSelection(current, it) },
                        modifier = Modifier.testTag("fullscreen-photo-selection"),
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF86BBFF),
                            uncheckedColor = Color.White,
                            checkmarkColor = Color.Black
                        )
                    )
                }
            }

            if (controlsVisible) {
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .testTag("fullscreen-photo-controls-bottom")
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.52f))
                            )
                        )
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        photos[pager.currentPage.coerceIn(0, photos.lastIndex)].name,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
