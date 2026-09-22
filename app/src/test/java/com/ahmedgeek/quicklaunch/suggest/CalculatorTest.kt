package com.ahmedgeek.quicklaunch.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorTest {
    private fun calc(s: String): String? = Calculator.evaluate(s)?.let { Calculator.format(it) }

    @Test fun basics() {
        assertEquals("9", calc("3x3"))
        assertEquals("9", calc("3 x 3"))
        assertEquals("9", calc("3*3"))
        assertEquals("14", calc("2+3*4"))
        assertEquals("20", calc("(2+3)*4"))
        assertEquals("2.5", calc("5/2"))
        assertEquals("-1", calc("2-3"))
        assertEquals("7", calc(" 10 - 3 "))
    }

    @Test fun precedenceAndAssociativity() {
        assertEquals("-4", calc("-2^2"))
        assertEquals("512", calc("2^3^2"))
        assertEquals("0.5", calc("2^-1"))
        assertEquals("1", calc("10-5-4"))
        assertEquals("1", calc("8/4/2"))
    }

    @Test fun decimalsAndRounding() {
        assertEquals("0.3", calc("0.1+0.2"))
        assertEquals("4.5", calc("3,5+1"))
        assertEquals("0.333333333333", calc("1/3"))
        assertEquals("1.5", calc(".5+1"))
    }

    @Test fun percentAndFactorial() {
        assertEquals("12", calc("15% * 80"))
        assertEquals("120", calc("5!"))
    }

    @Test fun functionsAndConstants() {
        assertEquals("4", calc("sqrt(16)"))
        assertEquals("4", calc("sqrt 16"))
        assertEquals("3", calc("log2(8)"))
        assertEquals("2", calc("log(100)"))
        assertEquals("6.28318530718", calc("2pi"))
        assertEquals("10", calc("2(3+2)"))
        assertEquals("1", calc("cos(0)"))
    }

    @Test fun bigAndSmall() {
        assertEquals("1E+20", calc("10^20"))
        assertEquals("1000000", calc("1000*1000"))
    }

    @Test fun notMath() {
        assertNull(calc("42"))
        assertNull(calc("-5"))
        assertNull(calc("pi"))
        assertNull(calc("spotify"))
        assertNull(calc("1password"))
        assertNull(calc("2048"))
        assertNull(calc("4pda"))
        assertNull(calc("3 xbox"))
        assertNull(calc("2 3"))
        assertNull(calc("3+"))
        assertNull(calc("(3+2"))
        assertNull(calc("1/0"))
        assertNull(calc("sqrt(-1)"))
        assertNull(calc("g restaurants"))
        assertNull(calc(""))
    }
}
