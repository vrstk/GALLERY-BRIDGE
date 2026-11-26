package com.gallerybridge.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import com.gallerybridge.app.core.Kind
import com.gallerybridge.app.core.Media
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gallerybridge.app.R
import com.gallerybridge.app.core.CatalogGallery
import com.gallerybridge.app.core.GalleryBatchSelection
import com.gallerybridge.app.core.GalleryCatalogFilter
import com.gallerybridge.app.core.GalleryCatalogProgress
import com.gallerybridge.app.core.GalleryCatalogModels
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
    historyCount: Int,
    selectedModel: MutableState<String?> = rememberSaveable { mutableStateOf(null) }
) {
    var catalogQuery by rememberSaveable { mutableStateOf("") }
    var modelQuery by rememberSaveable { mutableStateOf("") }
    var alphabetic by rememberSaveable { mutableStateOf(false) }
    var browseModels by rememberSaveable { mutableStateOf(false) }
    var chosenModel by selectedModel
    val publicationsScroll = rememberLazyListState()
    val modelsScroll = rememberLazyListState()
    val modelPublicationsScroll = rememberLazyListState()
    // Exactly one status filter is active; sorting and filtering are separate.
    var statusFilter by rememberSaveable { mutableStateOf(GalleryCatalogProgress.Filter.ALL.name) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showBatchTools by rememberSaveable { mutableStateOf(false) }
    var showBackupTools by rememberSaveable { mutableStateOf(false) }
    var batchSelection by rememberSaveable { mutableStateOf(listOf<String>()) }
    var showBatchConfirmation by remember { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<CatalogGallery?>(null) }
    var pendingEdit by remember { mutableStateOf<CatalogGallery?>(null) }
    // Batch queues may hold thousands of files. Build the per-publication
    // index only when the queue changes to avoid quadratic Compose filtering.
    val queueIndex = remember(queue) { GalleryCatalogProgress.index(queue) }
    val statuses = remember(entries, queueIndex) {
        entries.associate { it.url to GalleryCatalogProgress.resolve(it, queueIndex) }
    }
    val activeStatusFilter = GalleryCatalogProgress.Filter.entries.firstOrNull {
        it.name == statusFilter
    } ?: GalleryCatalogProgress.Filter.ALL
    val activeGroup = remember(entries, chosenModel) {
        chosenModel?.let { key ->
            GalleryCatalogModels.group(entries.filter { GalleryCatalogModels.key(it.model) == key })
                .firstOrNull()
        }
    }
    LaunchedEffect(chosenModel, activeGroup) {
        if (chosenModel != null && activeGroup == null) chosenModel = null
    }
    val inModel = activeGroup != null
    val showModelIndex = browseModels && !inModel
    val scopeEntries = activeGroup?.entries ?: entries
    val query = if (inModel) modelQuery else catalogQuery
    val updateQuery: (String) -> Unit = { if (inModel) modelQuery = it else catalogQuery = it }
    val filtered = remember(scopeEntries, statuses, query, alphabetic, activeStatusFilter) {
        GalleryCatalogFilter.apply(scopeEntries, query, alphabetic, false).filter { entry ->
            statuses[entry.url]?.let {
                GalleryCatalogProgress.matches(entry, it, activeStatusFilter)
            } == true
        }
    }
    val modelGroups = remember(filtered, showModelIndex) {
        if (showModelIndex) GalleryCatalogModels.group(filtered) else emptyList()
    }
    val eligibleBatchUrls = remember(entries, queueIndex) {
        GalleryBatchSelection.eligibleUrls(entries, queueIndex)
    }
    val selectedBatch = remember(eligibleBatchUrls, batchSelection) {
        GalleryBatchSelection.plan(eligibleBatchUrls, batchSelection)
    }
    val filteredUrls = remember(filtered) {
        filtered.mapNotNull { GalleryKeys.canonical(it.url) }.distinct()
    }
    val batchAdditions = remember(filteredUrls, eligibleBatchUrls, selectedBatch) {
        filteredUrls.asSequence()
            .filter { it in eligibleBatchUrls && it !in selectedBatch }
            .take(GalleryBatchSelection.LIMIT - selectedBatch.size)
            .toList()
    }
    val hiddenBatchCount = selectedBatch.count { it !in filteredUrls }
    val attentionCount = remember(scopeEntries, statuses) {
        scopeEntries.count { statuses[it.url]?.needsAttention == true }
    }
    val errorCount = remember(scopeEntries, statuses) {
        scopeEntries.count { statuses[it.url]?.hasErrors == true }
    }

    LazyColumn(
        modifier = Modifier.widthIn(max = GalleryContentWidth).fillMaxSize(),
        state = when {
            inModel -> modelPublicationsScroll
            showModelIndex -> modelsScroll
            else -> publicationsScroll
        },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        item(key = "search") {
            OutlinedTextField(
                value = query, onValueChange = updateQuery,
                label = { Text(if (inModel) "Поиск у модели" else "Поиск по каталогу") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                shape = MaterialTheme.shapes.medium,
                singleLine = true, modifier = Modifier.fillMaxWidth().testTag("catalog-search"),
                trailingIcon = if (query.isNotEmpty()) {
                    { TextButton(onClick = { updateQuery("") }) { Text("Сброс") } }
                } else null
            )
        }
        item(key = "sorting") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !alphabetic, onClick = { alphabetic = false },
                    label = { Text("Недавно добавлены") })
                FilterChip(selected = alphabetic, onClick = { alphabetic = true },
                    label = { Text("По модели А–Я") })
                FilterChip(
                    selected = browseModels,
                    onClick = {
                        browseModels = !browseModels
                        chosenModel = null
                    },
                    label = { Text("По моделям") },
                    modifier = Modifier.testTag("catalog-model-mode")
                )
            }
        }
        item(key = "status") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    activeGroup?.label ?: if (showModelIndex) "Модели" else "Публикации",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    (if (showModelIndex)
                        "Моделей: ${modelGroups.size} · Публикаций: ${filtered.size}"
                    else "Показано: ${filtered.size} из ${scopeEntries.size}") +
                        (if (attentionCount > 0) " · Требуют внимания: $attentionCount" else "") +
                        (if (errorCount > 0) " · Ошибки: $errorCount" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (inModel) item(key = "model-back") {
            TextButton(
                modifier = Modifier.testTag("catalog-model-back"),
                onClick = { chosenModel = null }
            ) { Text("Все модели") }
        }
        item(key = "tools") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                TextButton(onClick = { showFilters = !showFilters }) {
                    Text(if (showFilters) "Скрыть фильтры" else "Фильтры")
                }
                if (entries.isNotEmpty()) {
                    TextButton(onClick = { showBatchTools = !showBatchTools }) {
                        Text("Пакетный просмотр")
                    }
                }
                TextButton(onClick = { showBackupTools = !showBackupTools }) {
                    Text("Копия")
                }
            }
        }
        if (showFilters) item(key = "filters") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        GalleryCatalogProgress.Filter.ALL to "Все",
                        GalleryCatalogProgress.Filter.NEW to "Без передач",
                        GalleryCatalogProgress.Filter.TRANSFERRED to "Передавались",
                        GalleryCatalogProgress.Filter.QUEUED to "В очереди",
                        GalleryCatalogProgress.Filter.REVIEW to "Требуют внимания",
                        GalleryCatalogProgress.Filter.ERRORS to "Ошибки"
                    ).forEach { (filter, label) ->
                        FilterChip(
                            selected = activeStatusFilter == filter,
                            onClick = { statusFilter = filter.name },
                            label = { Text(label) },
                            modifier = Modifier.testTag(
                                "catalog-status-filter-${filter.name.lowercase()}")
                        )
                    }
                }
                Text(
                    "Статусы основаны на локальной очереди и истории. " +
                        "«Передавались» не означает завершённую синхронизацию облака.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                Text("Выбрано: ${selectedBatch.size} из ${GalleryBatchSelection.LIMIT}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (hiddenBatchCount > 0) {
                    Text("Вне текущего списка: $hiddenBatchCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = batchAdditions.isNotEmpty(),
                        onClick = { batchSelection = selectedBatch + batchAdditions }
                    ) { Text("Добавить из списка") }
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
                        updateQuery("")
                        alphabetic = false
                        statusFilter = GalleryCatalogProgress.Filter.ALL.name
                    }) { Text("Сбросить фильтры") }
                }
            }
        } else if (showModelIndex) {
            items(modelGroups, key = { "model:" + it.key }) { group ->
                OutlinedCard(
                    onClick = {
                        modelQuery = ""
                        modelPublicationsScroll.requestScrollToItem(0)
                        chosenModel = group.key
                    },
                    modifier = Modifier.fillMaxWidth().testTag("catalog-model-row"),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            group.label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${group.count} галерей",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
        } else {
            items(filtered, key = { it.url }) { entry ->
                val progress = statuses[entry.url]
                    ?: GalleryCatalogProgress.resolve(entry, queueIndex)
                var menuExpanded by remember(entry.url) { mutableStateOf(false) }
                OutlinedCard(
                    onClick = { onOpen(entry) },
                    modifier = Modifier.fillMaxWidth().testTag("catalog-publication-card"),
                    shape = MaterialTheme.shapes.medium,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.outlineVariant
                    ),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    val colors = MaterialTheme.colorScheme
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val batchUrl = remember(entry.url) { GalleryKeys.canonical(entry.url) }
                            if (showBatchTools && batchUrl != null && batchUrl in eligibleBatchUrls) {
                                val checked = batchUrl in selectedBatch
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { select ->
                                        batchSelection = if (select) selectedBatch + batchUrl
                                            else selectedBatch - batchUrl
                                    },
                                    enabled = checked || selectedBatch.size < GalleryBatchSelection.LIMIT,
                                    modifier = Modifier.semantics {
                                        contentDescription = "Выбрать для просмотра: ${entry.title}"
                                    }
                                )
                            }
                            // Only the explicit catalog cover; never pass it to the queue.
                            if (entry.source == "elitebabes" &&
                                entry.previewUrl.startsWith("https://cdn.elitebabes.com/")) {
                                CachedGalleryImage(
                                    media = Media(entry.previewUrl,
                                        entry.title.ifBlank { "Обложка" },
                                        Kind.PHOTO, "Обложка каталога"),
                                    modifier = Modifier.size(width = 52.dp, height = 60.dp),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier.size(width = 52.dp, height = 60.dp)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(colors.primaryContainer,
                                                    colors.surfaceContainerHigh)
                                            ), MaterialTheme.shapes.small
                                        ).testTag("catalog-publication-preview"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(painter = painterResource(R.drawable.ic_gallery_bridge),
                                        contentDescription = null,
                                        modifier = Modifier.size(30.dp))
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    entry.model.ifBlank { "Без имени" },
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    entry.title.ifBlank { "Без названия" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (entry.type == "video") {
                                    Text("Видео · только каталог", style = MaterialTheme.typography.labelSmall)
                                }
                                if (entry.studio.isNotBlank()) {
                                    Text(entry.studio, style = MaterialTheme.typography.labelSmall)
                                }
                                if (entry.date.isNotBlank()) {
                                    Text(
                                        (if (entry.displayedDate.isNotBlank()) "Дата на карточке: " else "") + entry.date,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = if (progress.needsAttention) colors.errorContainer
                                    else colors.surfaceContainerHigh
                            ) {
                                Text(
                                    progress.title,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (progress.needsAttention) colors.onErrorContainer
                                        else colors.onSurface
                                )
                            }
                            if (progress.kind != GalleryCatalogProgress.Kind.SAVED) {
                                Text(
                                    progress.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                    color = colors.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { onOpen(entry) }) { Text("Открыть") }
                            if (progress.linkedQueue) {
                                TextButton(onClick = onQueue) { Text("Очередь") }
                            }
                            Spacer(Modifier.weight(1f))
                            Box {
                                IconButton(onClick = { menuExpanded = true },
                                    modifier = Modifier.size(40.dp)) {
                                    Icon(Icons.Filled.MoreVert,
                                        contentDescription = "Действия с публикацией")
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Изменить") },
                                        onClick = {
                                            menuExpanded = false
                                            pendingEdit = entry
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Убрать") },
                                        onClick = {
                                            menuExpanded = false
                                            pendingRemoval = entry
                                        }
                                    )
                                }
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
                val byUrl = remember(entries) {
                    entries.associateBy { GalleryKeys.canonical(it.url) }
                }
                Column(
                    Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Публикации откроются по одной. Просмотр не запускает передачу файлов.")
                    selectedBatch.forEach { url ->
                        byUrl[url]?.let { entry ->
                            Column {
                                Text(entry.title.ifBlank { "Без названия" },
                                    style = MaterialTheme.typography.bodyMedium)
                                Text(entry.model.ifBlank { "Без имени" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(enabled = selectedBatch.isNotEmpty(), onClick = {
                    showBatchConfirmation = false
                    onBatchReview(selectedBatch)
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
                    GalleryPublicationDateField(
                        value = editDate,
                        onValueChange = { editDate = it; saveError = "" },
                        label = "Дата (ГГГГ-ММ-ДД)"
                    )
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
