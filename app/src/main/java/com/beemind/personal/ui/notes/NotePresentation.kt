package com.beemind.personal.ui.notes

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatNoteDate(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): String = DateTimeFormatter.ofPattern("d MMMM")
    .withLocale(Locale.forLanguageTag("ar")).withZone(zone).format(Instant.ofEpochMilli(timestamp))

fun formatNoteTimestamp(timestamp: Long): String = DateTimeFormatter.ofPattern("d MMMM • HH:mm")
    .withLocale(Locale.forLanguageTag("ar")).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(timestamp))

/** Bounded lazy text blocks preserve every character, including newlines and surrogate pairs. */
fun readingChunks(text: String, size: Int = 4_000): List<String> {
    require(size >= 2)
    val chunks = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        var end = minOf(start + size, text.length)
        if (end < text.length) {
            val newline = text.lastIndexOf('\n', end - 1)
            if (newline > start + size / 2) end = newline + 1
            if (text[end - 1].isHighSurrogate()) end -= 1
        }
        chunks += text.substring(start, end)
        start = end
    }
    return chunks
}

fun masonryColumns(widthDp: Int, fontScale: Float): Int =
    if (widthDp >= 360 && widthDp / fontScale >= 340) 2 else 1
