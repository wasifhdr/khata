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
