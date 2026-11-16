package com.gallerybridge.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** No transfer can begin before a person explicitly confirms a possible repeat. */
@Composable
internal fun GalleryRepeatReviewDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Проверка повторной передачи") },
        text = {
            Text(
                "Эта публикация уже встречалась в истории передач или сохранённой очереди. " +
                    "Часть фотографий может уже находиться в выбранном хранилище. " +
                    "Подтверждение не доказывает синхронизацию облака и не удаляет старые файлы. " +
                    "Проверьте назначение и продолжайте только если действительно нужен повтор."
            )
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Подтвердить повтор") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Вернуться") } }
    )
}
