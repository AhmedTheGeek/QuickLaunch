package com.ahmedgeek.quicklaunch.suggest

import android.content.Context
import com.ahmedgeek.quicklaunch.R

/** `3x3` shows `= 9`; Enter copies the result and closes. */
class CalculatorSource(private val context: Context) : SuggestionSource {
    override fun suggest(raw: String, query: String, out: MutableList<Suggestion>) {
        val value = Calculator.evaluate(raw) ?: return
        val text = Calculator.format(value)
        out.add(
            Suggestion(
                key = "calc",
                title = "= $text",
                badge = context.getText(R.string.copy),
                glyph = R.drawable.ic_calculator,
                handlerUrl = null,
            ) { c -> Suggestion.copy(c, text) },
        )
    }
}
