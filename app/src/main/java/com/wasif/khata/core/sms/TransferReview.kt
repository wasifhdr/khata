package com.wasif.khata.core.sms

import com.wasif.khata.core.data.dao.TransactionDao
import com.wasif.khata.core.model.TransactionKind
import com.wasif.khata.core.time.KhataClock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The words a bank uses when money moves rather than is spent, matched case-
 * insensitively against the merchant text.
 *
 * "Recharge" is the near miss the list must not catch: EBL Skybanking Mobile Recharge
 * is spending, and it carries none of these.
 */
private val TRANSFER_SHAPES = listOf(
    "transfer",
    "npsb",
    "atm",
    "withdraw",
    "cash out",
    "send money",
)

/**
 * Whether a row is worth asking the owner about when no partner message arrives.
 *
 * Deliberately narrow. On a real 1,154-row ledger, asking about every unpaired row is
 * 10.7 questions a month against 1.4 for these shapes alone, and the difference is
 * whether the notification survives its first week.
 */
fun isTransferShaped(merchantRaw: String?): Boolean {
    val text = merchantRaw?.lowercase() ?: return false
    return TRANSFER_SHAPES.any { it in text }
}

/**
 * The three-minute check itself, kept out of the worker so it can be tested without
 * WorkManager. The worker is then only a way of arriving here late.
 */
@Singleton
class TransferReview @Inject constructor(
    private val transactionDao: TransactionDao,
    private val clock: KhataClock,
) {
    /**
     * Flags the row for review unless something already answered the question: it
     * paired, it was deleted, or it was never transfer-shaped to begin with.
     */
    suspend fun reviewIfUnpaired(transactionId: Long) {
        // findById already filters deletedAt IS NULL, which is what makes a deleted
        // row silent without a second check.
        val row = transactionDao.findById(transactionId) ?: return
        if (row.transferGroupId != null) return
        if (row.kind != TransactionKind.NORMAL) return
        if (!isTransferShaped(row.merchantRaw)) return

        transactionDao.setReviewPending(transactionId, pending = true, updatedAt = clock.now())
    }
}
