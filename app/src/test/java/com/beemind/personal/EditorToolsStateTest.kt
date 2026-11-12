package com.beemind.personal

import com.beemind.personal.ui.notes.*
import java.time.*
import org.junit.Test
import org.junit.Assert.*

class EditorToolsStateTest {
    private val now = Instant.parse("2026-01-01T12:00:00Z")
    @Test fun repeatedFiveIsAnchoredAndBoundedWithoutChangingInitial() {
        var draft = AppointmentDraft(null)
        repeat(4) { index ->
            draft = draft.plusFive(now.plusSeconds(index * 70L))
            assertEquals(now.plusSeconds((index + 1) * 300L).toEpochMilli(), draft.due)
        }
        assertNull(draft.initial)
        draft = draft.copy(minutes = 525600).plusFive(now)
        assertEquals(525600, draft.minutes)
    }
    @Test fun invalidWallTimeRetainsSelectionForCorrectionAndBlocksCommit() {
        val zone = ZoneId.of("America/New_York")
        val initial = now.plusSeconds(3600).toEpochMilli()
        for (wall in listOf(LocalDateTime.of(2026,3,8,2,30), LocalDateTime.of(2026,11,1,1,30), LocalDateTime.of(2025,1,1,12,0))) {
            val draft = AppointmentDraft(initial).local(wall.toLocalDate(), wall.toLocalTime(), zone, now)
            assertNotNull(draft.error); assertFalse(draft.valid(now))
            assertEquals(initial, draft.initial); assertEquals(wall, draft.localValue)
            val fixed = draft.local(LocalDate.of(2026,11,2), LocalTime.of(14,35), zone, now)
            assertNull(fixed.error); assertTrue(fixed.valid(now))
            assertEquals(14, Instant.ofEpochMilli(fixed.due!!).atZone(zone).hour)
        }
    }
    @Test fun unchangedClockRetainsSecondsAndMillisWhileExplicitMinuteEditChangesThem() {
        val due = Instant.parse("2026-01-02T15:20:35.123Z").toEpochMilli()
        val zone = ZoneId.of("Asia/Riyadh")
        val wall = Instant.ofEpochMilli(due).atZone(zone).toLocalDateTime()
        val draft = AppointmentDraft(due)
        assertEquals(due, draft.chooseTime(wall, 18, 20, zone, now).due)
        assertEquals(due, draft.chooseTime(wall, 18, 20, zone, now).initial)
        assertEquals(Instant.parse("2026-01-02T15:21:00Z").toEpochMilli(), draft.chooseTime(wall, 18, 21, zone, now).due)
    }
    @Test fun categorySessionsAreIndependentAndBackAlwaysHasOneDestination() {
        val reminder = AppointmentDraft(123L).plusFive(now)
        val resurface = AppointmentDraft(456L)
        val capsule = AppointmentDraft(null).plusFive(now.plusSeconds(3600))
        assertEquals(123L, reminder.initial); assertEquals(456L, resurface.due)
        assertEquals(3600000L, capsule.due!! - reminder.due!!)
        ToolExpansionState.entries.filter { it != ToolExpansionState.Closed && it != ToolExpansionState.Primary }.forEach {
            assertEquals(ToolExpansionState.Primary, it.back())
        }
        assertEquals(ToolExpansionState.Closed, ToolExpansionState.Primary.back())
    }
}
