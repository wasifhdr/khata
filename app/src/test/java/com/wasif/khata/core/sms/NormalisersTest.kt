package com.wasif.khata.core.sms

import com.wasif.khata.core.model.Money
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NormalisersTest {

    @Test
    fun `parseAmount accepts a space between unit and digits`() {
        assertEquals(Money(6000), parseAmount("BDT 60"))
        assertEquals(Money(1731600), parseAmount("BDT 17316"))
    }

    @Test
    fun `parseAmount accepts no space between unit and digits`() {
        assertEquals(Money(500000), parseAmount("BDT5000"))
    }

    @Test
    fun `parseAmount accepts thousands separators`() {
        assertEquals(Money(260000), parseAmount("Tk 2,600.00"))
        assertEquals(Money(102711), parseAmount("Tk 1,027.11"))
    }

    @Test
    fun `parseAmount accepts a period between unit and digits`() {
        assertEquals(Money(75000), parseAmount("Tk.750.00"))
    }

    @Test
    fun `parseAmount accepts a bare number with no unit`() {
        assertEquals(Money(5856), parseAmount("58.56"))
        assertEquals(Money(420), parseAmount("4.20"))
    }

    @Test
    fun `parseAmount handles one and two decimal places and zero`() {
        assertEquals(Money(500420), parseAmount("BDT 5004.2"))
        assertEquals(Money(0), parseAmount("Tk 0.00"))
        assertEquals(Money(290), parseAmount("Tk 2.90"))
    }

    @Test
    fun `parseAmount is case insensitive about the unit`() {
        assertEquals(Money(30928), parseAmount("TK 309.28"))
    }

    @Test
    fun `parseAmount rejects text with no number`() {
        assertNull(parseAmount("Thanks. EBL Helpline"))
        assertNull(parseAmount(""))
    }

    @Test
    fun `parseEblDateTime reads an uppercase month`() {
        assertEquals(
            Instant.parse("2026-09-01T12:46:08Z").toEpochMilli(),
            parseEblDateTime("01-SEP-26 06:46:08 PM"),
        )
    }

    @Test
    fun `parseEblDateTime reads a mixed case month`() {
        assertEquals(
            Instant.parse("2026-08-31T12:27:44Z").toEpochMilli(),
            parseEblDateTime("31-Aug-26 06:27:44 PM"),
        )
    }

    @Test
    fun `parseEblDateTime reads a midnight hour correctly`() {
        assertEquals(
            Instant.parse("2026-08-17T18:44:55Z").toEpochMilli(),
            parseEblDateTime("18-Aug-26 12:44:55 AM"),
        )
    }

    @Test
    fun `parseBkashDateTime reads a 24 hour timestamp`() {
        assertEquals(
            Instant.parse("2026-08-21T06:35:00Z").toEpochMilli(),
            parseBkashDateTime("21/08/2026 12:35"),
        )
        assertEquals(
            Instant.parse("2026-08-01T18:38:00Z").toEpochMilli(),
            parseBkashDateTime("02/08/2026 00:38"),
        )
    }

    @Test
    fun `datetime parsers reject the other providers format`() {
        assertNull(parseEblDateTime("21/08/2026 12:35"))
        assertNull(parseBkashDateTime("01-SEP-26 06:46:08 PM"))
    }

    @Test
    fun `accountTail normalises both masks of the same account`() {
        assertEquals("352", accountTail("115***352"))
        assertEquals("352", accountTail("115**9352"))
        assertEquals("286", accountTail("112***286"))
        assertEquals("286", accountTail("112**0286"))
    }

    @Test
    fun `accountTail rejects input with fewer than three digits`() {
        assertNull(accountTail("**"))
        assertNull(accountTail("A/C"))
        assertNull(accountTail(""))
    }
}
