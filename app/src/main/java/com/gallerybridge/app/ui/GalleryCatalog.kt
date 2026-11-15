package com.gallerybridge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gallerybridge.app.core.CatalogGallery
import java.util.Locale

/**
 * Gallery catalog is a source-page index, not a second copy of media files.
 * Users must explicitly open a record before a network scan can begin.
 */
@Composable
internal fun GalleryCatalog(
    entries: List<CatalogGallery>,
    onOpen: (CatalogGallery) -> Unit,
    onRemove: (CatalogGallery) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var alphabetic by rememberSaveable { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<CatalogGallery?>(null) }
    val locale = Locale.getDefault()
    val filtered = remember(entries, query, alphabetic) {
        val needle = query.trim().lowercase(locale)
        val matching = entries.filter {
            needle.isEmpty() || listOf(it.model, it.title, it.date, it.url)
                .any { field -> field.lowercase(locale).contains(needle) }
        }
        if (alphabetic) matching.sortedWith(compareBy<CatalogGallery> {
            it.model.lowercase(locale)
        }.thenBy { it.title.lowercase(locale) }) else matching.sortedByDescending { it.updatedAt }
    }

    Column(Modifier.widthIn(max = GalleryContentWidth).fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("Поиск по каталогу") },
            placeholder = { Text("Модель, название или ссылка") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilterChip(
                selected = alphabetic, onClick = { alphabetic = !alphabetic },
                label = { Text("По модели А-Я") }
            )
            Spacer(Modifier.weight(1f))
            Text(
                "Галерей: " + filtered.size,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        if (entries.isEmpty()) {
            Text(
                "Каталог пока пуст. Сохраните имя модели и название галереи на экране загрузки. " +
                    "Новые передачи по HTTPS-ссылкам также появятся здесь.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp)
            )
        } else if (filtered.isEmpty()) {
            Text("По запросу ничего не найдено.", modifier = Modifier.padding(top = 16.dp))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.url }) { item ->
                    OutlinedCard(
                        onClick = { onOpen(item) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(
                                item.model.ifBlank { "Без имени" },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                item.title.ifBlank { "Без названия" },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                            if (item.date.isNotBlank()) {
                                Text(item.date, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                item.url, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (item.target.isNotBlank()) {
                                Text(
                                    item.target + " · " + item.outcome +
                                        " · " + item.completed + "/" + item.total,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { pendingRemoval = item }) {
                                    Text("Убрать из каталога")
                                }
                                TextButton(onClick = { onOpen(item) }) {
                                    Text("К загрузке")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    pendingRemoval?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Убрать галерею из каталога?") },
            text = { Text("Удалится только запись каталога. Файлы, история и очередь не изменятся.") },
            confirmButton = {
                TextButton(onClick = { onRemove(entry); pendingRemoval = null }) {
                    Text("Убрать")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) { Text("Отмена") }
            }
        )
    }
}
