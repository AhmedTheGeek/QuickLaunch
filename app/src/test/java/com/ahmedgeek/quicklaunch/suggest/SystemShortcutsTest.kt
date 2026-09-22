package com.ahmedgeek.quicklaunch.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemShortcutsTest {
    private fun ids(q: String) = SystemShortcuts.match(q).map { "${it.shortcut.id}${if (it.exact) "!" else ""}" }

    @Test fun exactNamesAndKeywords() {
        assertEquals(listOf("wifi!"), ids("wifi"))
        assertEquals(listOf("wifi!"), ids("wi fi"))
        assertEquals(listOf("bluetooth!"), ids("bt"))
        assertEquals(listOf("flashlight!"), ids("torch"))
    }

    @Test fun prefixNeedsThreeLetters() {
        assertTrue(ids("di").isEmpty())
        assertEquals(listOf("display"), ids("disp"))
        assertEquals(listOf("battery"), ids("batt"))
    }

    @Test fun matchesLaterWords() {
        assertEquals(listOf("developer"), ids("usb deb"))
    }

    @Test fun exactComesFirstAndListIsCapped() {
        val r = SystemShortcuts.match("mobile data")
        assertTrue(r.size <= 2)
        assertTrue(r.all { it.exact })
    }

    @Test fun keysRoundTrip() {
        for (s in SystemShortcuts.ALL) assertEquals(s, SystemShortcuts.byKey(s.key))
    }

    @Test fun nothingForAppNames() {
        assertTrue(ids("spotify").isEmpty())
        assertTrue(ids("").isEmpty())
    }
}
