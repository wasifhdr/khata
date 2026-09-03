package com.wasif.khata.core.sms

import androidx.work.Data
import androidx.work.workDataOf

enum class IngestionMode { BACKFILL, REPARSE, MESSAGE, TEACH }

const val KEY_MODE = "mode"
const val KEY_SENDER = "sender"
const val KEY_BODY = "body"
const val KEY_RECEIVED_AT = "received_at"
const val KEY_RAW_ID = "raw_id"

private const val KEY_PROCESSED = "processed"
private const val KEY_TOTAL = "total"
private const val KEY_SUMMARY_TOTAL = "summary_total"
private const val KEY_RECORDED = "recorded"
private const val KEY_UPDATED = "updated"
private const val KEY_IGNORED = "ignored"
private const val KEY_UNMATCHED = "unmatched"
private const val KEY_DUPLICATES = "duplicates"
private const val KEY_NOT_MINE = "not_mine"

/** Present only once a pass has reported something, which is what absence means. */
private const val KEY_PRESENT = "present"

fun IngestProgress.toData(): Data = workDataOf(
    KEY_PRESENT to true,
    KEY_PROCESSED to processed,
    KEY_TOTAL to total,
    KEY_SUMMARY_TOTAL to summary.total,
    KEY_RECORDED to summary.recorded,
    KEY_UPDATED to summary.updated,
    KEY_IGNORED to summary.ignored,
    KEY_UNMATCHED to summary.unmatched,
    KEY_DUPLICATES to summary.duplicates,
    KEY_NOT_MINE to summary.notMine,
)

/**
 * Null when the Data carries no progress at all -- a worker that has been enqueued
 * but has not yet reported. Zero progress and no progress draw differently, so they
 * must not collapse into each other here.
 */
fun Data.toIngestProgress(): IngestProgress? {
    if (!getBoolean(KEY_PRESENT, false)) return null
    return IngestProgress(
        processed = getInt(KEY_PROCESSED, 0),
        total = getInt(KEY_TOTAL, 0),
        summary = IngestSummary(
            total = getInt(KEY_SUMMARY_TOTAL, 0),
            recorded = getInt(KEY_RECORDED, 0),
            updated = getInt(KEY_UPDATED, 0),
            ignored = getInt(KEY_IGNORED, 0),
            unmatched = getInt(KEY_UNMATCHED, 0),
            duplicates = getInt(KEY_DUPLICATES, 0),
            notMine = getInt(KEY_NOT_MINE, 0),
        ),
    )
}
