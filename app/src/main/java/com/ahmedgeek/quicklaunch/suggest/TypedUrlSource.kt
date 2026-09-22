package com.ahmedgeek.quicklaunch.suggest

import android.content.Context
import com.ahmedgeek.quicklaunch.R
import com.ahmedgeek.quicklaunch.clipboard.LinkDetector

/**
 * A typed link: `github.com/foo`, `https://…`, `www.example.org`. Same strict check as the clipboard
 * row, so file names and versions never count. A bare domain goes below the apps, so an app called
 * "Booking.com" still comes first for `booking.com`.
 */
class TypedUrlSource(private val context: Context) : SuggestionSource {
    override fun suggest(raw: String, query: String, out: MutableList<Suggestion>) {
        val url = LinkDetector.detect(raw) ?: return
        out.add(
            Suggestion(
                // Per host: the handler app is the same for the whole site, so its icon resolves once.
                key = "url|" + host(url),
                title = LinkDetector.display(url),
                badge = context.getText(R.string.link_open),
                glyph = R.drawable.ic_link,
                handlerUrl = url,
                belowApps = isBareDomain(raw),
            ) { c -> Suggestion.openUrl(c, url) },
        )
    }

    companion object {
        fun isBareDomain(raw: String): Boolean =
            !raw.contains("://") && raw.none { it == '/' || it == '?' || it == '#' } &&
                !raw.startsWith("www.", ignoreCase = true)

        fun host(url: String): String {
            val start = url.indexOf("://").let { if (it < 0) 0 else it + 3 }
            var end = start
            while (end < url.length && url[end] != '/' && url[end] != '?' && url[end] != '#' && url[end] != ':') end++
            return url.substring(start, end).lowercase()
        }
    }
}
