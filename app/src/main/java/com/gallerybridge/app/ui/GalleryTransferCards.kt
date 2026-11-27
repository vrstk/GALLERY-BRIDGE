package com.gallerybridge.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gallerybridge.app.transfer.HistoryStore
import com.gallerybridge.app.transfer.QueueStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Purely presentational transfer summaries. No writes to the queue, history or
 * cloud provider. All outcome labels describe persisted results, not cloud sync.
 */
@Composable
internal fun GalleryQueueSummary(queue: QueueStore.Snapshot) {
    val done = queue.entries.count { it.state == "DONE" }
    val skipped = queue.entries.count { it.state == "SKIPPED" }
    val failed = queue.entries.count { it.state == "FAILED" || it.state == "BLOCKED" }
    val completed = done + skipped
    val total = queue.entries.size
    val colors = MaterialTheme.colorScheme

    OutlinedCard(
        modifier = Modifier.fillMaxWidth().testTag("queue-summary"),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, colors.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = colors.surfaceContainerLow)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(queue.model.ifBlank { "Без имени" },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Готово: $completed из $total",
                    style = MaterialTheme.typography.labelMedium)
            }
            Text(queue.title.ifBlank { "Без названия" },
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant)
            if (total > 0) {
                // Custom track does not show a false completion stop-dot at zero.
                Box(Modifier.fillMaxWidth().height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.surfaceContainerHighest)) {
                    Box(Modifier.fillMaxWidth((completed.toFloat() / total).coerceIn(0f, 1f))
                        .fillMaxHeight().background(colors.secondary))
                }
            }
            Text(queue.target,
                style = MaterialTheme.typography.labelMedium,
                color = colors.secondary)
            Text(
                (if (queue.target == "Папка Android") "Передано" else "Загружено") +
                    ": $done · Уже были: $skipped · Ошибки: $failed · Осталось: ${queue.remaining}",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant
            )
            if (queue.blocked > 0) {
                Text("Требуют ручной проверки: ${queue.blocked}",
                    style = MaterialTheme.typography.bodySmall, color = colors.error)
            }
            if (queue.target == "Папка Android" && done > 0) {
                Text("Синхронизация зависит от выбранного провайдера папки.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun GalleryHistoryCard(record: HistoryStore.Record) {
    var expanded by rememberSaveable(record.id) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().testTag("history-record-card"),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, colors.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = colors.surfaceContainerLow)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(record.model.ifBlank { "Без имени" },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault())
                        .format(Date(record.updatedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }
            Text(record.title.ifBlank { "Без названия" },
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(record.target, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, color = colors.secondary)
                Text("· ${record.outcome.ifBlank { "Исход не указан" }}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Готово: ${record.uploaded + record.skipped} из ${record.total}" +
                    " · Осталось: ${record.remaining}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = { expanded = !expanded },
                    modifier = Modifier.size(40.dp).testTag("history-details-toggle")) {
                    Icon(
                        if (expanded) Icons.Outlined.KeyboardArrowUp
                        else Icons.Outlined.KeyboardArrowDown,
                        contentDescription = if (expanded) "Скрыть статистику"
                            else "Показать статистику"
                    )
                }
            }
            if (expanded) {
                HorizontalDivider(color = colors.outlineVariant)
                Text("Загружено: ${record.uploaded} · Уже были: ${record.skipped}" +
                    " · Ошибки: ${record.failed}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant)
            }
        }
    }
}
