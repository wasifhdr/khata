package com.wasif.khata.core.model

import kotlin.math.absoluteValue

@JvmInline
value class Money(val minor: Long) : Comparable<Money> {

    val isZero: Boolean get() = minor == 0L

    operator fun plus(other: Money) = Money(minor + other.minor)

    operator fun minus(other: Money) = Money(minor - other.minor)

    operator fun unaryMinus() = Money(-minor)

    fun abs() = Money(minor.absoluteValue)

    override fun compareTo(other: Money) = minor.compareTo(other.minor)

    fun format(withSymbol: Boolean = true): String {
        val sign = if (minor < 0) "-" else ""
        val magnitude = minor.absoluteValue
        val paisa = (magnitude % 100).toString().padStart(2, '0')
        val body = "${groupIntegerPart(magnitude / 100)}.$paisa"
        return if (withSymbol) "$sign$SYMBOL$body" else "$sign$body"
    }

    companion object {
        const val SYMBOL = "৳"

        val ZERO = Money(0)

        fun ofTaka(taka: Long, paisa: Int = 0) = Money(taka * 100 + paisa)

        fun parse(input: String): Money? {
            val cleaned = input.replace(SYMBOL, "")
                .replace("Tk", "", ignoreCase = true)
                .replace(",", "")
                .replace(" ", "")
                .trim()
            if (cleaned.isEmpty()) return null

            val negative = cleaned.startsWith("-")
            val unsigned = cleaned.removePrefix("-")

            val parts = unsigned.split(".")
            if (parts.size > 2) return null

            val takaPart = parts[0]
            if (takaPart.isEmpty() || !takaPart.all { it.isDigit() }) return null

            val paisaPart = when {
                parts.size == 1 -> "00"
                // A single decimal digit means tenths: "12.5" is 12.50, never 12.05.
                parts[1].length == 1 -> parts[1] + "0"
                parts[1].length == 2 -> parts[1]
                else -> return null
            }
            if (!paisaPart.all { it.isDigit() }) return null

            val magnitude = takaPart.toLong() * 100 + paisaPart.toLong()
            return Money(if (negative) -magnitude else magnitude)
        }

        private fun groupIntegerPart(value: Long): String {
            val digits = value.toString()
            if (digits.length <= 3) return digits
            return digits.reversed().chunked(3).joinToString(",").reversed()
        }
    }
}
