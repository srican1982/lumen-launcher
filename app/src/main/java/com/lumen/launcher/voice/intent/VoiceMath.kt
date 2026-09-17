package com.lumen.launcher.voice.intent

import com.lumen.launcher.search.Calculator
import java.util.Locale
import kotlin.math.round

object VoiceMath {
    private val units = """miles?|kilometers?|kilometres?|km|kilograms?|kilos?|kg|pounds?|lbs?|meters?|metres?|feet|foot|celsius|fahrenheit"""

    fun interpret(raw: String): String? {
        Calculator.interpret(raw)?.let { calculation ->
            return "${calculation.expression} is ${calculation.value}."
        }
        return convert(raw)
    }

    private fun convert(raw: String): String? {
        val q = raw.lowercase().trim()
        val direct = Regex(
            """(?:convert\s+)?(\d+(?:\.\d+)?)\s*($units)\s+(?:to|in|into)\s+($units)"""
        ).find(q)
        val reversed = Regex(
            """how many\s+($units)\s+(?:is|are|in)\s+(\d+(?:\.\d+)?)\s*($units)"""
        ).find(q)

        val value: Double
        val from: String
        val to: String
        if (direct != null) {
            value = direct.groupValues[1].toDouble()
            from = unit(direct.groupValues[2])
            to = unit(direct.groupValues[3])
        } else if (reversed != null) {
            to = unit(reversed.groupValues[1])
            value = reversed.groupValues[2].toDouble()
            from = unit(reversed.groupValues[3])
        } else {
            return null
        }

        val result = when (from to to) {
            "mile" to "kilometer" -> value * 1.60934
            "kilometer" to "mile" -> value / 1.60934
            "kilogram" to "pound" -> value * 2.20462
            "pound" to "kilogram" -> value / 2.20462
            "meter" to "foot" -> value * 3.28084
            "foot" to "meter" -> value / 3.28084
            "celsius" to "fahrenheit" -> value * 9.0 / 5.0 + 32.0
            "fahrenheit" to "celsius" -> (value - 32.0) * 5.0 / 9.0
            else -> return null
        }
        return "${format(value)} ${spokenUnit(from, value)} is ${format(result)} ${spokenUnit(to, result)}."
    }

    private fun unit(raw: String): String = when (raw) {
        "mile", "miles" -> "mile"
        "kilometer", "kilometers", "kilometre", "kilometres", "km" -> "kilometer"
        "kilogram", "kilograms", "kilo", "kilos", "kg" -> "kilogram"
        "pound", "pounds", "lbs" -> "pound"
        "meter", "meters", "metre", "metres" -> "meter"
        "foot", "feet" -> "foot"
        else -> raw
    }

    private fun spokenUnit(unit: String, value: Double): String {
        if (unit == "celsius" || unit == "fahrenheit") return "degrees $unit"
        return if (kotlin.math.abs(value - 1.0) < 0.0001) unit else when (unit) {
            "foot" -> "feet"
            else -> "${unit}s"
        }
    }

    private fun format(value: Double): String {
        val rounded = round(value * 100.0) / 100.0
        return if (rounded % 1.0 == 0.0) rounded.toLong().toString()
        else String.format(Locale.US, "%.2f", rounded).trimEnd('0').trimEnd('.')
    }
}
