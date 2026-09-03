package com.wasif.khata.core.sms

import androidx.work.Data
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IngestionWorkTest {

    @Test
    fun `progress survives a trip through Data intact`() {
        val progress = IngestProgress(
            processed = 41,
            total = 120,
            summary = IngestSummary(
                total = 41,
                recorded = 12,
                updated = 3,
                ignored = 9,
                unmatched = 7,
                duplicates = 6,
                notMine = 4,
            ),
        )

        assertEquals(progress, progress.toData().toIngestProgress())
    }

    // The summary's own `total` and the progress's `total` are different numbers
    // that would collide under one key -- a backfill would then report its
    // denominator as its processed count and the bar would sit full from the
    // first message.
    @Test
    fun `the two totals do not collide`() {
        val progress = IngestProgress(
            processed = 1,
            total = 999,
            summary = IngestSummary(total = 1),
        )

        val restored = progress.toData().toIngestProgress()!!

        assertEquals(999, restored.total)
        assertEquals(1, restored.summary.total)
    }

    @Test
    fun `data carrying no progress reads as none rather than as zero`() {
        // A worker that has not called setProgress yet emits empty Data. Zero
        // progress and no progress are different answers: one draws a bar at the
        // start, the other draws nothing.
        assertNull(Data.EMPTY.toIngestProgress())
    }
}
