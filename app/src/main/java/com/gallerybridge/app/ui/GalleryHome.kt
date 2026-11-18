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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gallerybridge.app.R
import com.gallerybridge.app.core.ModelListingSources

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
    catalogCount: Int,
    onCatalog: () -> Unit,
    mailConfigured: Boolean,
    folderConfigured: Boolean,
    folderName: String,
    onMailSettings: () -> Unit,
    onFolderSettings: () -> Unit,
    latestTransfer: LatestTransfer?,
    onLatestTransfer: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("home-search-card"),
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerLow,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Новая загрузка", modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge)
                if (hasResults) {
                    TextButton(onClick = onResults) {
                        Text("Открыть результаты")
                    }
                }
            }
            GalleryLinkField(url, onUrlChange, searchAllowed, onSearch)
            FindMediaButton(
                searchAllowed && url.isNotBlank(), onSearch,
                if (ModelListingSources.accepts(url)) "Найти фотопубликации" else "Найти фотографии"
            )
        }
    }
    OutlinedCard(
        onClick = onCatalog,
        modifier = Modifier.fillMaxWidth().testTag("home-catalog-card"),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, colors.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = colors.surfaceContainerLow)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Image(
                painterResource(R.drawable.ic_gallery_bridge),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Каталог галерей", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Сохранено галерей: " + catalogCount,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Открыть каталог",
                tint = colors.onSurfaceVariant)
        }
    }
    Text("Мои хранилища", style = MaterialTheme.typography.titleMedium)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StorageCard(
            title = "Папка Android / облака",
            configured = folderConfigured,
            configuredLabel = if (folderConfigured) "Выбрана: $folderName" else
                if (folderName == "Папка не выбрана") "Выбрать папку"
                else "Доступ утрачен · выбрать снова",
            onClick = onFolderSettings,
            modifier = Modifier.fillMaxWidth(),
            testTag = "storage-android-folder"
        ) {
            Icon(painterResource(R.drawable.ic_folder), null,
                tint = colors.secondary, modifier = Modifier.size(22.dp))
        }
        StorageCard(
            title = "Mail WebDAV",
            configured = mailConfigured,
            configuredLabel = "Данные указаны",
            onClick = onMailSettings,
            modifier = Modifier.fillMaxWidth(),
            testTag = "storage-mail-webdav"
        ) {
            Icon(Icons.Outlined.Email, null, tint = colors.secondary,
                modifier = Modifier.size(22.dp))
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
        label = { Text("Ссылка на фотогалерею", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
internal fun FindMediaButton(
    enabled: Boolean, onClick: () -> Unit, label: String = "Найти фотографии"
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Icon(Icons.Outlined.Search, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
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
    testTag: String,
    icon: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.testTag(testTag),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, colors.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = colors.surfaceContainerLow)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = MaterialTheme.shapes.small, color = colors.surfaceContainerHigh) {
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) { icon() }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    if (configured || testTag == "storage-android-folder") configuredLabel
                    else "Настроить доступ",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (configured) colors.secondary else colors.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                null,
                Modifier.size(18.dp),
                tint = colors.onSurfaceVariant
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
