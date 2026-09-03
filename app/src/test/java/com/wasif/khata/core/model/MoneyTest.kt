package com.wasif.khata.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

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

    @Test
    fun `parse accepts negative amounts`() {
        assertEquals(Money(-123456), Money.parse("-1234.56"))
        assertEquals(Money(-123456), Money.parse("-৳1,234.56"))
        assertEquals(Money(-1250), Money.parse("-12.5"))
    }

    @Test
    fun `format and parse round-trip for negative amounts`() {
        val original = Money(-987654321)
        assertEquals(original, Money.parse(original.format()))
    }

    @Test
    fun `parse rejects a taka part too large for Long`() {
        assertNull(Money.parse("99999999999999999999.00"))
    }

    @Test
    fun `parse rejects a taka value whose minor-unit conversion overflows`() {
        // 92233720368547758.07 is exactly Long.MAX_VALUE in minor units (the true
        // boundary), so bump the taka part by one to actually push past it.
        assertNull(Money.parse("92233720368547759.07"))
    }

    @Test
    fun `parse rejects a lone or doubled minus sign`() {
        assertNull(Money.parse("-"))
        assertNull(Money.parse("--123"))
    }

    @Test
    fun `parse reads a sum written without its leading zero`() {
        // EBL sends "Balance is BDT .56" and "debited with BDT .06".
        assertEquals(56L, Money.parse(".56")?.minor)
        assertEquals(6L, Money.parse(".06")?.minor)
        assertEquals(-6L, Money.parse("-.06")?.minor)
    }

    @Test
    fun `parse still rejects a bare decimal point`() {
        assertNull(Money.parse("."))
    }
}
