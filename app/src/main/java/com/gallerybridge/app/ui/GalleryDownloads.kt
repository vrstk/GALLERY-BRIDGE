package com.gallerybridge.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gallerybridge.app.R
import com.gallerybridge.app.core.Kind
import com.gallerybridge.app.core.Media

@Composable
internal fun MediaTile(
    media: Media,
    checked: Boolean,
    enabled: Boolean,
    detail: String?,
    onOpen: () -> Unit,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = Modifier.fillMaxWidth().clip(shape)
            .clickable(onClick = onOpen)
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
                CachedGalleryImage(
                    media = media,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
                Checkbox(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                        .size(34.dp).background(
                            if (checked) colors.primaryContainer else colors.surface.copy(alpha = 0.85f),
                            MaterialTheme.shapes.small
                        )
                )
            }
            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(media.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}


/**
 * Zero automatic network requests: only names, selection and an explicit
 * "Открыть" action. Opening a photo can then fetch it on user request.
 */
@Composable
internal fun MediaListRow(
    media: Media,
    checked: Boolean,
    enabled: Boolean,
    detail: String?,
    onOpen: () -> Unit,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, if (checked) colors.primary else colors.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier.semantics {
                    contentDescription = "Выбрать файл: " + media.name
                }
            )
            Column(Modifier.weight(1f)) {
                Text(
                    media.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (detail != null) Text(
                    detail, style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant, maxLines = 2
                )
            }
            TextButton(onClick = onOpen) { Text("Открыть") }
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
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary, contentColor = colors.onPrimary
            )
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
