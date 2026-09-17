package com.lumen.launcher.search

import java.util.Locale
import kotlin.math.round

/**
 * Local calculator for the search bar: `2+5*7`, `18% of 86`, and `18% tip on $86`.
 */
object Calculator {

    private val tipPattern = Regex(
        """(?:(?:i\s+need\s+to\s+calculate|calculate|calc|tip)\s+)?(\d+(?:\.\d+)?)\s*%\s*(?:tip\s*)?(?:on|of|for)?\s*\$?\s*(\d+(?:\.\d+)?)""",
        RegexOption.IGNORE_CASE
    )
    private val percentOfPattern = Regex(
        """\$?\s*(\d+(?:\.\d+)?)\s*%\s*(?:of)\s*\$?\s*(\d+(?:\.\d+)?)""",
        RegexOption.IGNORE_CASE
    )
    private val expressionPattern = Regex("""^[\d.\s()+\-*/%x×÷,]+$""")

    fun interpret(raw: String): Calculation? {
        val query = raw.trim()
            .replace(Regex("""^(?:what\s+is|whats|calculate|work\s+out)\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\bpercent\b""", RegexOption.IGNORE_CASE), "%")
            .replace(Regex("""\bdivided\s+by\b""", RegexOption.IGNORE_CASE), "/")
            .replace(Regex("""\bmultiplied\s+by\b|\btimes\b""", RegexOption.IGNORE_CASE), "*")
            .replace(Regex("""\bplus\b|\badded\s+to\b""", RegexOption.IGNORE_CASE), "+")
            .replace(Regex("""\bminus\b""", RegexOption.IGNORE_CASE), "-")
            .trim()
        if (query.isEmpty()) return null

        tipPattern.matchEntire(query)?.let { match ->
            val percent = match.groupValues[1].toDouble()
            val amount = match.groupValues[2].toDouble()
            val tip = amount * percent / 100.0
            val total = amount + tip
            return Calculation(
                expression = "${format(percent)}% of ${format(amount)}",
                value = format(tip),
                detail = "Tip ${format(tip)} · total ${format(total)}"
            )
        }

        percentOfPattern.find(query)?.let { match ->
            val percent = match.groupValues[1].toDouble()
            val amount = match.groupValues[2].toDouble()
            val result = amount * percent / 100.0
            return Calculation(
                expression = "${format(percent)}% of ${format(amount)}",
                value = format(result)
            )
        }

        val normalized = query
            .replace("×", "*")
            .replace("x", "*", ignoreCase = true)
            .replace("÷", "/")
            .replace(",", "")
            .replace(" ", "")
        if (normalized.length < 2 || !expressionPattern.matches(query.replace("×", "*").replace("÷", "/").replace("x", "*", ignoreCase = true))) {
            return null
        }
        if (!normalized.any { it.isDigit() } || !normalized.any { it in "+-*/%" }) return null
        val value = evaluate(normalized) ?: return null
        return Calculation(expression = query, value = format(value))
    }

    fun evaluate(input: String): Double? {
        return try {
            val parser = Parser(input)
            val value = parser.parseExpression()
            if (!parser.done()) null else value
        } catch (_: Exception) {
            null
        }
    }

    private fun format(value: Double): String {
        val rounded = round(value * 1_000_000.0) / 1_000_000.0
        return if (rounded % 1.0 == 0.0) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.US, "%.4f", rounded).trimEnd('0').trimEnd('.')
        }
    }

    private class Parser(private val source: String) {
        private var index = 0

        fun done(): Boolean = index >= source.length

        fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                value = when (peek()) {
                    '+' -> {
                        next()
                        value + parseTerm()
                    }
                    '-' -> {
                        next()
                        value - parseTerm()
                    }
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double {
            var value = parseUnary()
            while (true) {
                value = when (peek()) {
                    '*' -> {
                        next()
                        value * parseUnary()
                    }
                    '/' -> {
                        next()
                        value / parseUnary()
                    }
                    '%' -> {
                        next()
                        value % parseUnary()
                    }
                    else -> return value
                }
            }
        }

        private fun parseUnary(): Double {
            return when (peek()) {
                '+' -> {
                    next()
                    parseUnary()
                }
                '-' -> {
                    next()
                    -parseUnary()
                }
                else -> parseFactor()
            }
        }

        private fun parseFactor(): Double {
            if (peek() == '(') {
                next()
                val value = parseExpression()
                if (peek() == ')') next() else error("expected )")
                return value
            }
            return parseNumber()
        }

        private fun parseNumber(): Double {
            val start = index
            while (peek()?.isDigit() == true) next()
            if (peek() == '.') {
                next()
                while (peek()?.isDigit() == true) next()
            }
            if (start == index) error("expected number")
            return source.substring(start, index).toDouble()
        }

        private fun peek(): Char? = source.getOrNull(index)

        private fun next() {
            index++
        }
    }
}

data class Calculation(
    val expression: String,
    val value: String,
    val detail: String? = null
)
