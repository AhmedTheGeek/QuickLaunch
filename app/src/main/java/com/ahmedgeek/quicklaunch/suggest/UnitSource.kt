package com.ahmedgeek.quicklaunch.suggest

import android.content.Context
import com.ahmedgeek.quicklaunch.R

/** `10cm in inch` shows `= 3.937008 in`; Enter copies the number. */
class UnitSource(private val context: Context) : SuggestionSource {
    override fun suggest(raw: String, query: String, out: MutableList<Suggestion>) {
        val r = UnitConverter.convert(raw) ?: return
        val text = Calculator.format(r.value, digits = 7)
        out.add(
            Suggestion(
                key = "unit",
                title = "= $text ${r.unit}",
                badge = context.getText(R.string.copy),
                glyph = R.drawable.ic_convert,
                handlerUrl = null,
            ) { c -> Suggestion.copy(c, text) },
        )
    }
}
