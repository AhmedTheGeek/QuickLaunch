package com.ahmedgeek.quicklaunch.suggest

/**
 * `10cm in inch`, `70 f to c`, `5 km to miles`, `2+3 kg in lb`. The amount may be any [Calculator]
 * expression. Factors are fixed tables, so no network and nothing to update; currencies are left out
 * for that reason.
 */
object UnitConverter {

    class Result(@JvmField val value: Double, @JvmField val unit: String)

    private enum class Dim { LENGTH, MASS, VOLUME, AREA, SPEED, TIME, DATA, TEMPERATURE, ENERGY, PRESSURE, ANGLE }

    /** value in base unit = (value + offset) * factor. Offset is only used for temperatures. */
    private class Unit(val dim: Dim, val symbol: String, val factor: Double, val offset: Double = 0.0)

    private val UNITS = HashMap<String, Unit>(256)

    private fun def(dim: Dim, factor: Double, vararg names: String, offset: Double = 0.0) {
        val u = Unit(dim, names[0], factor, offset)
        for (n in names) UNITS[n] = u
    }

    init {
        with(Dim.LENGTH) {
            def(this, 0.001, "mm", "millimeter", "millimeters", "millimetre", "millimetres")
            def(this, 0.01, "cm", "centimeter", "centimeters", "centimetre", "centimetres")
            def(this, 1.0, "m", "meter", "meters", "metre", "metres")
            def(this, 1000.0, "km", "kilometer", "kilometers", "kilometre", "kilometres")
            def(this, 0.0254, "in", "inch", "inches", "\"")
            def(this, 0.3048, "ft", "foot", "feet", "'")
            def(this, 0.9144, "yd", "yard", "yards")
            def(this, 1609.344, "mi", "mile", "miles")
            def(this, 1852.0, "nmi", "nautical mile", "nautical miles")
        }
        with(Dim.MASS) {
            def(this, 1e-6, "mg", "milligram", "milligrams")
            def(this, 0.001, "g", "gram", "grams")
            def(this, 1.0, "kg", "kilo", "kilos", "kilogram", "kilograms")
            def(this, 1000.0, "t", "tonne", "tonnes", "ton", "tons")
            def(this, 0.028349523125, "oz", "ounce", "ounces")
            def(this, 0.45359237, "lb", "lbs", "pound", "pounds")
            def(this, 6.35029318, "st", "stone", "stones")
        }
        with(Dim.VOLUME) {
            def(this, 0.001, "ml", "milliliter", "milliliters", "millilitre", "millilitres")
            def(this, 0.01, "cl", "centiliter", "centiliters", "centilitre", "centilitres")
            def(this, 0.1, "dl", "deciliter", "deciliters", "decilitre", "decilitres")
            def(this, 1.0, "l", "liter", "liters", "litre", "litres")
            def(this, 1000.0, "m3", "m³", "cubic meter", "cubic meters")
            def(this, 0.00492892159375, "tsp", "teaspoon", "teaspoons")
            def(this, 0.01478676478125, "tbsp", "tablespoon", "tablespoons")
            def(this, 0.2365882365, "cup", "cups")
            def(this, 0.0295735295625, "fl oz", "floz", "fluid ounce", "fluid ounces")
            def(this, 0.473176473, "pt", "pint", "pints")
            def(this, 0.946352946, "qt", "quart", "quarts")
            def(this, 3.785411784, "gal", "gallon", "gallons")
        }
        with(Dim.AREA) {
            def(this, 1e-4, "cm2", "cm²")
            def(this, 1.0, "m2", "m²", "sqm", "square meter", "square meters")
            def(this, 1e6, "km2", "km²", "square kilometer", "square kilometers")
            def(this, 1e4, "ha", "hectare", "hectares")
            def(this, 4046.8564224, "acre", "acres")
            def(this, 0.09290304, "ft2", "ft²", "sqft", "square foot", "square feet")
            def(this, 2589988.110336, "mi2", "mi²", "square mile", "square miles")
        }
        with(Dim.SPEED) {
            def(this, 1.0, "m/s", "mps")
            def(this, 1 / 3.6, "km/h", "kmh", "kph")
            def(this, 0.44704, "mph")
            def(this, 1852.0 / 3600, "kn", "knot", "knots")
            def(this, 0.3048, "ft/s", "fps")
        }
        with(Dim.TIME) {
            def(this, 0.001, "ms", "millisecond", "milliseconds")
            def(this, 1.0, "s", "sec", "secs", "second", "seconds")
            def(this, 60.0, "min", "mins", "minute", "minutes")
            def(this, 3600.0, "h", "hr", "hrs", "hour", "hours")
            def(this, 86400.0, "day", "days", "d")
            def(this, 604800.0, "week", "weeks", "wk")
            def(this, 31557600.0, "year", "years", "yr")
        }
        with(Dim.DATA) {
            def(this, 1.0, "B", "byte", "bytes", "b")
            def(this, 0.125, "bit", "bits")
            def(this, 1e3, "KB", "kb", "kilobyte", "kilobytes")
            def(this, 1e6, "MB", "mb", "megabyte", "megabytes")
            def(this, 1e9, "GB", "gb", "gigabyte", "gigabytes")
            def(this, 1e12, "TB", "tb", "terabyte", "terabytes")
            def(this, 1024.0, "KiB", "kib")
            def(this, 1048576.0, "MiB", "mib")
            def(this, 1073741824.0, "GiB", "gib")
            def(this, 1099511627776.0, "TiB", "tib")
            def(this, 125.0, "kbit", "kbps")
            def(this, 125000.0, "Mbit", "mbit", "mbps")
            def(this, 125000000.0, "Gbit", "gbit", "gbps")
        }
        with(Dim.TEMPERATURE) {
            def(this, 1.0, "°C", "c", "°c", "celsius", offset = 273.15)
            def(this, 5.0 / 9, "°F", "f", "°f", "fahrenheit", offset = 459.67)
            def(this, 1.0, "K", "k", "kelvin")
        }
        with(Dim.ENERGY) {
            def(this, 1.0, "J", "j", "joule", "joules")
            def(this, 1000.0, "kJ", "kj", "kilojoule", "kilojoules")
            def(this, 4.184, "cal", "calorie", "calories")
            def(this, 4184.0, "kcal", "kilocalorie", "kilocalories")
            def(this, 3600.0, "Wh", "wh")
            def(this, 3.6e6, "kWh", "kwh")
        }
        with(Dim.PRESSURE) {
            def(this, 1.0, "Pa", "pa", "pascal", "pascals")
            def(this, 1000.0, "kPa", "kpa")
            def(this, 100.0, "hPa", "hpa", "mbar")
            def(this, 1e5, "bar")
            def(this, 6894.757293168, "psi")
            def(this, 101325.0, "atm")
        }
        with(Dim.ANGLE) {
            def(this, Math.PI / 180, "°", "deg", "degree", "degrees")
            def(this, 1.0, "rad", "radian", "radians")
        }
    }

    private val SEPARATORS = arrayOf(" in ", " to ", " as ", "=")

    /** Longest unit name we look for at the end of the amount. */
    private const val MAX_UNIT_LENGTH = 18

    fun convert(input: String): Result? {
        val s = input.trim()
        // The last separator, so "5 in in cm" reads as (5 in) in (cm).
        var sep = -1
        var sepLen = 0
        for (candidate in SEPARATORS) {
            val at = s.lastIndexOf(candidate, ignoreCase = true)
            if (at > sep) {
                sep = at
                sepLen = candidate.length
            }
        }
        if (sep <= 0) return null
        val to = unit(s.substring(sep + sepLen).trim()) ?: return null
        val left = s.substring(0, sep).trim()
        // Try the longest unit name that ends the left side and leaves a valid amount before it.
        for (len in minOf(left.length - 1, MAX_UNIT_LENGTH) downTo 1) {
            val from = unit(left.substring(left.length - len).trim()) ?: continue
            if (from.dim != to.dim) continue
            val amount = Calculator.evaluate(left.substring(0, left.length - len), requireOp = false) ?: continue
            val base = (amount + from.offset) * from.factor
            return Result(base / to.factor - to.offset, to.symbol)
        }
        return null
    }

    /** Names are stored as written ("KiB") and in lowercase; try both. */
    private fun unit(name: String): Unit? {
        if (name.isEmpty()) return null
        return UNITS[name] ?: UNITS[name.lowercase()]
    }
}
