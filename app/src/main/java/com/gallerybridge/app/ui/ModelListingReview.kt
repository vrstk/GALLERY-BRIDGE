package com.gallerybridge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.gallerybridge.app.core.CatalogGallery
import com.gallerybridge.app.core.GalleryKeys
import com.gallerybridge.app.core.ModelListingSources
import kotlinx.coroutines.launch

/** Model previews never enter the download queue. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ModelListingReview(
    result: ModelListingSources.Result,
    existing: List<CatalogGallery>,
    selected: Set<String>,
    message: String,
    busy: Boolean,
    scanning: Boolean,
    destination: String = "Папка Android",
    onDestination: (String) -> Unit = {},
    onSelect: (String, Boolean) -> Unit,
    onSelectPhotos: (Set<String>) -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    onQueueSelected: () -> Unit,
    onStop: () -> Unit,
    onContinue: () -> Unit,
    onOpen: (ModelListingSources.Publication) -> Unit,
    onBack: () -> Unit
) {
    val photos = result.photos
    val videoCards = result.videoCards
    val saved = remember(existing) { existing.mapTo(HashSet()) { GalleryKeys.canonical(it.url) } }
    var search by rememberSaveable { mutableStateOf("") }
    val query = search.trim()
    val cards = remember(result) { result.allCards }
    val visible = remember(cards, query) {
        if (query.isEmpty()) cards else cards.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.studio.contains(query, ignoreCase = true) ||
                it.displayedDate.contains(query, ignoreCase = true)
        }
    }
    val visiblePhotoUrls = remember(visible) {
        visible.filter { it.type == "photo" }.mapTo(linkedSetOf()) { it.url }
    }
    val hiddenSelected = selected.size - visible.count { it.url in selected }
    val chosenPhotos = photos.count { it.url in selected }
    val chosenVideos = videoCards.count { it.url in selected }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // The status/diagnostics and publication cards share ONE scroll container.
    // Keeping diagnostics above a weighted inner list made that list shrink to
    // a few pixels (or disappear) on phones with long API error messages.
    LazyColumn(
        modifier = Modifier.widthIn(max = GalleryContentWidth).fillMaxSize()
            .testTag("model-listing-list"),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item(key = "model-listing-header") {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("Галереи модели", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Собрано: ${cards.size} публикаций · Фото: ${photos.size} · Видео: " +
                        "${maxOf(videoCards.size, result.videos)} · Порций: ${result.loadedPages}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (cards.isNotEmpty()) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = { Text("Поиск по названию, студии или дате") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("model-listing-search"),
                        trailingIcon = if (search.isNotEmpty()) {
                            { TextButton(onClick = { search = "" }) { Text("Сброс") } }
                        } else null
                    )
                    if (query.isNotEmpty()) {
                        Text("Найдено: ${visible.size} из ${cards.size}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (visible.isNotEmpty()) {
                    TextButton(onClick = {
                        coroutineScope.launch { listState.animateScrollToItem(1) }
                    }) {
                        Text("К карточкам (${visible.size})")
                    }
                }
                result.advertisedCount?.let {
                    Text("На сайте указано: $it.", style = MaterialTheme.typography.bodySmall)
                }
                if (!scanning && result.incomplete) {
                    Text(
                        if (result.advertisedCount == cards.size)
                            "Собраны все ${cards.size} заявленных карточек, " +
                                "но сервер не подтвердил конец выдачи. " +
                                "Каталог пока считается неполным."
                        else
                            "Каталог НЕПОЛНЫЙ: собрано ${cards.size} карточек. " +
                                "Ошибка или повтор порции не означает конец выдачи.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (result.errors.isNotEmpty()) {
                    Text(result.errors.joinToString(" ").take(600),
                        color = MaterialTheme.colorScheme.error)
                }
                if (message.isNotBlank()) {
                    Text(message, style = MaterialTheme.typography.bodySmall)
                }
                if (scanning) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    TextButton(onClick = onStop) { Text("Остановить сбор") }
                } else {
                    TextButton(onClick = onContinue, enabled = !busy) {
                        Text(if (result.incomplete) "Продолжить сбор" else "Обновить каталог")
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = destination == "Папка Android",
                        onClick = { onDestination("Папка Android") },
                        enabled = !busy, label = { Text("Папка Android / Drive") }
                    )
                    FilterChip(
                        selected = destination == "Mail WebDAV",
                        onClick = { onDestination("Mail WebDAV") },
                        enabled = !busy, label = { Text("Облако Mail") }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    TextButton(
                        onClick = { onSelectPhotos(visiblePhotoUrls) },
                        enabled = !busy && visiblePhotoUrls.any { it !in selected }
                    ) {
                        Text(if (query.isEmpty()) "Выбрать все фото" else "Выбрать найденные фото")
                    }
                    TextButton(onClick = onClear, enabled = selected.isNotEmpty() && !busy) {
                        Text("Снять выбор")
                    }
                }
                Text(
                    "Выбрано: фотогалерей $chosenPhotos, видео " +
                        "$chosenVideos. " +
                        (if (query.isNotEmpty() && hiddenSelected > 0)
                            "Вне поиска: $hiddenSelected. " else "") +
                        "Даты показаны на карточках сайта, " +
                        "это не подтверждённые даты публикации.",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = onQueueSelected, enabled = chosenPhotos > 0 && !busy) {
                        Text("Проверить ($chosenPhotos)")
                    }
                    OutlinedButton(onClick = onSave, enabled = selected.isNotEmpty() && !busy) {
                        Text("В каталог")
                    }
                    TextButton(onClick = onBack, enabled = !busy) { Text("Назад") }
                }
                HorizontalDivider()
            }
        }
        if (cards.isNotEmpty() && visible.isEmpty()) {
            item(key = "model-listing-empty") {
                Text("По этому запросу ничего не найдено.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
        itemsIndexed(visible, key = { _, publication -> publication.url }) { index, publication ->
            val video = publication.type == "video"
            val already = GalleryKeys.canonical(publication.url) in saved
            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("model-listing-card-$index")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Checkbox(
                        checked = publication.url in selected,
                        enabled = !busy,
                        onCheckedChange = { onSelect(publication.url, it) },
                        modifier = Modifier.testTag("model-listing-select-$index")
                    )
                    if (publication.previewUrl.isNotBlank()) {
                        AsyncImage(
                            model = publication.previewUrl,
                            contentDescription = "Обложка публикации",
                            modifier = Modifier.size(width = 76.dp, height = 90.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(publication.title.ifBlank { "Без названия" },
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(if (video) "Видео · только каталог" else "Фотогалерея",
                            style = MaterialTheme.typography.labelSmall)
                        if (publication.studio.isNotBlank()) {
                            Text(publication.studio, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            publication.displayedDate.ifBlank { "Дата не указана" } +
                                if (already) " · В каталоге" else "",
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = { onOpen(publication) }, enabled = !busy) {
                            Text("Открыть")
                        }
                    }
                }
            }
        }
    }
}
