package com.gallerybridge.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gallerybridge.app.R

internal data class LatestTransfer(
    val title: String,
    val destination: String,
    val completed: Int,
    val total: Int,
    val status: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GalleryHome(
    url: String,
    onUrlChange: (String) -> Unit,
    searchAllowed: Boolean,
    hasResults: Boolean,
    onSearch: () -> Unit,
    onResults: () -> Unit,
    onBrowser: () -> Unit,
    mailConfigured: Boolean,
    driveConfigured: Boolean,
    onMailSettings: () -> Unit,
    onDriveSettings: () -> Unit,
    latestTransfer: LatestTransfer?,
    onLatestTransfer: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    GalleryCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Новая загрузка", style = MaterialTheme.typography.titleLarge)
            GalleryLinkField(url, onUrlChange, searchAllowed, onSearch)
            FindMediaButton(searchAllowed && url.isNotBlank(), onSearch)
            OutlinedButton(onClick = onBrowser, enabled = searchAllowed,
                modifier = Modifier.fillMaxWidth()) { Text("Открыть во встроенном браузере") }
            if (hasResults) {
                TextButton(onClick = onResults, modifier = Modifier.align(Alignment.End)) {
                    Text("Открыть результаты")
                }
            }
        }
    }
    Text("Мои хранилища", style = MaterialTheme.typography.titleMedium)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 340.dp && LocalDensity.current.fontScale <= 1.2f) 2 else 1
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = columns,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StorageCard("Google Drive", driveConfigured, "Папка задана", onDriveSettings, Modifier.weight(1f)) {
                Image(painterResource(R.drawable.ic_google_drive), null, Modifier.size(23.dp))
            }
            StorageCard("Mail WebDAV", mailConfigured, "Данные указаны", onMailSettings, Modifier.weight(1f)) {
                Icon(Icons.Outlined.Email, null, tint = colors.secondary, modifier = Modifier.size(22.dp))
            }
        }
    }
    Text("Последняя загрузка", style = MaterialTheme.typography.titleMedium)
    if (latestTransfer == null) {
        GalleryCard {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TransferIcon()
                Text(
                    "Здесь появится первая передача",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            }
        }
    } else {
        LatestTransferCard(latestTransfer, onLatestTransfer)
    }
}

@Composable
internal fun GalleryLinkField(
    url: String,
    onUrlChange: (String) -> Unit,
    enabled: Boolean,
    onSearch: () -> Unit
) {
    OutlinedTextField(
        value = url,
        onValueChange = onUrlChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        label = { Text("Ссылка на галерею или видео", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        placeholder = { Text("https://") },
        leadingIcon = { Icon(painterResource(R.drawable.ic_link), null) },
        trailingIcon = if (enabled && url.isNotEmpty()) {
            { IconButton(onClick = { onUrlChange("") }) { Icon(Icons.Outlined.Close, "Очистить ссылку") } }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { if (enabled && url.isNotBlank()) onSearch() }),
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

@Composable
internal fun FindMediaButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = SearchButtonBlue, contentColor = Color.White)
    ) {
        Icon(Icons.Outlined.Search, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Найти медиафайлы")
    }
}

@Composable
internal fun GalleryCard(content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        content = content
    )
}

@Composable
private fun StorageCard(
    title: String,
    configured: Boolean,
    configuredLabel: String,
    onClick: () -> Unit,
    modifier: Modifier,
    icon: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.testTag(if (title == "Mail WebDAV") "storage-mail-webdav" else "storage-google-drive"),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, colors.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = colors.surfaceContainerLow)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.small, color = colors.surfaceContainerHigh) {
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { icon() }
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    null,
                    Modifier.size(18.dp),
                    tint = colors.onSurfaceVariant
                )
            }
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                if (configured) configuredLabel else "Настроить доступ",
                style = MaterialTheme.typography.bodySmall,
                color = if (configured) colors.secondary else colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TransferIcon() {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(R.drawable.ic_download), null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun LatestTransferCard(transfer: LatestTransfer, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val progress = if (transfer.total > 0) {
        (transfer.completed.toFloat() / transfer.total).coerceIn(0f, 1f)
    } else 0f
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, colors.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = colors.surfaceContainerLow)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TransferIcon()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(transfer.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${transfer.destination} · ${transfer.status}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                if (transfer.total > 0) {
                    val percent = transfer.completed * 100 / transfer.total
                    Text("${transfer.completed} из ${transfer.total} файлов · $percent%", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.secondary,
                        trackColor = colors.surfaceContainerHighest
                    )
                }
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = colors.onSurfaceVariant)
        }
    }
}
