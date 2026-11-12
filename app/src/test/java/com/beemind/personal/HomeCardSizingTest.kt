package com.beemind.personal

import com.beemind.personal.domain.*
import com.beemind.personal.ui.notes.*
import org.junit.Assert.*
import org.junit.Test

class HomeCardSizingTest {
    @Test fun identityMappingHasGoldenValuesAcrossRuntimeRestarts() {
        assertEquals(HomeCardSize.Compact, homeCardSize("short"))
        assertEquals(HomeCardSize.Tall, homeCardSize("c"))
        assertEquals(HomeCardSize.Medium, homeCardSize("a"))
    }
    @Test fun reorderResurfaceAndTextOrTraitEditsDoNotChangeSize() {
        val notes = (0..99).map { NoteSummary("note-$it", null, "نفس النص", it.toLong(), 0) }
        val original = notes.associate { it.id to homeCardSize(it.id) }
        val changed = notes.reversed().map { it.copy(preview = "نص مختلف", expressiveTrait = ExpressiveTrait.GLOW, resurfaced = true) }
        assertEquals(original, changed.associate { it.id to homeCardSize(it.id) })
        val counts = original.values.groupingBy { it }.eachCount()
        assertEquals(3, counts.size)
        assertTrue(counts.values.all { it in 20..45 })
        assertTrue(HomeCardSize.Tall.heightDp - HomeCardSize.Compact.heightDp >= 80)
    }
}
