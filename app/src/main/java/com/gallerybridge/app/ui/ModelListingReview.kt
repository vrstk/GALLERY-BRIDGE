package com.gallerybridge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gallerybridge.app.core.CatalogGallery
import com.gallerybridge.app.core.GalleryKeys
import com.gallerybridge.app.core.ModelListingSources
import com.gallerybridge.app.core.Media
import com.gallerybridge.app.core.Kind

/** Gallery covers are previews only, never candidate originals for transfer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ModelListingReview(
    result: ModelListingSources.Result,
    existing: List<CatalogGallery>,
    selected: Set<String>,
    message: String,
    busy: Boolean,
    destination: String,
    onDestination: (String) -> Unit,
    onSelect: (String, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    onQueue: () -> Unit,
    onStop: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (ModelListingSources.Publication) -> Unit,
    onBack: () -> Unit
) {
    val photos = result.photos
    val videos = result.videoCards
    Column(
        modifier = Modifier.widthIn(max = GalleryContentWidth).fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Каталог модели", style = MaterialTheme.typography.titleLarge)
        Text("Собрано ${result.allCards.size} публикаций: фото ${photos.size}, видео ${videos.size}. " +
            "Обработано порций: ${result.loadedPages}.",
            style = MaterialTheme.typography.bodyMedium)
        result.advertisedCount?.let {
            Text("На сайте указано: $it публикаций.", style = MaterialTheme.typography.bodySmall)
        }
        if (result.incomplete) {
            Text("НЕПОЛНЫЙ КАТАЛОГ: получено ${result.allCards.size} карточек. " +
                "Не все порции подтверждены; повтор ответа и ошибки сети не считаются концом выдачи.",
                color = MaterialTheme.colorScheme.error)
        }
        if (result.errors.isNotEmpty()) {
            Text(result.errors.joinToString(" ").take(650), color = MaterialTheme.colorScheme.error)
        }
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onRefresh, enabled = !busy) { Text("Обновить каталог") }
            if (busy) TextButton(onClick = onStop) { Text("Остановить") }
        }
        Text("Выбрано фотогалерей: ${selected.size}. Сначала будут проверены реальные " +
            "фотографии каждой галереи. Видео в очередь фотографий не включаются.",
            style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = onSelectAll, enabled = !busy && photos.isNotEmpty()) {
                Text("Выбрать все фото")
            }
            TextButton(onClick = onClear, enabled = !busy && selected.isNotEmpty()) {
                Text("Снять выбор")
            }
            TextButton(onClick = onSave, enabled = !busy && selected.isNotEmpty()) {
                Text("Сохранить в каталог")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(destination == "Папка Android",
                onClick = { onDestination("Папка Android") }, label = { Text("Папка Android / Drive") })
            FilterChip(destination == "Mail WebDAV",
                onClick = { onDestination("Mail WebDAV") }, label = { Text("Облако Mail") })
        }
        Button(
            onClick = onQueue, enabled = !busy && selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Собрать фотографии и добавить в очередь (${selected.size})") }
        Text("Дата — отображаемая на карточке сайта, не подтверждённая дата публикации. " +
            "Для каждой фотогалереи создаётся отдельная папка.",
            style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(result.allCards, key = { it.url }) { publication ->
                val saved = existing.any { GalleryKeys.same(it.url, publication.url) }
                OutlinedCard {
                    Row(Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (publication.type == "photo") {
                            Checkbox(
                                checked = publication.url in selected,
                                enabled = !busy,
                                onCheckedChange = { onSelect(publication.url, it) }
                            )
                        }
                        if (publication.previewUrl.isNotBlank()) {
                            CachedGalleryImage(
                                media = Media(publication.previewUrl, publication.title.ifBlank { "Обложка" },
                                    Kind.PHOTO, "Обложка каталога"),
                                modifier = Modifier.size(width = 72.dp, height = 84.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(publication.title.ifBlank { "Без названия" }, maxLines = 2,
                                overflow = TextOverflow.Ellipsis)
                            Text((if (publication.type == "video") "Видео" else "Фото") +
                                " · " + publication.displayedDate.ifBlank { "Дата неизвестна" } +
                                (if (saved) " · Сохранено" else ""),
                                style = MaterialTheme.typography.bodySmall)
                            if (publication.studio.isNotBlank())
                                Text(publication.studio, style = MaterialTheme.typography.labelSmall)
                            TextButton(onClick = { onOpen(publication) }) {
                                Text("Открыть публикацию")
                            }
                        }
                    }
                }
            }
        }
        TextButton(onClick = onBack) { Text("Назад") }
    }
}
