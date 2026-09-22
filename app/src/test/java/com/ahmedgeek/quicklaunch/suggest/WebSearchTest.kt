package com.ahmedgeek.quicklaunch.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebSearchTest {
    private val engines = WebSearch.DEFAULTS

    private fun url(raw: String): String? = WebSearch.match(raw, engines)?.let { it.engine.urlFor(it.terms) }

    @Test fun keywordAndTerms() {
        assertEquals("https://www.google.com/search?q=restaurants+near+me", url("g restaurants near me"))
        assertEquals("https://www.youtube.com/results?search_query=funny+cat", url("yt funny cat"))
        assertEquals("https://www.youtube.com/results?search_query=funny+cat", url("YT  funny cat "))
    }

    @Test fun termsAreEncoded() {
        assertEquals("https://www.google.com/search?q=c%2B%2B+%26+rust", url("g c++ & rust"))
    }

    @Test fun needsKeywordSpaceAndTerms() {
        assertNull(url("g"))
        assertNull(url("g "))
        assertNull(url("youtube"))
        assertNull(url("gmail"))
        assertNull(url("google maps"))
        assertNull(url(" g x"))
    }
}
