package com.gallerybridge.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gallerybridge.app.R

/** Read-only summaries: do not imply the Android provider has finished cloud sync. */
@Composable
internal fun StorageDestinationCards(
    selected: String,
    folderName: String,
    folderSelected: Boolean,
    folderReady: Boolean,
    mailConfigured: Boolean,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    val folderStatus = when {
        folderReady -> "Доступ к папке предоставлен"
        folderSelected -> "Нет доступа — выберите папку снова"
        else -> "Папка не выбрана"
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StorageDestinationCard(
            title = "Папка Android / облака",
            subtitle = if (folderReady) folderName else folderStatus,
            status = if (folderReady) folderStatus else null,
            isSelected = selected == "Папка Android",
            enabled = enabled,
            testTag = "storage-destination-android",
            onClick = { onSelect("Папка Android") }
        ) {
            Icon(
                painterResource(R.drawable.ic_folder), contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(25.dp)
            )
        }
        StorageDestinationCard(
            title = "Mail WebDAV",
            subtitle = if (mailConfigured) "Почта и пароль указаны" else
                "Введите почту и пароль приложения",
            status = null,
            isSelected = selected == "Mail WebDAV",
            enabled = enabled,
            testTag = "storage-destination-mail",
            onClick = { onSelect("Mail WebDAV") }
        ) {
            Icon(
                Icons.Outlined.Email, contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(25.dp)
            )
        }
    }
}

@Composable
private fun StorageDestinationCard(
    title: String,
    subtitle: String,
    status: String?,
    isSelected: Boolean,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().testTag(testTag).semantics { selected = isSelected },
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, if (isSelected) colors.primary else colors.outlineVariant),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) colors.primaryContainer else colors.surfaceContainerLow
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = MaterialTheme.shapes.small, color = colors.surfaceContainerHigh) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { icon() }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle, style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant, maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (status != null) Text(
                    status, style = MaterialTheme.typography.labelSmall,
                    color = colors.secondary
                )
            }
            RadioButton(selected = isSelected, onClick = null, enabled = enabled)
        }
    }
}
