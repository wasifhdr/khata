package com.wasif.khata.core.sms

import com.wasif.khata.core.data.dao.TransactionDao
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.notify.TransferNotifier
import com.wasif.khata.core.time.KhataClock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

// Fifteen minutes covers both shapes seen in the corpus: an Own Account Transfer
// pair one second apart, and a cross-institution EBL-to-bKash pair minutes apart.
private const val WINDOW_MILLIS = 15L * 60L * 1000L

@Singleton
class TransferPairing @Inject constructor(
    private val transactionDao: TransactionDao,
    private val notifier: TransferNotifier,
    private val clock: KhataClock,
) {

    /** Returns the shared transferGroupId when a pair formed, or null when none did. */
    suspend fun pair(transactionId: Long): String? {
        val subject = transactionDao.findById(transactionId) ?: return null
        if (subject.transferGroupId != null) return null

        val opposite = when (subject.direction) {
            TransactionDirection.DEBIT -> TransactionDirection.CREDIT
            TransactionDirection.CREDIT -> TransactionDirection.DEBIT
        }

        val candidate = transactionDao.findPairCandidates(
            amountMinor = subject.amountMinor,
            notAccountId = subject.accountId,
            direction = opposite,
            fromMillis = subject.occurredAt - WINDOW_MILLIS,
            toMillis = subject.occurredAt + WINDOW_MILLIS,
        )
            .filter { it.id != subject.id }
            // Closest in time wins; the id break keeps re-runs deterministic.
            .minWithOrNull(compareBy({ abs(it.occurredAt - subject.occurredAt) }, { it.id }))
            ?: return null

        val group = UUID.randomUUID().toString()
        transactionDao.markAsTransfer(listOf(subject.id, candidate.id), group, clock.now())
        // markAsTransfer clears the review flag, but a notification already on screen
        // is not in the database. Pairing can happen up to fifteen minutes out, well
        // after the three-minute question was asked, and a question the owner can no
        // longer answer correctly is worse than never having asked it.
        notifier.retract(subject.id)
        notifier.retract(candidate.id)
        return group
    }
}
