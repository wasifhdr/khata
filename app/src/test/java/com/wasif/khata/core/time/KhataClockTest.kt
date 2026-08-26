package com.wasif.khata.core.time

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class KhataClockTest {

    @Test
    fun `an evening UTC instant is already the next calendar day in Dhaka`() {
        val millis = Instant.parse("2026-08-26T20:30:00Z").toEpochMilli()

        assertEquals(LocalDate.of(2026, 8, 27), millis.toDhakaLocalDate())
    }

    @Test
    fun `a morning UTC instant is the same calendar day in Dhaka`() {
        val millis = Instant.parse("2026-08-26T06:00:00Z").toEpochMilli()

        assertEquals(LocalDate.of(2026, 8, 26), millis.toDhakaLocalDate())
    }

    @Test
    fun `the instant at exactly 18-00Z is the first moment of the next Dhaka day`() {
        val millis = Instant.parse("2026-08-26T18:00:00Z").toEpochMilli()

        assertEquals(LocalDate.of(2026, 8, 27), millis.toDhakaLocalDate())
    }

    @Test
    fun `one second before 18-00Z is still the previous Dhaka day`() {
        val millis = Instant.parse("2026-08-26T17:59:59Z").toEpochMilli()

        assertEquals(LocalDate.of(2026, 8, 26), millis.toDhakaLocalDate())
    }

    @Test
    fun `a fixed clock returns exactly what it was given`() {
        val clock = object : KhataClock {
            override fun now(): Long = 42L
        }

        assertEquals(42L, clock.now())
    }
}
