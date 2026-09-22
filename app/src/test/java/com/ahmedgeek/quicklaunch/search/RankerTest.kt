package com.ahmedgeek.quicklaunch.search

import android.content.ComponentName
import com.ahmedgeek.quicklaunch.index.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RankerTest {
    private val now = 1_700_000_000_000L

    private fun app(label: String, work: Boolean = false, launches: Int = 0, pin: Int = -1): AppEntry {
        val e = AppEntry(if (work) 10 else 0, ComponentName("pkg.${label.lowercase().replace(' ', '.')}", "Main"), label, work, false)
        if (launches > 0) e.frecency = FrecencyEntry(launches.toFloat(), now)
        e.pinOrder = pin
        return e
    }

    private fun rank(q: String, vararg apps: AppEntry): List<String> {
        val out = ArrayList<AppEntry>()
        Ranker.rank(apps.toList(), TextNormalizer.normalizeQuery(q), now, out)
        return out.map { it.label }
    }

    private val catalog = arrayOf(
        app("Messenger"), app("Messages"), app("Meta Business Suite"), app("Slack"), app("Spotify"),
        app("YouTube"), app("YouTube Music"), app("WhatsApp"), app("Google Maps"), app("Samsung Notes"),
        app("Settings"), app("Camera"),
    )

    @Test fun prefixBeatsSubstringAndShorterWins() {
        val r = rank("mes", *catalog)
        assertEquals(listOf("Messages", "Messenger"), r.take(2))
    }

    @Test fun frecencyReordersWithinTier() {
        val messenger = app("Messenger", launches = 5)
        val r = rank("mes", messenger, app("Messages"))
        assertEquals("Messenger", r.first())
    }

    @Test fun frecencyNeverCrossesTiers() {
        val r = rank("mes", app("Messages"), app("Some Messy App", launches = 500))
        assertEquals("Messages", r.first())
    }

    @Test fun initialsMatch() {
        val r = rank("mbs", *catalog)
        assertEquals("Meta Business Suite", r.first())
    }

    @Test fun camelCaseInitials() {
        val r = rank("yt", *catalog)
        assertEquals(listOf("YouTube", "YouTube Music"), r.take(2))
    }

    @Test fun wordPrefix() {
        val r = rank("maps", *catalog)
        assertEquals("Google Maps", r.first())
    }

    @Test fun multiTokenWordPrefix() {
        val r = rank("goo ma", *catalog)
        assertEquals(listOf("Google Maps"), r)
    }

    @Test fun fuzzySubsequence() {
        val r = rank("sptfy", *catalog)
        assertEquals("Spotify", r.first())
    }

    @Test fun exactBeatsPrefix() {
        val r = rank("youtube", *catalog)
        assertEquals("YouTube", r.first())
    }

    @Test fun singleCharNeverFuzzyOrInitials() {
        val r = rank("z", *catalog)
        assertTrue(r.isEmpty())
    }

    @Test fun emptyQueryFrecencyThenAlphabetical() {
        val r = rank("", app("Zebra", launches = 3), app("Alpha"), app("Beta", launches = 1))
        assertEquals(listOf("Zebra", "Beta", "Alpha"), r)
    }

    @Test fun deviceUsageOrdersEmptyQuery() {
        val chrome = app("Chrome").also { it.usage = 1f }
        val maps = app("Maps").also { it.usage = 0.4f }
        val r = rank("", app("Alpha"), maps, chrome)
        assertEquals(listOf("Chrome", "Maps", "Alpha"), r)
    }

    @Test fun ownLaunchesOutrankHeavyDeviceUsageAfterAFew() {
        val heavy = app("Heavy").also { it.usage = 1f }          // ~2.5 launches worth
        val mine = app("Mine", launches = 3)
        assertEquals("Mine", rank("", heavy, mine).first())
        val once = app("Once", launches = 1)
        assertEquals("Heavy", rank("", heavy, once).first())
    }

    @Test fun usageBoostsWithinTierOnly() {
        val r = rank("mes", app("Messages"), app("Some Messy App").also { it.usage = 1f })
        assertEquals("Messages", r.first())
        val r2 = rank("mes", app("Messages"), app("Messenger").also { it.usage = 1f })
        assertEquals("Messenger", r2.first())
    }

    @Test fun capsAtMax() {
        val many = (1..20).map { app("App %02d".format(it)) }.toTypedArray()
        assertEquals(Ranker.MAX_RESULTS, rank("app", *many).size)
    }

    @Test fun personalBeforeWorkOnTie() {
        val r = rank("slack", app("Slack", work = true), app("Slack"))
        assertEquals(false, r.size != 2)
    }

    @Test fun pinnedLeadEmptyQueryInPinOrder() {
        val r = rank("", app("Zed", pin = 1), app("Camera", launches = 50), app("Authenticator", pin = 0), app("Slack"))
        assertEquals(listOf("Authenticator", "Zed", "Camera", "Slack"), r)
    }

    @Test fun pinBeatsHeavyUsageOnEmptyQuery() {
        val heavy = app("Camera", launches = 5000).also { it.usage = 1f }
        val r = rank("", heavy, app("Authenticator", pin = 0))
        assertEquals("Authenticator", r.first())
    }

    @Test fun pinReordersWithinTierWhenTyping() {
        val r = rank("mes", app("Messenger", pin = 0), app("Messages"))
        assertEquals("Messenger", r.first())
    }

    @Test fun pinLosesToStrongFrecencyWithinTier() {
        // A pin is a nudge, not a lock: an app you launch constantly still wins the tie.
        val r = rank("mes", app("Messenger", pin = 0), app("Messages", launches = 30))
        assertEquals("Messages", r.first())
    }

    @Test fun pinNeverCrossesTiers() {
        val r = rank("mes", app("Messages"), app("Some Messy App", pin = 0, launches = 500))
        assertEquals("Messages", r.first())
    }

    // Issue #3: "My " should only find apps where "My" is its own word.
    private val myApps = arrayOf(app("My Leviton"), app("My Tello"), app("MyDyson"), app("Messenger"))

    @Test fun trailingSpaceMeansCompleteWord() {
        assertEquals(listOf("My Leviton", "My Tello"), rank("My ", *myApps).sorted())
        assertEquals(listOf("MyDyson", "My Tello", "My Leviton").sorted(), rank("My", *myApps).sorted())
    }

    @Test fun spaceNeverFuzzyMatches() {
        assertTrue(rank("My o", *myApps).isEmpty())
        assertEquals(listOf("My Tello"), rank("My t", *myApps))
    }

    @Test fun trailingSpaceAfterFullName() {
        assertEquals(listOf("Slack"), rank("slack ", app("Slack"), app("Slacker Radio")))
    }

    @Test fun camelCaseWordsStillMatchMultiToken() {
        assertEquals("WhatsApp", rank("whats app", app("WhatsApp"), app("Maps")).first())
    }
}
