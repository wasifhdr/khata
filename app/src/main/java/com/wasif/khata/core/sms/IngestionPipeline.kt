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
import com.wasif.khata.core.data.entity.signedMinor
import com.wasif.khata.core.model.AccountType
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

/**
 * Whether this account's identifiers name the sender outright, as bKash's do.
 * EBL's identifiers are account tails that only appear in the body, so an EBL
 * message says nothing about which account it is until the tail is captured --
 * which is why a rule for one has to label the account.
 */
fun namesSender(smsIdentifiers: String, sender: String): Boolean =
    smsIdentifiers.split(",").any { it.isNotBlank() && it.trim().equals(sender, ignoreCase = true) }

sealed interface IngestResult {
    data class Recorded(val transactionId: Long) : IngestResult
    data class Updated(val transactionId: Long) : IngestResult
    data object Ignored : IngestResult
    data object Unmatched : IngestResult
    data object Duplicate : IngestResult

    /**
     * Sender no rule claims. Not stored at all, so it is neither a transaction nor
     * something to review -- distinct from [Ignored], which a rule deliberately
     * matched and discarded.
     */
    data object NotMine : IngestResult
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
    // Defaulted to a no-op so the many tests that build a pipeline directly are not
    // all about the AI. The real binding decides whether a key is set.
    private val teach: TeachRequest = TeachRequest {},
) {

    suspend fun ingest(sender: String, body: String, receivedAt: Long): IngestResult {
        val now = clock.now()

        // Before the message is stored, not after. On a real inbox 84% of messages
        // are from senders no rule claims -- friends, OTPs, operator promos -- and
        // keeping them would put the whole of someone's SMS history in this
        // database while burying the handful worth reviewing.
        //
        // ponytail: a sender no rule claims can never be taught either, so a new
        // bank needs a seeded rule before its messages are seen. Two banks and a
        // wallet is the whole world here; revisit if adding a bank stops being a
        // code change.
        if (!engine.claimsSender(sender, parsingRuleDao.allIncludingDisabled())) return IngestResult.NotMine

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

        val result = process(rawId, sender, body, receivedAt, now)

        // Only from here, never from process(): process is also the reparse path, and
        // a backfill over years of messages would otherwise fire hundreds of requests
        // before the user had seen a screen. Whether a key is set is the binding's
        // question, not this one's.
        if (result is IngestResult.Unmatched) teach(rawId)
        return result
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
                    counterparty = null,
                    source = TransactionSource.SMS,
                    // Not whether the merchant has a category: filing something is a
                    // separate question from trusting it, and a merchant nobody has
                    // filed yet still produced a rule-matched amount, direction and
                    // account. What is worth a second look is a row whose date had to
                    // be guessed -- it lands in whatever month the message happened to
                    // arrive in, which is the one error that moves a monthly total --
                    // or one with no merchant text at all, where nothing on the row
                    // says what it was.
                    confidence = if (parsed.occurredAt == null || parsed.merchant.isNullOrBlank()) {
                        Confidence.MEDIUM
                    } else {
                        Confidence.HIGH
                    },
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

            // Only movements after the last statement change the balance. Anything
            // older is already inside the figure the bank quoted, so applying it
            // would count it twice -- which is what a reparse of six years of
            // history does otherwise.
            val occurredAt = parsed.occurredAt ?: receivedAt
            val alreadyInStatement = account.reportedBalanceAt?.let { occurredAt <= it } == true
            if (!alreadyInStatement) {
                accountDao.adjustBalance(account.id, signedMinor(parsed.amount.minor, parsed.direction), now)
            }
            // The message's own time, not the moment it was read: a backfill
            // processes six years in one pass and every statement would otherwise
            // carry the same timestamp, making "newer" meaningless.
            parsed.balance?.let {
                accountDao.applyStatedBalance(account.id, it.minor, occurredAt, now)
            }
            rawMessageDao.markStatus(rawId, RawMessageStatus.PARSED, parsed.ruleId, now)

            // Inside the same database transaction, so a half-formed pair is impossible.
            if (parsed.kind == RuleKind.ATM_WITHDRAWAL) {
                depositIntoCash(transactionId, parsed, occurredAt, now)
            } else {
                pairing.pair(transactionId)
            }

            if (existing != null) IngestResult.Updated(transactionId) else IngestResult.Recorded(transactionId)
        }
    }

    /**
     * Cash out of an ATM or an agent is not spending -- it is the same money in a
     * different pocket. Without the other half it left the ledger entirely: on a
     * real phone, 31 withdrawals worth Tk 162,100 debited the bank and arrived
     * nowhere, and every one of them was counted as money spent.
     *
     * The pair is written explicitly rather than left to [TransferPairing], whose
     * time window would not know these two belong together.
     */
    private suspend fun depositIntoCash(
        withdrawalId: Long,
        parsed: ParsedMessage,
        occurredAt: Long,
        now: Long,
    ) {
        val accounts = accountDao.getAll()
        val cash = accounts.firstOrNull { it.type == AccountType.CASH } ?: return
        val withdrawal = transactionDao.findById(withdrawalId) ?: return
        val from = accounts.firstOrNull { it.id == withdrawal.accountId }?.name ?: "your account"

        // Derived from the withdrawal, so re-reading the same message finds this
        // row again instead of paying the cash in a second time.
        val uuid = "cash-${withdrawal.uuid}"
        val existing = transactionDao.findByUuid(uuid)

        val cashId = transactionDao.upsert(
            TransactionEntity(
                id = existing?.id ?: 0,
                uuid = uuid,
                accountId = cash.id,
                amountMinor = parsed.amount.minor,
                direction = TransactionDirection.CREDIT,
                occurredAt = occurredAt,
                merchantRaw = parsed.merchant,
                merchantId = null,
                categoryId = null,
                note = "Withdrawn from $from",
                counterparty = null,
                source = TransactionSource.SMS,
                confidence = Confidence.HIGH,
                rawMessageId = null,
                transferGroupId = withdrawal.transferGroupId,
                feeMinor = null,
                referenceNumber = null,
                providerTxnId = null,
                kind = TransactionKind.TRANSFER,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
        )
        val id = if (cashId == -1L) existing!!.id else cashId

        if (existing == null) accountDao.adjustBalance(cash.id, parsed.amount.minor, now)
        if (withdrawal.transferGroupId == null) {
            transactionDao.markAsTransfer(listOf(withdrawalId, id), UUID.randomUUID().toString(), now)
        }
    }

    private suspend fun resolveAccount(sender: String, tail: String?): AccountEntity? {
        val accounts = accountDao.getAll()
        if (tail != null) {
            accounts.firstOrNull { accountTail(it.smsIdentifiers) == tail }?.let { return it }
        }
        return accounts.firstOrNull { namesSender(it.smsIdentifiers, sender) }
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

private fun sha256(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }
