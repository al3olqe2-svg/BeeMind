package com.beemind.personal

import com.beemind.personal.ui.notes.masonryColumns
import com.beemind.personal.ui.notes.readingChunks
import org.junit.Assert.*
import org.junit.Test

class NotePresentationTest {
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
