package com.wasif.khata.core.sms

import com.wasif.khata.core.data.dao.RawMessageDao
import com.wasif.khata.core.time.KhataClock
import javax.inject.Inject
import javax.inject.Singleton

data class IngestSummary(
    val total: Int = 0,
    val recorded: Int = 0,
    val updated: Int = 0,
    val ignored: Int = 0,
    val unmatched: Int = 0,
    val duplicates: Int = 0,
) {
    fun plus(result: IngestResult): IngestSummary = when (result) {
        is IngestResult.Recorded -> copy(total = total + 1, recorded = recorded + 1)
        is IngestResult.Updated -> copy(total = total + 1, updated = updated + 1)
        IngestResult.Ignored -> copy(total = total + 1, ignored = ignored + 1)
        IngestResult.Unmatched -> copy(total = total + 1, unmatched = unmatched + 1)
        IngestResult.Duplicate -> copy(total = total + 1, duplicates = duplicates + 1)
    }
}

/**
 * Reads the whole inbox through the pipeline. Idempotent by construction: a repeat
 * run inserts no new raw messages, so it reports duplicates rather than doubling
 * anything.
 */
@Singleton
class BackfillUseCase @Inject constructor(
    private val source: MessageSource,
    private val pipeline: IngestionPipeline,
) {
    suspend operator fun invoke(): IngestSummary =
        source.readAll().fold(IngestSummary()) { summary, message ->
            summary.plus(pipeline.ingest(message.sender, message.body, message.receivedAt))
        }
}

/**
 * Re-runs the current rule set over every stored message. This is the repair
 * mechanism for a system with no review gate: a rule added months later retroactively
 * fixes history, and because writes key off the raw message, re-running cannot
 * duplicate what it already recorded.
 */
@Singleton
class ReparseUseCase @Inject constructor(
    private val rawMessageDao: RawMessageDao,
    private val pipeline: IngestionPipeline,
    private val clock: KhataClock,
) {
    suspend operator fun invoke(): IngestSummary {
        val now = clock.now()
        return rawMessageDao.allForReparse().fold(IngestSummary()) { summary, raw ->
            summary.plus(pipeline.process(raw.id, raw.sender, raw.body, raw.receivedAt, now))
        }
    }
}
