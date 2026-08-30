package com.wasif.khata.core.time

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class KhataClockTest {

    @Test
    fun `month start is midnight Dhaka on the first`() {
        val mid = Instant.parse("2026-08-28T09:41:00Z").toEpochMilli()
        // Dhaka is UTC+6, so 1 August 00:00 Dhaka is 31 July 18:00 UTC.
        val expected = Instant.parse("2026-07-31T18:00:00Z").toEpochMilli()

        assertEquals(expected, mid.dhakaMonthStart())
    }

    @Test
    fun `next month start is exclusive and rolls the year`() {
        val dec = Instant.parse("2026-12-15T00:00:00Z").toEpochMilli()
        val expected = Instant.parse("2026-12-31T18:00:00Z").toEpochMilli()

        assertEquals(expected, dec.dhakaNextMonthStart())
    }

    @Test
    fun `an instant just after Dhaka midnight belongs to the new month`() {
        // 1 September 00:30 Dhaka is 31 August 18:30 UTC. Computing the month
        // in UTC would put this in August, and every month-to-date figure would
        // be wrong for the first six hours of every month.
        val earlySep = Instant.parse("2026-08-31T18:30:00Z").toEpochMilli()

        assertEquals(Instant.parse("2026-08-31T18:00:00Z").toEpochMilli(), earlySep.dhakaMonthStart())
    }

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

    @Test
    fun `the Dhaka day index rolls at Dhaka midnight, not UTC midnight`() {
        // 23:59 Dhaka on 28 August is 17:59 UTC the same day.
        val lateEvening = Instant.parse("2026-08-28T17:59:00Z").toEpochMilli()
        // 00:01 Dhaka on 29 August is 18:01 UTC on the 28th.
        val justAfterMidnight = Instant.parse("2026-08-28T18:01:00Z").toEpochMilli()

        assertEquals(
            lateEvening.toDhakaDayIndex() + 1,
            justAfterMidnight.toDhakaDayIndex(),
        )
    }

    @Test
    fun `the day index round-trips to the Dhaka calendar date`() {
        val millis = Instant.parse("2026-08-28T18:01:00Z").toEpochMilli()

        assertEquals(
            LocalDate.of(2026, 8, 29),
            millis.toDhakaDayIndex().dhakaDayIndexToLocalDate(),
        )
    }

    @Test
    fun `two instants on the same Dhaka day share an index`() {
        val morning = Instant.parse("2026-08-29T02:00:00Z").toEpochMilli()
        val evening = Instant.parse("2026-08-29T17:00:00Z").toEpochMilli()

        assertEquals(morning.toDhakaDayIndex(), evening.toDhakaDayIndex())
    }
}
