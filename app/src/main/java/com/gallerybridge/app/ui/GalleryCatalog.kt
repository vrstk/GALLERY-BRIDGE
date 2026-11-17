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
import com.gallerybridge.app.core.GalleryBatchSelection
import com.gallerybridge.app.core.GalleryCatalogFilter
import com.gallerybridge.app.core.GalleryCatalogProgress
import com.gallerybridge.app.transfer.QueueStore
import com.gallerybridge.app.core.GalleryFields
import com.gallerybridge.app.core.GalleryKeys

/**
 * One scroll container for controls AND entries. Bulk tools are collapsed by
 * default so catalog cards appear immediately instead of below a tall header.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GalleryCatalog(
    entries: List<CatalogGallery>,
    queue: QueueStore.Snapshot?,
    onQueue: () -> Unit,
    onBatchReview: (List<String>) -> Unit,
    onOpen: (CatalogGallery) -> Unit,
    onRemove: (CatalogGallery) -> Unit,
    onEdit: (CatalogGallery, GalleryFields) -> Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
    backupStatus: String,
    historyCount: Int
) {
    var query by rememberSaveable { mutableStateOf("") }
    var alphabetic by rememberSaveable { mutableStateOf(false) }
    var onlyTransferred by rememberSaveable { mutableStateOf(false) }
    var onlyQueued by rememberSaveable { mutableStateOf(false) }
    var onlyAttention by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showBatchTools by rememberSaveable { mutableStateOf(false) }
    var showBackupTools by rememberSaveable { mutableStateOf(false) }
    var batchSelection by rememberSaveable { mutableStateOf(listOf<String>()) }
    var showBatchConfirmation by remember { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<CatalogGallery?>(null) }
    var pendingEdit by remember { mutableStateOf<CatalogGallery?>(null) }
    val filtered = remember(entries, queue, query, alphabetic, onlyTransferred, onlyQueued, onlyAttention) {
        GalleryCatalogProgress.filter(
            GalleryCatalogFilter.apply(entries, query, alphabetic, onlyTransferred),
            queue, onlyQueued, onlyAttention
        )
    }
    val batchCandidates = remember(entries, queue) {
        entries.filter { GalleryBatchSelection.eligible(it, queue) }
    }
    val selectedBatch = remember(entries, queue, batchSelection) {
        GalleryBatchSelection.plan(entries, batchSelection, queue)
    }
    val attentionCount = remember(entries, queue) {
        entries.count { GalleryCatalogProgress.resolve(it, queue).needsAttention }
    }

    LazyColumn(
        modifier = Modifier.widthIn(max = GalleryContentWidth).fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "search") {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text("Поиск по каталогу") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                trailingIcon = if (query.isNotEmpty()) {
                    { TextButton(onClick = { query = "" }) { Text("Сброс") } }
                } else null
            )
        }
        item(key = "sorting") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !alphabetic, onClick = { alphabetic = false },
                    label = { Text("Новые") })
                FilterChip(selected = alphabetic, onClick = { alphabetic = true },
                    label = { Text("По модели А–Я") })
            }
        }
        item(key = "status") {
            Text(
                "Показано: ${filtered.size} из ${entries.size}" +
                    if (attentionCount > 0) " · Требуют внимания: $attentionCount" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item(key = "tools") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { showFilters = !showFilters }) {
                    Text(if (showFilters) "Скрыть фильтры" else "Фильтры")
                }
                if (entries.isNotEmpty()) {
                    TextButton(onClick = { showBatchTools = !showBatchTools }) {
                        Text("Пакетный просмотр")
                    }
                }
                TextButton(onClick = { showBackupTools = !showBackupTools }) {
                    Text("Резервная копия")
                }
            }
        }
        if (showFilters) item(key = "filters") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = onlyTransferred, onClick = { onlyTransferred = !onlyTransferred },
                    label = { Text("С передачами") })
                FilterChip(selected = onlyQueued, onClick = {
                    onlyQueued = !onlyQueued
                    if (onlyQueued) onlyAttention = false
                }, label = { Text("В очереди") })
                FilterChip(selected = onlyAttention, onClick = {
                    onlyAttention = !onlyAttention
                    if (onlyAttention) onlyQueued = false
                }, label = { Text("Требуют внимания") })
            }
        }
        if (showBackupTools || backupStatus.isNotBlank()) item(key = "backup") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (showBackupTools) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onExport, enabled = entries.isNotEmpty() || historyCount > 0) {
                            Text("Сохранить копию")
                        }
                        OutlinedButton(onClick = onImport) { Text("Восстановить") }
                    }
                    Text(
                        "Копия: каталог, до 40 записей истории и тема. " +
                            "Без паролей, очередей и фотографий. Храните JSON в защищённом месте.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (backupStatus.isNotBlank()) {
                    Text(backupStatus, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        if (showBatchTools && entries.isNotEmpty()) item(key = "batch") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("До ${GalleryBatchSelection.LIMIT} публикаций. Каждая открывается вручную; передачи не запускаются.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = batchCandidates.isNotEmpty(),
                        onClick = {
                            batchSelection = batchCandidates.take(GalleryBatchSelection.LIMIT)
                                .map { it.url }
                        }
                    ) { Text("Выбрать новые") }
                    TextButton(enabled = batchSelection.isNotEmpty(),
                        onClick = { batchSelection = emptyList() }) { Text("Снять выбор") }
                    Button(enabled = selectedBatch.isNotEmpty(),
                        onClick = { showBatchConfirmation = true }) {
                        Text("Просмотреть (${selectedBatch.size})")
                    }
                }
            }
        }
        if (entries.isEmpty()) {
            item(key = "empty") {
                Text("Каталог пока пуст. Найдите фотогалерею по HTTPS-ссылке и сохраните её данные.",
                    style = MaterialTheme.typography.bodyMedium)
            }
        } else if (filtered.isEmpty()) {
            item(key = "none") {
                Column {
                    Text("По текущим фильтрам ничего не найдено.")
                    TextButton(onClick = {
                        query = ""; alphabetic = false; onlyTransferred = false
                        onlyQueued = false; onlyAttention = false
                    }) { Text("Сбросить фильтры") }
                }
            }
        } else {
            items(filtered, key = { it.url }) { entry ->
                val progress = GalleryCatalogProgress.resolve(entry, queue)
                OutlinedCard(
                    onClick = { onOpen(entry) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            if (showBatchTools && GalleryBatchSelection.eligible(entry, queue)) {
                                Checkbox(
                                    checked = entry.url in batchSelection,
                                    onCheckedChange = { checked ->
                                        batchSelection = if (checked) {
                                            (batchSelection + entry.url).distinct()
                                                .take(GalleryBatchSelection.LIMIT)
                                        } else batchSelection - entry.url
                                    },
                                    enabled = entry.url in batchSelection ||
                                        batchSelection.size < GalleryBatchSelection.LIMIT
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(entry.model.ifBlank { "Без имени" },
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(entry.title.ifBlank { "Без названия" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Text(progress.title + if (entry.date.isNotBlank()) " · ${entry.date}" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (progress.needsAttention) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.secondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { onOpen(entry) }) { Text("Открыть") }
                            TextButton(onClick = { pendingEdit = entry }) { Text("Изменить") }
                            TextButton(onClick = { pendingRemoval = entry }) { Text("Убрать") }
                            if (progress.linkedQueue) {
                                TextButton(onClick = onQueue) { Text("Очередь") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showBatchConfirmation) {
        AlertDialog(
            onDismissRequest = { showBatchConfirmation = false },
            title = { Text("Начать просмотр публикаций?") },
            text = {
                Text(
                    "Будут открыты ${selectedBatch.size} сохранённых публикаций по одной. " +
                        "Это не скачивает фотографии, не изменяет очередь и не запускает передачи. " +
                        "После каждого просмотра переходите к следующей вручную."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showBatchConfirmation = false
                    if (selectedBatch.isNotEmpty()) onBatchReview(selectedBatch)
                }) { Text("Начать просмотр") }
            },
            dismissButton = {
                TextButton(onClick = { showBatchConfirmation = false }) { Text("Отмена") }
            }
        )
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
