package com.ahmedgeek.quicklaunch.search

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {
    @Test fun lowercasesAndTrims() = assertEquals("messenger", TextNormalizer.normalize("  Messenger "))
    @Test fun stripsDiacritics() = assertEquals("cafe muller", TextNormalizer.normalize("Café  Müller!"))
    @Test fun collapsesPunctuation() = assertEquals("meta business suite", TextNormalizer.normalize("Meta - Business_Suite"))
    @Test fun keepsDigits() = assertEquals("1password", TextNormalizer.normalize("1Password"))
    @Test fun emptyStaysEmpty() = assertEquals("", TextNormalizer.normalize(""))

    @Test fun splitsCamelCase() = assertArrayEquals(arrayOf("you", "tube"), TextNormalizer.splitWords("YouTube"))
    @Test fun splitsCamelCaseWithSpaces() =
        assertArrayEquals(arrayOf("whats", "app", "business"), TextNormalizer.splitWords("WhatsApp Business"))
    @Test fun splitsAcronymBoundary() = assertArrayEquals(arrayOf("html", "viewer"), TextNormalizer.splitWords("HTMLViewer"))
    @Test fun keepsAllCaps() = assertArrayEquals(arrayOf("dex"), TextNormalizer.splitWords("DEX"))

    @Test fun initials() = assertEquals("mbs", TextNormalizer.initials(arrayOf("meta", "business", "suite")))
}
