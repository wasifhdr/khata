package com.wasif.khata.core.sms

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.entity.TransactionEntity
import com.wasif.khata.core.data.seed.DatabaseSeeder
import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionKind
import com.wasif.khata.core.model.TransactionSource
import com.wasif.khata.core.notify.TransferNotifier
import com.wasif.khata.core.time.KhataClock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransferPairingTest {

    private lateinit var db: KhataDatabase
    private lateinit var pairing: TransferPairing

    private val clock = object : KhataClock {
        override fun now(): Long = 9_000L
    }

    // The real Own Account Transfer pair from the corpus, one second apart.
    private val debitAt = Instant.parse("2026-08-18T07:20:19Z").toEpochMilli()
    private val creditAt = Instant.parse("2026-08-18T07:20:20Z").toEpochMilli()

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
        DatabaseSeeder(db.accountDao(), db.categoryDao(), db.parsingRuleDao(), clock).seedIfEmpty()
        pairing = TransferPairing(
                db.transactionDao(),
                TransferNotifier(ApplicationProvider.getApplicationContext()),
                clock,
            )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun accountId(name: String) = db.accountDao().getAll().first { it.name == name }.id

    private suspend fun insert(
        accountId: Long,
        amountMinor: Long,
        direction: TransactionDirection,
        occurredAt: Long,
        transferGroupId: String? = null,
    ): Long = db.transactionDao().upsert(
        TransactionEntity(
            uuid = UUID.randomUUID().toString(),
            accountId = accountId,
            amountMinor = amountMinor,
            direction = direction,
            occurredAt = occurredAt,
            merchantRaw = "Own Account Transfer",
            merchantId = null,
            categoryId = null,
            note = null,
            source = TransactionSource.SMS,
            confidence = Confidence.HIGH,
            rawMessageId = null,
            transferGroupId = transferGroupId,
            feeMinor = null,
            referenceNumber = null,
            createdAt = occurredAt,
            updatedAt = occurredAt,
        )
    )

    private suspend fun byId(id: Long) = db.transactionDao().findById(id)!!

    @Test
    fun `an equal and opposite pair one second apart is grouped`() = runTest {
        val debit = insert(accountId("EBL Student"), 650000, TransactionDirection.DEBIT, debitAt)
        val credit = insert(accountId("EBL Salary"), 650000, TransactionDirection.CREDIT, creditAt)

        val group = pairing.pair(credit)

        assertNotNull("expected a pair to form", group)
        assertEquals(group, byId(debit).transferGroupId)
        assertEquals(group, byId(credit).transferGroupId)
        assertEquals(TransactionKind.TRANSFER, byId(debit).kind)
        assertEquals(TransactionKind.TRANSFER, byId(credit).kind)
    }

    @Test
    fun `a same-account pair is not a transfer`() = runTest {
        val salary = accountId("EBL Salary")
        insert(salary, 650000, TransactionDirection.DEBIT, debitAt)
        val credit = insert(salary, 650000, TransactionDirection.CREDIT, creditAt)

        assertNull(pairing.pair(credit))
        assertNull(byId(credit).transferGroupId)
    }

    @Test
    fun `an unequal amount does not pair`() = runTest {
        insert(accountId("EBL Student"), 650000, TransactionDirection.DEBIT, debitAt)
        val credit = insert(accountId("EBL Salary"), 640000, TransactionDirection.CREDIT, creditAt)

        assertNull(pairing.pair(credit))
    }

    @Test
    fun `a pair outside the fifteen minute window does not pair`() = runTest {
        insert(accountId("EBL Student"), 650000, TransactionDirection.DEBIT, debitAt)
        val credit = insert(
            accountId("EBL Salary"),
            650000,
            TransactionDirection.CREDIT,
            debitAt + 16 * 60 * 1000,
        )

        assertNull(pairing.pair(credit))
    }

    @Test
    fun `an already grouped candidate is not re-paired`() = runTest {
        insert(accountId("EBL Student"), 650000, TransactionDirection.DEBIT, debitAt, transferGroupId = "existing")
        val credit = insert(accountId("EBL Salary"), 650000, TransactionDirection.CREDIT, creditAt)

        assertNull(pairing.pair(credit))
    }

    @Test
    fun `when two candidates qualify the closest in time wins`() = runTest {
        val student = accountId("EBL Student")
        val far = insert(student, 650000, TransactionDirection.DEBIT, debitAt - 10 * 60 * 1000)
        val near = insert(student, 650000, TransactionDirection.DEBIT, debitAt)
        val credit = insert(accountId("EBL Salary"), 650000, TransactionDirection.CREDIT, creditAt)

        val group = pairing.pair(credit)

        assertEquals(group, byId(near).transferGroupId)
        assertNull(byId(far).transferGroupId)
    }

    @Test
    fun `pairing is deterministic when run twice`() = runTest {
        val debit = insert(accountId("EBL Student"), 650000, TransactionDirection.DEBIT, debitAt)
        val credit = insert(accountId("EBL Salary"), 650000, TransactionDirection.CREDIT, creditAt)

        val first = pairing.pair(credit)
        val second = pairing.pair(credit)

        assertEquals(first, byId(credit).transferGroupId)
        assertEquals(first, byId(debit).transferGroupId)
        // Already grouped, so the second run forms nothing new.
        assertNull(second)
    }

    @Test
    fun `a transaction with no counterpart pairs with nothing`() = runTest {
        val credit = insert(accountId("EBL Salary"), 650000, TransactionDirection.CREDIT, creditAt)

        assertNull(pairing.pair(credit))
        assertEquals(TransactionKind.NORMAL, byId(credit).kind)
    }
}
