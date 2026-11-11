package com.beemind.personal

import com.beemind.personal.ui.notes.masonryColumns
import com.beemind.personal.ui.notes.readingChunks
import org.junit.Assert.*
import org.junit.Test

class NotePresentationTest {
    @Test fun calendarCivilDateDoesNotShiftInNegativeDeviceTimezones() {
        val previous = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Los_Angeles"))
            val selectedDate = java.time.Instant.parse("2027-01-01T00:00:00Z").toEpochMilli()
            // Material DatePicker encodes a civil date as UTC midnight, not a local instant.
            val headline = com.beemind.personal.ui.notes.formatNoteDate(selectedDate, java.time.ZoneOffset.UTC)
            assertTrue(headline.startsWith("1 ")); assertTrue(headline.contains("يناير"))
            assertTrue(com.beemind.personal.ui.notes.formatNoteDate(selectedDate).contains("ديسمبر"))
        } finally { java.util.TimeZone.setDefault(previous) }
    }
    @Test fun compactDatesUseLocalZoneAndRetainFullInternalTimestamp() {
        val previous = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Riyadh"))
            val stamp = java.time.Instant.parse("2026-12-31T22:03:05Z").toEpochMilli()
            val value = com.beemind.personal.ui.components.reminderTimestamp(stamp)
            assertEquals(com.beemind.personal.ui.notes.formatNoteTimestamp(stamp), value)
            assertTrue(value.contains("يناير")); assertTrue(value.contains("01:03"))
            assertFalse(value.contains("2027")); assertFalse(value.contains("GMT")); assertFalse(value.contains("UTC"))
            assertFalse(value.contains("01:03:05")); assertEquals(1798754585000L, stamp)
        } finally { java.util.TimeZone.setDefault(previous) }
    }
    @Test fun lazyReadingPreservesLongArabicAndEmojiAtChunkBoundaries() {
        val text = ("ع".repeat(3_999) + "🌈\nيوم طويل\n").repeat(50)
        val chunks = readingChunks(text)
        assertEquals(text, chunks.joinToString(""))
        assertTrue(chunks.all { it.length <= 4_000 && !it.last().isHighSurrogate() })
    }
    @Test fun narrowScreensAndLargeFontsUseOneReadableColumn() {
        assertEquals(2, masonryColumns(393, 1f))
        assertEquals(1, masonryColumns(320, 1f))
        assertEquals(1, masonryColumns(393, 1.5f))
    }
}
