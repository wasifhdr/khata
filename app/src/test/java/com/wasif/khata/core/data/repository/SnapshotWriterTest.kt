package com.wasif.khata.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.entity.AccountEntity
import com.wasif.khata.core.data.entity.TransactionEntity
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionSource
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.time.toDhakaDayIndex
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SnapshotWriterTest {

    private lateinit var db: KhataDatabase
    private lateinit var writer: SnapshotWriter

    private val day1 = Instant.parse("2026-09-01T06:00:00Z").toEpochMilli()
    private val day2 = Instant.parse("2026-09-02T06:00:00Z").toEpochMilli()
    private val day3 = Instant.parse("2026-09-03T06:00:00Z").toEpochMilli()

    private val clock = object : KhataClock { override fun now(): Long = day3 }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
        writer = SnapshotWriter(db.balanceSnapshotDao(), db.transactionDao(), db.accountDao(), clock)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun account(opening: Long, current: Long, uuid: String = "acc-1"): Long =
        db.accountDao().upsert(
            AccountEntity(
                uuid = uuid,
                name = uuid,
                type = AccountType.CASH,
                openingBalanceMinor = opening,
                currentBalanceMinor = current,
                reportedBalanceMinor = null,
                reportedBalanceAt = null,
                includeInNetWorth = true,
                smsIdentifiers = "",
                createdAt = 1000,
                updatedAt = 1000,
            ),
        )

    private suspend fun txn(accountId: Long, at: Long, minor: Long, direction: TransactionDirection) {
        db.transactionDao().upsert(
            TransactionEntity(
                uuid = "t-$at-$minor-$direction",
                accountId = accountId,
                amountMinor = minor,
                direction = direction,
                occurredAt = at,
                merchantRaw = null,
                merchantId = null,
                categoryId = null,
                note = null,
                source = TransactionSource.MANUAL,
                confidence = Confidence.HIGH,
                rawMessageId = null,
                transferGroupId = null,
                feeMinor = null,
                referenceNumber = null,
                createdAt = at,
                updatedAt = at,
            ),
        )
    }

    @Test
    fun `each day gets the balance as it stood that night`() = runTest {
        // Opening 1000, spend 200 on day 1, receive 500 on day 2 -> current 1300.
        val id = account(opening = 1_000, current = 1_300)
        txn(id, day1, 200, TransactionDirection.DEBIT)
        txn(id, day2, 500, TransactionDirection.CREDIT)

        writer.fillThrough(day3)

        val rows = db.balanceSnapshotDao().rowsFor(id).associate { it.dayIndex to it.balanceMinor }
        assertEquals(800L, rows[day1.toDhakaDayIndex()])
        assertEquals(1_300L, rows[day2.toDhakaDayIndex()])
        assertEquals(1_300L, rows[day3.toDhakaDayIndex()])
    }

    @Test
    fun `a second run writes nothing, because nothing is missing`() = runTest {
        val id = account(opening = 0, current = -100)
        txn(id, day1, 100, TransactionDirection.DEBIT)
        writer.fillThrough(day3)
        val first = db.balanceSnapshotDao().rowsFor(id).map { it.uuid }

        writer.fillThrough(day3)

        // Same rows, same identities -- not merely the same count.
        assertEquals(first, db.balanceSnapshotDao().rowsFor(id).map { it.uuid })
    }

    @Test
    fun `a gap of several days is filled in one pass`() = runTest {
        val id = account(opening = 0, current = -100)
        txn(id, day1, 100, TransactionDirection.DEBIT)

        writer.fillThrough(day3)

        // Days 1, 2 and 3 -- a phone that was off for two nights leaves no hole.
        assertEquals(3, db.balanceSnapshotDao().rowsFor(id).size)
    }

    @Test
    fun `an account with no transactions still gets its opening balance`() = runTest {
        val id = account(opening = 7_500, current = 7_500, uuid = "acc-quiet")

        writer.fillThrough(day3)

        val rows = db.balanceSnapshotDao().rowsFor(id)
        // Skipping it would drop it out of net worth entirely on every day of the
        // chart, which is a different claim from "it did not move".
        assertEquals(7_500L, rows.single().balanceMinor)
        assertEquals(day3.toDhakaDayIndex(), rows.single().dayIndex)
    }
}
