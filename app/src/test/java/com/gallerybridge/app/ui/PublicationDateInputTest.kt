package com.gallerybridge.app.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.gallerybridge.app.core.GalleryKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicationDateInputTest {
    @Test fun eightDigitsGainSeparatorsWithoutChangingIsoStorage() {
        assertEquals("2025-08-29", PublicationDateInput.format("20250829"))
        assertEquals("2025-08-29", PublicationDateInput.format("2025-08-29"))
        assertEquals("2025-08", PublicationDateInput.format("202508"))
        assertEquals("", PublicationDateInput.format(""))
        assertEquals("2025-08-29", PublicationDateInput.format("202508291234"))
    }

    @Test fun pasteIntoEmptyFieldPlacesCaretAtEnd() {
        val result = PublicationDateInput.edit(
            TextFieldValue("", selection = TextRange.Zero),
            TextFieldValue("20250829", selection = TextRange(8))
        )
        assertEquals("2025-08-29", result.text)
        assertEquals(result.text.length, result.selection.start)
    }

    @Test fun deletingSeparatorDoesNotTrapBackspace() {
        val previous = TextFieldValue("2025-08", selection = TextRange(5))
        val deleted = TextFieldValue("202508", selection = TextRange(4))
        val next = PublicationDateInput.edit(previous, deleted)
        assertEquals("2020-8", next.text)
        assertEquals(3, next.selection.start)
    }

    @Test fun impossibleDatesStayVisibleAndFailExistingValidation() {
        val date = PublicationDateInput.format("20250230")
        assertEquals("2025-02-30", date)
        assertFalse(GalleryKeys.validDate(date))
        assertFalse(GalleryKeys.validDate("2025-02"))
        assertTrue(GalleryKeys.validDate(""))
        assertTrue(GalleryKeys.validDate("2024-02-29"))
    }

    @Test fun formatOnlyAcceptsAsciiDigitsAndDoesNotInventMissingValues() {
        assertEquals("2025-08-29", PublicationDateInput.format("2025/08/29"))
        assertEquals("2025", PublicationDateInput.format("ab2025"))
        assertEquals("2025-08-29", PublicationDateInput.format("2025-08-29 ignored"))
    }
}
