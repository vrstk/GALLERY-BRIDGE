package com.gallerybridge.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Only display metadata actually known for this publication, never inferred labels. */
@Composable
internal fun GalleryPhotoHeader(
    visibleCount: Int,
    totalCount: Int,
    foundCount: Int,
    extraCount: Int,
    publicationModel: String,
    publicationTitle: String,
    publicationDate: String = ""
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (publicationModel.isNotBlank() || publicationTitle.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth().testTag("gallery-publication-summary"),
                shape = MaterialTheme.shapes.large,
                color = colors.surfaceContainerLow,
                border = BorderStroke(1.dp, colors.outlineVariant)
            ) {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "ПУБЛИКАЦИЯ", style = MaterialTheme.typography.labelSmall,
                            color = colors.primary, modifier = Modifier.weight(1f)
                        )
                        if (publicationDate.isNotBlank()) {
                            Text(
                                "Дата: $publicationDate",
                                modifier = Modifier.testTag("gallery-publication-date"),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                    if (publicationModel.isNotBlank()) {
                        Text(publicationModel, style = MaterialTheme.typography.titleMedium,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (publicationTitle.isNotBlank()) {
                        Text(publicationTitle, style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Text(
            if (totalCount > visibleCount) "Фотографии: $visibleCount из $totalCount"
                else "Фотографии: $visibleCount",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            if (extraCount > 0)
                "Всего найдено: $foundCount · Дополнительных вариантов: $extraCount"
            else "Все найденные фотографии показаны ниже",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
    }
}
