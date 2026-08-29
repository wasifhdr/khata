package com.wasif.khata.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

interface KhataClock {
    fun now(): Long
}

class SystemKhataClock @Inject constructor() : KhataClock {
    override fun now(): Long = System.currentTimeMillis()
}

val DHAKA: ZoneId = ZoneId.of("Asia/Dhaka")

fun Long.toDhakaLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(DHAKA).toLocalDate()

// Month boundaries are computed in Dhaka, never UTC. At 00:30 Dhaka on the
// first, UTC is still 18:30 on the last of the previous month -- computing
// there would put the first six hours of every month in the wrong one, and
// every month-to-date figure would be wrong along with it.
fun Long.dhakaMonthStart(): Long =
    Instant.ofEpochMilli(this)
        .atZone(DHAKA)
        .toLocalDate()
        .withDayOfMonth(1)
        .atStartOfDay(DHAKA)
        .toInstant()
        .toEpochMilli()

fun Long.dhakaNextMonthStart(): Long =
    Instant.ofEpochMilli(this)
        .atZone(DHAKA)
        .toLocalDate()
        .withDayOfMonth(1)
        .plusMonths(1)
        .atStartOfDay(DHAKA)
        .toInstant()
        .toEpochMilli()

/**
 * Dhaka is UTC+6 with no daylight saving, so shifting by six hours before
 * integer-dividing by a day yields the local calendar day directly. SQLite gets
 * the same arithmetic inline, which is why the grouped day-total query needs no
 * timezone function.
 */
const val DHAKA_OFFSET_MILLIS: Long = 6 * 60 * 60 * 1000L
private const val DAY_MILLIS: Long = 24 * 60 * 60 * 1000L

fun Long.toDhakaDayIndex(): Long = (this + DHAKA_OFFSET_MILLIS) / DAY_MILLIS

fun Long.dhakaDayIndexToLocalDate(): LocalDate =
    LocalDate.ofEpochDay(this)
