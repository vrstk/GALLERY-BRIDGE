package com.gallerybridge.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import com.gallerybridge.app.R
import com.gallerybridge.app.core.Kind
import com.gallerybridge.app.core.Media

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MediaFilters(selected: Kind?, photos: Int, videos: Int, onSelect: (Kind?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected == null, { onSelect(null) }, { Text("Все (${photos + videos})") })
        FilterChip(selected == Kind.PHOTO, { onSelect(Kind.PHOTO) }, { Text("Фото ($photos)") }, enabled = photos > 0)
        FilterChip(selected == Kind.VIDEO, { onSelect(Kind.VIDEO) }, { Text("Видео ($videos)") }, enabled = videos > 0)
    }
}

@Composable
internal fun MediaTile(
    media: Media,
    checked: Boolean,
    enabled: Boolean,
    detail: String?,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = Modifier.fillMaxWidth().clip(shape)
            .toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange)
            .semantics { contentDescription = media.name },
        shape = shape,
        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, if (checked) colors.primary else colors.outlineVariant)
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).background(colors.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                if (media.kind == Kind.VIDEO) {
                    Icon(Icons.Outlined.PlayArrow, "Видео", Modifier.size(36.dp), tint = colors.primary)
                } else {
                    val context = LocalContext.current
                    val request = remember(context, media.url) {
                        ImageRequest.Builder(context)
                            .data(media.url)
                            .httpHeaders(NetworkHeaders.Builder().set("User-Agent", "GalleryBridge/0.1").build())
                            .diskCachePolicy(CachePolicy.DISABLED)
                            .build()
                    }
                    val placeholder = painterResource(R.drawable.ic_gallery_bridge)
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop,
                        placeholder = placeholder,
                        error = placeholder
                    )
                }
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    shape = MaterialTheme.shapes.small,
                    color = if (checked) colors.primary else colors.surface
                ) {
                    Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                        if (checked) Icon(Icons.Outlined.CheckCircle, null, Modifier.size(19.dp), tint = colors.onPrimary)
                        else Surface(Modifier.size(13.dp), shape = MaterialTheme.shapes.extraSmall,
                            color = Color.Transparent, border = BorderStroke(1.dp, colors.outline)) {}
                    }
                }
            }
            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(media.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun DownloadActions(selectedCount: Int, enabled: Boolean, onChooseDestination: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    HorizontalDivider(color = colors.outlineVariant)
    Row(
        Modifier.widthIn(max = GalleryContentWidth).fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Выбрано: $selectedCount", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Button(
            onClick = onChooseDestination,
            enabled = enabled,
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = SearchButtonBlue, contentColor = Color.White)
        ) {
            Icon(painterResource(R.drawable.ic_download), null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Загрузить")
        }
    }
}

@Composable
internal fun GalleryDisclosure(title: String, expanded: Boolean, onToggle: () -> Unit) {
    OutlinedButton(onClick = onToggle, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Text(title, Modifier.weight(1f))
        Icon(if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, null)
    }
}
