package com.ahmedgeek.quicklaunch.search

import org.junit.Assert.assertEquals
import org.junit.Test

class AliasStoreTest {
    @Test fun roundTripKeepsOrder() {
        val m = linkedMapOf("sp" to "0|com.spotify.music/.MainActivity", "wa" to "0|com.whatsapp/.Main")
        assertEquals(m.toList(), AliasStore.parse(AliasStore.serialize(m)).toList())
    }

    @Test fun parseSkipsJunk() {
        assertEquals(emptyMap<String, String>(), AliasStore.parse(null))
        assertEquals(emptyMap<String, String>(), AliasStore.parse(""))
        assertEquals(mapOf("sp" to "k"), AliasStore.parse("sp\tk\nnotab\n\tkey\nalias\t"))
    }
}
