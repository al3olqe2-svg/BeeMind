package com.beemind.personal

import com.beemind.personal.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReminderTimesTest {
    private val now = Instant.parse("2026-01-01T12:00:00Z")
    @Test fun relativeReferenceIsStableAndFiveMinutesCanBeAdded() {
        val five = ReminderTimes.relative(now, 5)
        assertEquals(now.plusSeconds(300).toEpochMilli(), five)
        assertEquals(five + 300_000, ReminderTimes.relative(now, 10))
        listOf(5L, 10L, 30L, 93L).forEach { assertEquals(now.toEpochMilli() + it * 60_000, ReminderTimes.relative(now, it)) }
    }
    @Test fun localTimeUsesExplicitZoneAndPreservesInstantAcrossZoneChanges() {
        val due = ReminderTimes.local(LocalDateTime.of(2026, 1, 2, 15, 20), ZoneId.of("Asia/Riyadh"), now)
        assertEquals(Instant.parse("2026-01-02T12:20:00Z").toEpochMilli(), due)
        assertEquals(due, Instant.ofEpochMilli(due).atZone(ZoneId.of("America/New_York")).toInstant().toEpochMilli())
    }
    @Test fun pastNowDstGapAndDstOverlapAreRejected() {
        fun rejected(block: () -> Unit) { try { block(); fail("Expected invalid time") } catch (_: ReminderTimeException) { } }
        rejected { ReminderTimes.future(now.toEpochMilli(), now) }
        rejected { ReminderTimes.relative(now, 0) }
        rejected { ReminderTimes.relative(now, 525601) }
        val zone = ZoneId.of("America/New_York")
        rejected { ReminderTimes.local(LocalDateTime.of(2026, 3, 8, 2, 30), zone, now) }
        rejected { ReminderTimes.local(LocalDateTime.of(2026, 11, 1, 1, 30), zone, now) }
    }
}
