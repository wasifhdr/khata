package com.wasif.khata.core.sms

import com.wasif.khata.core.model.Money
import com.wasif.khata.core.time.DHAKA
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.util.Locale

// One matcher for all four shapes the providers actually emit: "BDT 60",
// "BDT5000", "Tk 2,600.00", and "Tk.750.00".
private val AMOUNT = Regex(
    """(?:BDT|TK)?\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
    RegexOption.IGNORE_CASE,
)

fun parseAmount(text: String): Money? {
    val digits = AMOUNT.find(text)?.groupValues?.get(1) ?: return null
    return Money.parse(digits.replace(",", ""))
}

// parseCaseInsensitive because EBL writes the month as SEP in transfer messages
// and Aug in card messages, for the same field.
private val EBL_FORMAT: DateTimeFormatter = DateTimeFormatterBuilder()
    .parseCaseInsensitive()
    .appendPattern("dd-MMM-yy hh:mm:ss a")
    .toFormatter(Locale.ENGLISH)

private val BKASH_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ENGLISH)

private fun LocalDateTime.toDhakaMillis(): Long =
    atZone(DHAKA).toInstant().toEpochMilli()

fun parseEblDateTime(text: String): Long? = try {
    LocalDateTime.parse(text.trim(), EBL_FORMAT).toDhakaMillis()
} catch (e: DateTimeParseException) {
    null
}

fun parseBkashDateTime(text: String): Long? = try {
    LocalDateTime.parse(text.trim(), BKASH_FORMAT).toDhakaMillis()
} catch (e: DateTimeParseException) {
    null
}

// Both maskings of one account expose the last three digits and nothing else in
// common: 115***352 and 115**9352 are the same account.
fun accountTail(masked: String): String? =
    masked.filter { it.isDigit() }.takeIf { it.length >= 3 }?.takeLast(3)
