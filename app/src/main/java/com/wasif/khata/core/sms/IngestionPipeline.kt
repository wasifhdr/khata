package com.wasif.khata.core.sms

import androidx.room.withTransaction
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.dao.AccountDao
import com.wasif.khata.core.data.dao.MerchantDao
import com.wasif.khata.core.data.dao.ParsingRuleDao
import com.wasif.khata.core.data.dao.RawMessageDao
import com.wasif.khata.core.data.dao.TransactionDao
import com.wasif.khata.core.data.entity.AccountEntity
import com.wasif.khata.core.data.entity.MerchantAliasEntity
import com.wasif.khata.core.data.entity.MerchantEntity
import com.wasif.khata.core.data.entity.RawMessageEntity
import com.wasif.khata.core.data.entity.TransactionEntity
import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.model.RawMessageStatus
import com.wasif.khata.core.model.RuleKind
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionKind
import com.wasif.khata.core.model.TransactionSource
import com.wasif.khata.core.time.KhataClock
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface IngestResult {
    data class Recorded(val transactionId: Long) : IngestResult
    data class Updated(val transactionId: Long) : IngestResult
    data object Ignored : IngestResult
    data object Unmatched : IngestResult
    data object Duplicate : IngestResult
}

@Singleton
class IngestionPipeline @Inject constructor(
    private val db: KhataDatabase,
    private val rawMessageDao: RawMessageDao,
    private val parsingRuleDao: ParsingRuleDao,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val merchantDao: MerchantDao,
    private val engine: RuleEngine,
    private val pairing: TransferPairing,
    private val clock: KhataClock,
) {

    suspend fun ingest(sender: String, body: String, receivedAt: Long): IngestResult {
        val now = clock.now()

        val rawId = rawMessageDao.insertIgnoringDuplicate(
            RawMessageEntity(
                uuid = UUID.randomUUID().toString(),
                sender = sender,
                body = body,
                receivedAt = receivedAt,
                bodyHash = sha256(body),
                status = RawMessageStatus.PENDING,
                matchedRuleId = null,
                createdAt = now,
                updatedAt = now,
            )
        )
        if (rawId == -1L) return IngestResult.Duplicate

        return process(rawId, sender, body, receivedAt, now)
    }

    /** Shared by [ingest] and reparse: everything after the raw message exists. */
    suspend fun process(
        rawId: Long,
        sender: String,
        body: String,
        receivedAt: Long,
        now: Long = clock.now(),
    ): IngestResult {
        return when (val outcome = engine.parse(sender, body, parsingRuleDao.enabled())) {
            is ParseOutcome.Ignored -> {
                rawMessageDao.markStatus(rawId, RawMessageStatus.IGNORED, outcome.ruleId, now)
                IngestResult.Ignored
            }

            ParseOutcome.Unmatched -> {
                rawMessageDao.markStatus(rawId, RawMessageStatus.UNMATCHED, null, now)
                IngestResult.Unmatched
            }

            is ParseOutcome.Parsed -> write(rawId, sender, outcome.value, receivedAt, now)
        }
    }

    private suspend fun write(
        rawId: Long,
        sender: String,
        parsed: ParsedMessage,
        receivedAt: Long,
        now: Long,
    ): IngestResult {
        val account = resolveAccount(sender, parsed.accountTail)
        if (account == null) {
            // A transaction with no account cannot be balanced, which is worse than
            // no transaction at all.
            rawMessageDao.markStatus(rawId, RawMessageStatus.UNMATCHED, parsed.ruleId, now)
            return IngestResult.Unmatched
        }

        return db.withTransaction {
            val merchant = resolveMerchant(parsed.merchant, now)
            // providerTxnId catches the reserved/successful twins, which are two
            // different raw messages. rawMessageId catches reparse of one message,
            // which is the only dedup key EBL offers since it sends no TrxID.
            val existing = parsed.providerTxnId?.let { transactionDao.findByProviderTxnId(it) }
                ?: transactionDao.findByRawMessageId(rawId)

            // Reverse the superseded row's balance effect before applying the new one,
            // or the "reserved" then "successful" pair double-counts.
            if (existing != null) {
                accountDao.adjustBalance(existing.accountId, -existing.signedMinor(), now)
            }

            val rowId = transactionDao.upsert(
                TransactionEntity(
                    id = existing?.id ?: 0,
                    uuid = existing?.uuid ?: UUID.randomUUID().toString(),
                    accountId = account.id,
                    amountMinor = parsed.amount.minor,
                    direction = parsed.direction,
                    occurredAt = parsed.occurredAt ?: receivedAt,
                    merchantRaw = parsed.merchant,
                    merchantId = merchant?.id,
                    categoryId = merchant?.categoryId,
                    note = null,
                    source = TransactionSource.SMS,
                    confidence = if (merchant?.categoryId != null) Confidence.HIGH else Confidence.MEDIUM,
                    rawMessageId = rawId,
                    transferGroupId = existing?.transferGroupId,
                    feeMinor = parsed.feeMinor,
                    referenceNumber = parsed.providerTxnId,
                    providerTxnId = parsed.providerTxnId,
                    kind = parsed.kind.toTransactionKind(),
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                )
            )
            val transactionId = if (rowId == -1L) existing!!.id else rowId

            accountDao.adjustBalance(account.id, signedMinor(parsed.amount.minor, parsed.direction), now)
            parsed.balance?.let { accountDao.setReportedBalance(account.id, it.minor, now) }
            rawMessageDao.markStatus(rawId, RawMessageStatus.PARSED, parsed.ruleId, now)

            // Inside the same database transaction, so a half-formed pair is impossible.
            pairing.pair(transactionId)

            if (existing != null) IngestResult.Updated(transactionId) else IngestResult.Recorded(transactionId)
        }
    }

    private suspend fun resolveAccount(sender: String, tail: String?): AccountEntity? {
        val accounts = accountDao.getAll()
        if (tail != null) {
            accounts.firstOrNull { accountTail(it.smsIdentifiers) == tail }?.let { return it }
        }
        return accounts.firstOrNull { account ->
            account.smsIdentifiers.split(",")
                .any { it.isNotBlank() && it.trim().equals(sender, ignoreCase = true) }
        }
    }

    private suspend fun resolveMerchant(raw: String?, now: Long): MerchantEntity? {
        if (raw.isNullOrBlank()) return null
        merchantDao.findByAlias(raw)?.let { return it }

        val id = merchantDao.upsert(
            MerchantEntity(
                uuid = UUID.randomUUID().toString(),
                canonicalName = raw,
                // Left null rather than guessed; Plan 3's AI fallback fills it.
                categoryId = null,
                placeId = null,
                isUserConfirmed = false,
                createdAt = now,
                updatedAt = now,
            )
        )
        merchantDao.upsertAlias(
            MerchantAliasEntity(
                uuid = UUID.randomUUID().toString(),
                merchantId = id,
                rawText = raw,
                createdAt = now,
                updatedAt = now,
            )
        )
        return merchantDao.findById(id)
    }
}

private fun RuleKind.toTransactionKind(): TransactionKind = when (this) {
    RuleKind.LOAN_DISBURSEMENT -> TransactionKind.LOAN_DISBURSEMENT
    RuleKind.TRANSFER_IN, RuleKind.TRANSFER_OUT, RuleKind.ATM_WITHDRAWAL -> TransactionKind.TRANSFER
    RuleKind.FEE -> TransactionKind.FEE
    RuleKind.NORMAL, RuleKind.IGNORE -> TransactionKind.NORMAL
}

private fun signedMinor(amountMinor: Long, direction: TransactionDirection): Long =
    if (direction == TransactionDirection.DEBIT) -amountMinor else amountMinor

private fun TransactionEntity.signedMinor(): Long = signedMinor(amountMinor, direction)

private fun sha256(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }
