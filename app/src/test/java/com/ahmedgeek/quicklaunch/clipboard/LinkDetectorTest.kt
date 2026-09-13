package com.ahmedgeek.quicklaunch.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkDetectorTest {
    @Test fun fullUrlsPassThroughUnchanged() {
        assertEquals("https://github.com/AhmedTheGeek/QuickLaunch", LinkDetector.detect("https://github.com/AhmedTheGeek/QuickLaunch"))
        assertEquals("http://localhost:8080/x?y=1#z", LinkDetector.detect("http://localhost:8080/x?y=1#z"))
        assertEquals("HTTPS://Example.COM", LinkDetector.detect("HTTPS://Example.COM"))
        assertEquals("https://[::1]:3000/", LinkDetector.detect("https://[::1]:3000/"))
        assertEquals("https://münchen.de/straße", LinkDetector.detect("https://münchen.de/straße"))
    }

    @Test fun surroundingWhitespaceIsTrimmed() {
        assertEquals("https://example.com", LinkDetector.detect("  https://example.com\n"))
    }

    @Test fun bareDomainsGetHttps() {
        assertEquals("https://example.com", LinkDetector.detect("example.com"))
        assertEquals("https://www.wikipedia.org", LinkDetector.detect("www.wikipedia.org"))
        assertEquals("https://docs.google.com/document/d/abc", LinkDetector.detect("docs.google.com/document/d/abc"))
        assertEquals("https://youtu.be/dQw4w9WgXcQ", LinkDetector.detect("youtu.be/dQw4w9WgXcQ"))
        assertEquals("https://www.notes.md", LinkDetector.detect("www.notes.md"))
        assertEquals("https://ahmedgeek.com:8443", LinkDetector.detect("ahmedgeek.com:8443"))
    }

    @Test fun filenamesAndNumbersAreNotLinks() {
        assertNull(LinkDetector.detect("notes.md"))
        assertNull(LinkDetector.detect("index.html"))
        assertNull(LinkDetector.detect("settings.gradle.kts"))
        assertNull(LinkDetector.detect("v1.2.3"))
        assertNull(LinkDetector.detect("3.14"))
        assertNull(LinkDetector.detect("e.g"))
        assertNull(LinkDetector.detect("R.string.app_name"))
    }

    @Test fun proseAndOtherSchemesAreNotLinks() {
        assertNull(LinkDetector.detect(null))
        assertNull(LinkDetector.detect(""))
        assertNull(LinkDetector.detect("   "))
        assertNull(LinkDetector.detect("see https://example.com for details"))
        assertNull(LinkDetector.detect("https://example.com\nhttps://other.com"))
        assertNull(LinkDetector.detect("mailto:me@ahmedgeek.com"))
        assertNull(LinkDetector.detect("me@ahmedgeek.com"))
        assertNull(LinkDetector.detect("ftp://files.example.com"))
        assertNull(LinkDetector.detect("https://"))
        assertNull(LinkDetector.detect("http://?"))
        assertNull(LinkDetector.detect("h" + "t".repeat(2100)))
    }

    @Test fun displayStripsSchemeAndBareSlash() {
        assertEquals("github.com/x/y", LinkDetector.display("https://github.com/x/y"))
        assertEquals("example.com", LinkDetector.display("https://example.com/"))
        assertEquals("example.com/a/", LinkDetector.display("http://example.com/a/"))
        assertEquals("localhost:8080", LinkDetector.display("http://localhost:8080"))
    }
}
