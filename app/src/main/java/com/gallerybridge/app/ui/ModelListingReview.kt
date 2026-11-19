package com.gallerybridge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gallerybridge.app.core.CatalogGallery
import com.gallerybridge.app.core.GalleryKeys
import com.gallerybridge.app.core.ModelListingSources

/**
 * Only publication metadata, never thumbnails or an automatic transfer queue.
 * A user opens each publication explicitly before scanning its actual photos.
 */
@Composable
internal fun ModelListingReview(
    result: ModelListingSources.Result,
    existing: List<CatalogGallery>,
    selected: Set<String>,
    message: String,
    onSelect: (String, Boolean) -> Unit,
    onSave: () -> Unit,
    onOpen: (ModelListingSources.Publication) -> Unit,
    onBack: () -> Unit
) {
    val saved = existing.mapTo(HashSet()) { GalleryKeys.canonical(it.url) }
    val available = result.allCards.count { GalleryKeys.canonical(it.url) !in saved }
    Column(
        modifier = Modifier.widthIn(max = GalleryContentWidth).fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Публикации модели", style = MaterialTheme.typography.titleLarge)
        Text(
            "Обнаружено фотопубликаций: ${result.photos.size}; видеокарточек: ${result.videos}. " +
                "Обработано карточек: ${result.cards} на ${result.loadedPages} страницах.",
            style = MaterialTheme.typography.bodyMedium
        )
        if (result.advertisedCount != null) {
            Text("На сайте указано публикаций: ${result.advertisedCount}.",
                style = MaterialTheme.typography.bodySmall)
        }
        if (result.incomplete) {
            Text(
                "Список не подтверждён полностью: проверьте ошибки API или " +
                    "импортируйте JSON из Soul. Повтор порции не означает конец списка.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (result.errors.isNotEmpty()) {
            Text("Не удалось прочитать некоторые страницы: ${result.errors.joinToString(" ")}",
                color = MaterialTheme.colorScheme.error)
        }
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
        Text(
            "Ранее сохранено: ${result.allCards.size - available}. " +
                "Доступно новых: $available. Выбор — максимум 100 за раз. " +
                "Сохранение не запускает передачу и не изменяет очередь. " +
                "Дата показана сайтом, а не проверена на странице публикации.",
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onSave, enabled = selected.isNotEmpty()) {
                Text("Сохранить выбранные (${selected.size})")
            }
            TextButton(onClick = onBack) { Text("Назад") }
        }
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(result.allCards, key = { it.url }) { publication ->
                val already = GalleryKeys.canonical(publication.url) in saved
                OutlinedCard {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = publication.url in selected,
                            enabled = !already && (publication.url in selected || selected.size < 100),
                            onCheckedChange = { onSelect(publication.url, it) }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(publication.title.ifBlank { "Без названия" }, maxLines = 2,
                                overflow = TextOverflow.Ellipsis)
                            Text((if (publication.type == "video") "Видео · " else "Фото · ") +
                                publication.displayedDate.ifBlank { "Дата не указана" } +
                                if (already) " · Уже в каталоге" else "",
                                style = MaterialTheme.typography.bodySmall)
                            Text(publication.url, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { onOpen(publication) }) {
                            Text(if (publication.type == "video") "Страница" else "Открыть")
                        }
                    }
                }
            }
        }
    }
}
