package com.ahmedgeek.quicklaunch.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitConverterTest {
    private fun conv(s: String): String? =
        UnitConverter.convert(s)?.let { "${Calculator.format(it.value, digits = 7)} ${it.unit}" }

    @Test fun length() {
        assertEquals("3.937008 in", conv("10cm in inch"))
        assertEquals("3.937008 in", conv("10 cm to in"))
        assertEquals("3.106856 mi", conv("5 km to miles"))
        assertEquals("12.7 cm", conv("5 in in cm"))
        assertEquals("182.88 cm", conv("6 ft in cm"))
    }

    @Test fun temperature() {
        assertEquals("21.11111 °C", conv("70f to c"))
        assertEquals("212 °F", conv("100 c in fahrenheit"))
        assertEquals("0 °C", conv("273.15 k to c"))
        assertEquals("-40 °F", conv("-40 c to f"))
    }

    @Test fun otherDimensions() {
        assertEquals("11.02311 lb", conv("5 kg in lb"))
        assertEquals("100 km/h", conv("27.7777778 m/s to kmh"))
        assertEquals("1.5 h", conv("90 min in hours"))
        assertEquals("1024 MiB", conv("1 GiB to MiB"))
        assertEquals("3.785412 l", conv("1 gal to l"))
    }

    @Test fun amountIsAnExpression() {
        assertEquals("11.02311 lb", conv("2+3 kg in lb"))
        assertEquals("100 cm", conv("0.5*2 m in cm"))
    }

    @Test fun rejects() {
        assertNull(conv("10 cm in kg"))
        assertNull(conv("10 in"))
        assertNull(conv("cm in inch"))
        assertNull(conv("spotify"))
        assertNull(conv("things to do"))
        assertNull(conv("3x3"))
    }
}
