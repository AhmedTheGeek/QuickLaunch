package com.ahmedgeek.quicklaunch.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrecencyTest {
    private val day = 24 * 60 * 60 * 1000L

    @Test fun halvesAfterHalfLife() {
        val d = Frecency.decay(4f, 0L, 7 * day)
        assertEquals(2f, d, 0.01f)
    }

    @Test fun noDecayAtSameInstant() = assertEquals(3f, Frecency.decay(3f, 100L, 100L), 0f)

    @Test fun boostGrowsAndSaturates() {
        assertEquals(0, Frecency.boost(0f))
        assertTrue(Frecency.boost(1f) in 40..45)
        assertTrue(Frecency.boost(5f) > Frecency.boost(1f))
        assertEquals(Frecency.MAX_BOOST, Frecency.boost(1_000_000f))
    }
}
