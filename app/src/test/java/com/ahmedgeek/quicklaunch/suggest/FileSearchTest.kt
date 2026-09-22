package com.ahmedgeek.quicklaunch.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileSearchTest {
    @Test fun keyword() {
        assertEquals("invoice march", FileSearchSource.terms("f Invoice March "))
        assertEquals("", FileSearchSource.terms("f "))
        assertNull(FileSearchSource.terms("f"))
        assertNull(FileSearchSource.terms("facebook"))
        assertNull(FileSearchSource.terms("ff x"))
    }

    @Test fun selectionHasOneLikePerWord() {
        val s = FileSearchSource.selection(2)
        assertEquals(2, s.split("LIKE ?").size - 1)
        assertTrue(s.startsWith("mime_type IS NOT NULL"))
    }

    @Test fun likeArgEscapesWildcards() {
        assertEquals("%invoice%", FileSearchSource.likeArg("invoice"))
        assertEquals("""%50\%\_off%""", FileSearchSource.likeArg("50%_off"))
    }

    @Test fun narrowingMatchesAllWordsAnyOrder() {
        assertTrue(FileSearchSource.matches("March_Invoice_2026.pdf", "invoice march"))
        assertFalse(FileSearchSource.matches("Invoice.pdf", "invoice march"))
    }

    @Test fun folderBadge() {
        assertEquals("Invoices", FileSearchSource.folder("Documents/Invoices/"))
        assertEquals("Download", FileSearchSource.folder("Download/"))
        assertEquals("", FileSearchSource.folder(null))
    }
}
