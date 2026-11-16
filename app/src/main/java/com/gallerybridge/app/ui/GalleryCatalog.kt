package com.gallerybridge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gallerybridge.app.core.CatalogGallery
import com.gallerybridge.app.core.GalleryCatalogFilter
import com.gallerybridge.app.core.GalleryFields
import com.gallerybridge.app.core.GalleryKeys

/**
 * Gallery catalog is a source-page index, not a second copy of media files.
 * Users must explicitly open a record before a network scan can begin.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GalleryCatalog(
    entries: List<CatalogGallery>,
    onOpen: (CatalogGallery) -> Unit,
    onRemove: (CatalogGallery) -> Unit,
    onEdit: (CatalogGallery, GalleryFields) -> Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
    backupStatus: String
) {
    var query by rememberSaveable { mutableStateOf("") }
    var alphabetic by rememberSaveable { mutableStateOf(false) }
    var onlyTransferred by rememberSaveable { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<CatalogGallery?>(null) }
    var pendingEdit by remember { mutableStateOf<CatalogGallery?>(null) }
    val filtered = remember(entries, query, alphabetic, onlyTransferred) {
        GalleryCatalogFilter.apply(entries, query, alphabetic, onlyTransferred)
    }

    Column(Modifier.widthIn(max = GalleryContentWidth).fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("Поиск по каталогу") },
            placeholder = { Text("Модель, название или ссылка") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            trailingIcon = if (query.isNotEmpty()) {
                { TextButton(onClick = { query = "" }) { Text("Сброс") } }
            } else null
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = !alphabetic, onClick = { alphabetic = false },
                label = { Text("Новые") })
            FilterChip(selected = alphabetic, onClick = { alphabetic = true },
                label = { Text("По модели А-Я") })
            FilterChip(selected = onlyTransferred, onClick = { onlyTransferred = !onlyTransferred },
                label = { Text("С передачами") })
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onExport, enabled = entries.isNotEmpty()) {
                Text("Экспорт JSON")
            }
            OutlinedButton(onClick = onImport) {
                Text("Импорт JSON")
            }
        }
        Text(
            "Копия содержит ссылки и названия галерей, но не фотографии или пароли. " +
                "Храните её в личном защищённом месте.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (backupStatus.isNotBlank()) {
            Text(backupStatus, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary)
        }
        Text(
            "Показано: " + filtered.size + " из " + entries.size,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (entries.isEmpty()) {
            Text(
                "Каталог пока пуст. Сохраните имя модели и название галереи на экране загрузки. " +
                    "Новые передачи по HTTPS-ссылкам также появятся здесь.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp)
            )
        } else if (filtered.isEmpty()) {
            Column(Modifier.padding(top = 16.dp)) {
                Text("По текущему поиску и фильтрам ничего не найдено.")
                TextButton(onClick = { query = ""; onlyTransferred = false }) {
                    Text("Сбросить фильтры")
                }
            }
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
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                maxItemsInEachRow = 3
                            ) {
                                TextButton(onClick = { pendingRemoval = item }) { Text("Убрать") }
                                TextButton(onClick = { pendingEdit = item }) { Text("Изменить") }
                                TextButton(onClick = { onOpen(item) }) { Text("Открыть") }
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
    pendingEdit?.let { entry ->
        var editModel by remember(entry.url) { mutableStateOf(entry.model) }
        var editTitle by remember(entry.url) { mutableStateOf(entry.title) }
        var editDate by remember(entry.url) { mutableStateOf(entry.date) }
        var saveError by remember(entry.url) { mutableStateOf("") }
        val invalidDate = !GalleryKeys.validDate(editDate.trim())
        AlertDialog(
            onDismissRequest = { pendingEdit = null },
            title = { Text("Изменить галерею") },
            text = {
                Column(
                    Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(entry.url, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(editModel, { editModel = it; saveError = "" },
                        label = { Text("Модель") }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true)
                    OutlinedTextField(editTitle, { editTitle = it; saveError = "" },
                        label = { Text("Название галереи") }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true)
                    OutlinedTextField(editDate, { editDate = it; saveError = "" },
                        label = { Text("Дата (ГГГГ-ММ-ДД)") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        isError = invalidDate,
                        supportingText = { Text("Необязательно") })
                    if (invalidDate) Text("Укажите реальную дату в формате ГГГГ-ММ-ДД.",
                        color = MaterialTheme.colorScheme.error)
                    if (saveError.isNotEmpty()) Text(saveError,
                        color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(
                    enabled = editModel.isNotBlank() && editTitle.isNotBlank() && !invalidDate,
                    onClick = {
                        val fields = GalleryFields(editModel.trim(), editTitle.trim(), editDate.trim())
                        if (onEdit(entry, fields)) pendingEdit = null
                        else saveError = "Не удалось сохранить изменения."
                    }
                ) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { pendingEdit = null }) { Text("Отмена") }
            }
        )
    }
}
