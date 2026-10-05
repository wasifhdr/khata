package com.wasif.khata.core.sms

import com.wasif.khata.core.data.dao.RawMessageDao
import com.wasif.khata.core.data.entity.RawMessageEntity
import com.wasif.khata.core.model.RawMessageStatus
import com.wasif.khata.core.sms.ai.RuleDrafter
import com.wasif.khata.core.sms.ai.RuleSuggester
import com.wasif.khata.core.time.KhataClock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last

data class IngestSummary(
    val total: Int = 0,
    val recorded: Int = 0,
    val updated: Int = 0,
    val ignored: Int = 0,
    val unmatched: Int = 0,
    val duplicates: Int = 0,
    val notMine: Int = 0,
) {
    fun plus(result: IngestResult): IngestSummary = when (result) {
        is IngestResult.Recorded -> copy(total = total + 1, recorded = recorded + 1)
        is IngestResult.Updated -> copy(total = total + 1, updated = updated + 1)
        IngestResult.Ignored -> copy(total = total + 1, ignored = ignored + 1)
        IngestResult.Unmatched -> copy(total = total + 1, unmatched = unmatched + 1)
        IngestResult.Duplicate -> copy(total = total + 1, duplicates = duplicates + 1)
        IngestResult.NotMine -> copy(total = total + 1, notMine = notMine + 1)
    }
}

/** How far a whole-inbox pass has got. Shared by backfill and reparse. */
data class IngestProgress(
    val processed: Int,
    val total: Int,
    val summary: IngestSummary,
) {
    val isComplete: Boolean get() = processed >= total
    val fraction: Float get() = if (total == 0) 1f else processed.toFloat() / total
}

/**
 * Reads the whole inbox through the pipeline. Idempotent by construction: a repeat
 * run inserts no new raw messages, so it reports duplicates rather than doubling
 * anything.
 *
 * Emits progress rather than returning once, because a multi-year inbox is thousands
 * of messages and a silent block reads as a hang.
 */
@Singleton
class BackfillUseCase @Inject constructor(
    private val source: MessageSource,
    private val pipeline: IngestionPipeline,
    private val suggester: RuleSuggester,
    private val drafter: RuleDrafter?,
    private val rawMessageDao: RawMessageDao?,
    private val smsStartFrom: SmsStartFrom = SmsStartFrom { null },
) {
    constructor(
        source: MessageSource,
        pipeline: IngestionPipeline,
        smsStartFrom: SmsStartFrom = SmsStartFrom { null },
    ) : this(source, pipeline, RuleSuggester { _, _ -> null }, null, null, smsStartFrom)

    fun run(): Flow<IngestProgress> = flow {
        val since = smsStartFrom()
        val messages = source.readAll().let { all ->
            if (since != null) all.filter { it.receivedAt >= since } else all
        }
        var summary = IngestSummary()
        val failedShapes = mutableSetOf<String>()
        emit(IngestProgress(processed = 0, total = messages.size, summary = summary))

        messages.forEachIndexed { index, message ->
            var result = pipeline.ingest(
                sender = message.sender,
                body = message.body,
                receivedAt = message.receivedAt,
                teachOnMiss = false,
            )
            // Inline teach during historical scan: the first instance of an unknown
            // format drafts and saves a rule immediately so every subsequent message
            // of that format in this pass matches locally without another API call.
            if (result is IngestResult.Unmatched && drafter != null && rawMessageDao != null) {
                val shape = "${message.sender}:${deriveIgnorePattern(message.body) ?: message.body}"
                if (failedShapes.add(shape)) {
                    val drafted = suggester(message.sender, message.body)
                    if (drafted != null && drafter.store(drafted, message.sender, message.body)) {
                        val raw = rawMessageDao.allByStatus(RawMessageStatus.UNMATCHED)
                            .lastOrNull { it.sender == message.sender && it.body == message.body }
                        if (raw != null) {
                            result = pipeline.process(raw.id, raw.sender, raw.body, raw.receivedAt)
                        }
                    }
                }
            }
            summary = summary.plus(result)
            emit(IngestProgress(processed = index + 1, total = messages.size, summary = summary))
        }
    }

    /** Convenience for callers that only want the outcome. */
    suspend operator fun invoke(): IngestSummary = run().last().summary
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
    private val smsStartFrom: SmsStartFrom = SmsStartFrom { null },
) {
    /**
     * Emits progress for the same reason backfill does: this walks every stored
     * message, which on a real phone is thousands, and a caller that only awaits
     * the answer shows a button reading "Saving..." for the better part of a
     * minute with no sign it is doing anything.
     */
    fun run(): Flow<IngestProgress> = pass { rawMessageDao.allForReparse() }

    /**
     * The same pass over unmatched messages alone.
     *
     * For a rule that cannot outrank an existing one -- RuleEngine sorts ascending, so
     * a rule taking max+1 is the lowest -- nothing already parsed can change, and
     * walking the whole inbox to discover that costs thousands of messages on a real
     * phone. Hiding one message should not re-read years of them.
     */
    fun runUnmatched(): Flow<IngestProgress> =
        pass { rawMessageDao.allByStatus(RawMessageStatus.UNMATCHED) }

    private fun pass(load: suspend () -> List<RawMessageEntity>): Flow<IngestProgress> = flow {
        val now = clock.now()
        val since = smsStartFrom()
        val messages = load().let { all ->
            if (since != null) all.filter { it.receivedAt >= since } else all
        }
        var summary = IngestSummary()
        emit(IngestProgress(processed = 0, total = messages.size, summary = summary))

        messages.forEachIndexed { index, raw ->
            summary = summary.plus(pipeline.process(raw.id, raw.sender, raw.body, raw.receivedAt, now))
            emit(IngestProgress(processed = index + 1, total = messages.size, summary = summary))
        }
    }

    /** Convenience for callers that only want the outcome. */
    suspend operator fun invoke(): IngestSummary = run().last().summary
}
