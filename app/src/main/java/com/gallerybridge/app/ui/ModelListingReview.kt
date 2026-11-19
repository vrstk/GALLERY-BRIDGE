package com.gallerybridge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
    onSelectAllPhotos: () -> Unit,
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
    val saved = existing.mapTo(HashSet()) { GalleryKeys.canonical(it.url) }
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
                    "Собрано: ${result.allCards.size} публикаций · Фото: ${photos.size} · Видео: " +
                        "${maxOf(videoCards.size, result.videos)} · Порций: ${result.loadedPages}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (result.allCards.isNotEmpty()) {
                    TextButton(onClick = {
                        coroutineScope.launch { listState.animateScrollToItem(1) }
                    }) {
                        Text("К карточкам (${result.allCards.size})")
                    }
                }
                result.advertisedCount?.let {
                    Text("На сайте указано: $it.", style = MaterialTheme.typography.bodySmall)
                }
                if (!scanning && result.incomplete) {
                    Text(
                        if (result.advertisedCount == result.allCards.size)
                            "Собраны все ${result.allCards.size} заявленных карточек, " +
                                "но сервер не подтвердил конец выдачи. " +
                                "Каталог пока считается неполным."
                        else
                            "Каталог НЕПОЛНЫЙ: собрано ${result.allCards.size} карточек. " +
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
                    TextButton(onClick = onSelectAllPhotos, enabled = !busy && photos.isNotEmpty()) {
                        Text("Выбрать все фото")
                    }
                    TextButton(onClick = onClear, enabled = selected.isNotEmpty() && !busy) {
                        Text("Снять выбор")
                    }
                }
                Text(
                    "Выбрано: фотогалерей $chosenPhotos, видео " +
                        "$chosenVideos. " +
                        "Даты показаны на карточках сайта, " +
                        "это не подтверждённые даты публикации.",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = onQueueSelected, enabled = chosenPhotos > 0 && !busy) {
                        Text("В очередь ($chosenPhotos)")
                    }
                    OutlinedButton(onClick = onSave, enabled = selected.isNotEmpty() && !busy) {
                        Text("В каталог")
                    }
                    TextButton(onClick = onBack, enabled = !busy) { Text("Назад") }
                }
                HorizontalDivider()
            }
        }
        itemsIndexed(result.allCards, key = { _, publication -> publication.url }) { index, publication ->
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
