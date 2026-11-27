package com.gallerybridge.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gallerybridge.app.core.ModelBatchQueuePlan

/** A read-only, non-persistent preflight report. No service is started before confirmation. */
@Composable
internal fun ModelBatchPreflightDialog(
    preview: ModelBatchQueuePlan.Preview,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val plan = preview.preparation
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Проверка очереди") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Выбрано: ${preview.selectedUrls.size} фотогалерей")
                Text("Готово: ${plan.galleries.size} галерей · ${plan.photoCount} фотографий",
                    color = MaterialTheme.colorScheme.primary)
                Text("Проверить перед повтором: ${plan.needsReview.size}")
                Text("Недоступно: ${plan.skipped.size}")
                Text("Назначение: ${preview.target}")
                if (plan.needsReview.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Нужна отдельная проверка", style = MaterialTheme.typography.titleSmall)
                    plan.needsReview.values.take(5).forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    if (plan.needsReview.size > 5) Text(
                        "И ещё ${plan.needsReview.size - 5} публикаций.",
                        style = MaterialTheme.typography.bodySmall)
                }
                if (plan.skipped.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Не включены в очередь", style = MaterialTheme.typography.titleSmall)
                    plan.skipped.values.take(5).forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    if (plan.skipped.size > 5) Text(
                        "И ещё ${plan.skipped.size - 5} публикаций.",
                        style = MaterialTheme.typography.bodySmall)
                }
                Text("Это проверка ссылок, а не подтверждение синхронизации с облаком. " +
                    "Отмена не затрагивает файлы или сохранённую очередь.",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm, enabled = plan.galleries.isNotEmpty(),
                modifier = Modifier.testTag("confirm-model-batch")
            ) { Text("Начать передачу") }
        },
        dismissButton = {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("cancel-model-batch")) {
                Text("Отмена")
            }
        }
    )
}
