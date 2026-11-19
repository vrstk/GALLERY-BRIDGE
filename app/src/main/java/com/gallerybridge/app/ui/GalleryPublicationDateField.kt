package com.gallerybridge.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.gallerybridge.app.core.GalleryKeys

/**
 * Editing-only YYYY-MM-DD mask. Persisted data stays in the existing ISO format.
 * Empty date is permitted; calendar validation remains in GalleryKeys.validDate.
 */
internal object PublicationDateInput {
    private fun isAsciiDigit(char: Char): Boolean = char in '0'..'9'

    fun format(source: String): String {
        val digits = source.filter(::isAsciiDigit).take(8)
        return buildString {
            append(digits.take(4))
            if (digits.length > 4) {
                append('-')
                append(digits.substring(4, minOf(6, digits.length)))
            }
            if (digits.length > 6) {
                append('-')
                append(digits.substring(6))
            }
        }
    }

    fun edit(previous: TextFieldValue, incoming: TextFieldValue): TextFieldValue {
        val previousDigits = previous.text.filter(::isAsciiDigit).take(8)
        var digits = incoming.text.filter(::isAsciiDigit).take(8)
        var digitsBeforeCursor = incoming.text
            .take(incoming.selection.start.coerceIn(0, incoming.text.length))
            .count(::isAsciiDigit).coerceAtMost(digits.length)

        // Backspace immediately after a separator should delete a digit,
        // not restore the same separator forever.
        if (previous.text.length > incoming.text.length &&
            previousDigits == digits &&
            previous.selection.collapsed &&
            incoming.selection.collapsed &&
            digitsBeforeCursor > 0
        ) {
            digits = digits.removeRange(digitsBeforeCursor - 1, digitsBeforeCursor)
            digitsBeforeCursor--
        }

        val formatted = format(digits)
        val cursor = if (digitsBeforeCursor >= digits.length) {
            formatted.length
        } else if (digitsBeforeCursor == 0) {
            0
        } else {
            var seen = 0
            formatted.indexOfFirst {
                if (isAsciiDigit(it)) seen++
                seen == digitsBeforeCursor
            }.let { if (it < 0) formatted.length else it + 1 }
        }
        return TextFieldValue(formatted, selection = TextRange(cursor))
    }
}

/** Shared by the Downloads metadata form and catalog edit dialog. */
@Composable
internal fun GalleryPublicationDateField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    label: String = "Дата ГГГГ-ММ-ДД",
    modifier: Modifier = Modifier
) {
    var field by remember {
        mutableStateOf(TextFieldValue(value, selection = TextRange(value.length)))
    }
    // Synchronize programmatic updates (saved metadata, confirmed source dates)
    // without resetting the caret after each user keystroke.
    LaunchedEffect(value) {
        if (field.text != value) {
            field = TextFieldValue(value, selection = TextRange(value.length))
        }
    }

    val partial = value.isNotBlank() && value.length < 10
    val invalid = value.length == 10 && !GalleryKeys.validDate(value)
    OutlinedTextField(
        value = field,
        onValueChange = { incoming ->
            val edited = PublicationDateInput.edit(field, incoming)
            field = edited
            if (edited.text != value) onValueChange(edited.text)
        },
        modifier = modifier.fillMaxWidth().testTag("publication-date-field"),
        label = { Text(label) },
        placeholder = { Text("ГГГГ-ММ-ДД") },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        isError = invalid,
        supportingText = {
            Text(
                when {
                    invalid -> "Укажите реальную календарную дату"
                    partial -> "Введите все 8 цифр даты"
                    else -> "Необязательно · формат ГГГГ-ММ-ДД"
                },
                color = if (invalid) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}
