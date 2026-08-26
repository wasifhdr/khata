package com.wasif.khata.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    @Test
    fun `ofTaka combines taka and paisa into minor units`() {
        assertEquals(123456L, Money.ofTaka(1234, 56).minor)
        assertEquals(500L, Money.ofTaka(5).minor)
    }

    @Test
    fun `addition and subtraction operate on minor units`() {
        assertEquals(Money(300), Money(100) + Money(200))
        assertEquals(Money(-100), Money(100) - Money(200))
    }

    @Test
    fun `negation and abs`() {
        assertEquals(Money(-250), -Money(250))
        assertEquals(Money(250), Money(-250).abs())
    }

    @Test
    fun `comparison orders by minor units`() {
        assertTrue(Money(100) < Money(200))
        assertEquals(Money(0), Money.ZERO)
        assertTrue(Money.ZERO.isZero)
    }

    @Test
    fun `format renders two decimal places with symbol and grouping`() {
        assertEquals("৳1,234.56", Money(123456).format())
        assertEquals("৳0.00", Money.ZERO.format())
        assertEquals("৳5.00", Money(500).format())
        assertEquals("৳12,345,678.90", Money(1234567890).format())
    }

    @Test
    fun `format renders negatives with the sign before the symbol`() {
        assertEquals("-৳1,234.56", Money(-123456).format())
    }

    @Test
    fun `format can omit the symbol`() {
        assertEquals("1,234.56", Money(123456).format(withSymbol = false))
    }

    @Test
    fun `parse accepts plain digits, decimals, commas, and the symbol`() {
        assertEquals(Money(123456), Money.parse("1234.56"))
        assertEquals(Money(123456), Money.parse("1,234.56"))
        assertEquals(Money(123456), Money.parse("৳1,234.56"))
        assertEquals(Money(123456), Money.parse("Tk 1,234.56"))
        assertEquals(Money(50000), Money.parse("500"))
    }

    @Test
    fun `parse pads a single decimal digit rather than truncating`() {
        assertEquals(Money(1250), Money.parse("12.5"))
    }

    @Test
    fun `parse rejects malformed input instead of guessing`() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("12.345"))
        assertNull(Money.parse("1.2.3"))
    }

    @Test
    fun `format and parse round-trip`() {
        val original = Money(987654321)
        assertEquals(original, Money.parse(original.format()))
    }
}
