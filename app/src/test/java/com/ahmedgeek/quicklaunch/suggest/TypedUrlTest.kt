package com.ahmedgeek.quicklaunch.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypedUrlTest {
    @Test fun bareDomainGoesBelowApps() {
        assertTrue(TypedUrlSource.isBareDomain("booking.com"))
        assertFalse(TypedUrlSource.isBareDomain("github.com/AhmedTheGeek"))
        assertFalse(TypedUrlSource.isBareDomain("www.example.org"))
        assertFalse(TypedUrlSource.isBareDomain("https://example.org"))
    }

    @Test fun hostForIconKey() {
        assertEquals("github.com", TypedUrlSource.host("https://github.com/a/b?c=1"))
        assertEquals("example.org", TypedUrlSource.host("https://Example.org:8080/x"))
    }
}
