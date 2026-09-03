package com.wasif.khata.core.data

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.testing.TestPager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.dao.pagingSourceMatching
import com.wasif.khata.core.data.entity.AccountEntity
import com.wasif.khata.core.data.entity.TransactionEntity
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionSource
import com.wasif.khata.core.time.dhakaMonthStart
import com.wasif.khata.core.time.dhakaNextMonthStart
import com.wasif.khata.core.time.toDhakaDayIndex
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransactionDaoTest {

    private lateinit var db: KhataDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insertAccount(): Long = db.accountDao().upsert(
        AccountEntity(
            uuid = "acc-1",
            name = "bKash",
            type = AccountType.MFS,
            openingBalanceMinor = 0,
            currentBalanceMinor = 0,
            reportedBalanceMinor = null,
            reportedBalanceAt = null,
            includeInNetWorth = true,
            smsIdentifiers = "bKash",
            createdAt = 1000,
            updatedAt = 1000,
        )
    )

    private fun transaction(
        accountId: Long,
        occurredAt: Long,
        uuid: String,
        amountMinor: Long = 10_000,
        direction: TransactionDirection = TransactionDirection.DEBIT,
        merchantRaw: String? = "SHWAPNO",
        categoryId: Long? = null,
    ) =
        TransactionEntity(
            uuid = uuid,
            accountId = accountId,
            amountMinor = amountMinor,
            direction = direction,
            occurredAt = occurredAt,
            merchantRaw = merchantRaw,
            merchantId = null,
            categoryId = categoryId,
            note = null,
            source = TransactionSource.MANUAL,
            confidence = Confidence.HIGH,
            rawMessageId = null,
            transferGroupId = null,
            feeMinor = null,
            referenceNumber = null,
            createdAt = occurredAt,
            updatedAt = occurredAt,
        )

    private suspend fun pageUuids(): List<String> {
        val pager = TestPager(PagingConfig(pageSize = 10), db.transactionDao().pagingSource())
        val page = pager.refresh() as PagingSource.LoadResult.Page
        return page.data.map { it.uuid }
    }

    @Test
    fun `paging source returns transactions newest first`() = runTest {
        val accountId = insertAccount()
        db.transactionDao().upsert(transaction(accountId, occurredAt = 1000, uuid = "t-old"))
        db.transactionDao().upsert(transaction(accountId, occurredAt = 3000, uuid = "t-new"))
        db.transactionDao().upsert(transaction(accountId, occurredAt = 2000, uuid = "t-mid"))

        assertEquals(listOf("t-new", "t-mid", "t-old"), pageUuids())
    }

    @Test
    fun `transactions sharing a timestamp get a stable order`() = runTest {
        val accountId = insertAccount()
        val firstId = db.transactionDao().upsert(transaction(accountId, occurredAt = 5000, uuid = "t-a"))
        val secondId = db.transactionDao().upsert(transaction(accountId, occurredAt = 5000, uuid = "t-b"))

        // Higher rowid first, so backfilled batches never shuffle between page loads.
        assertEquals(listOf("t-b", "t-a"), pageUuids())
        assertEquals(true, secondId > firstId)
    }

    @Test
    fun `soft deleted transactions are excluded from paging`() = runTest {
        val accountId = insertAccount()
        val id = db.transactionDao().upsert(transaction(accountId, occurredAt = 1000, uuid = "t-1"))
        db.transactionDao().upsert(transaction(accountId, occurredAt = 2000, uuid = "t-2"))

        db.transactionDao().softDelete(id, deletedAt = 9999)

        assertEquals(listOf("t-2"), pageUuids())
    }

    @Test
    fun `observeById emits null after soft delete`() = runTest {
        val accountId = insertAccount()
        val id = db.transactionDao().upsert(transaction(accountId, occurredAt = 1000, uuid = "t-1"))

        assertEquals("t-1", db.transactionDao().observeById(id).first()?.uuid)

        db.transactionDao().softDelete(id, deletedAt = 9999)
        assertNull(db.transactionDao().observeById(id).first())
    }

    @Test
    fun `adjustBalance applies a signed delta to the account`() = runTest {
        val accountId = insertAccount()

        db.accountDao().adjustBalance(accountId, deltaMinor = -5000, updatedAt = 2000)
        assertEquals(-5000, db.accountDao().observeAll().first().single().currentBalanceMinor)

        db.accountDao().adjustBalance(accountId, deltaMinor = 8000, updatedAt = 3000)
        assertEquals(3000, db.accountDao().observeAll().first().single().currentBalanceMinor)
    }

    @Test
    fun `observeTotalMinorBetween sums only debits inside the window`() = runTest {
        val accountId = insertAccount()
        val inWindow = Instant.parse("2026-08-10T06:00:00Z").toEpochMilli()
        val before = Instant.parse("2026-07-10T06:00:00Z").toEpochMilli()
        val dao = db.transactionDao()

        dao.upsert(transaction(accountId, inWindow, "t1", amountMinor = 2_340_50))
        dao.upsert(transaction(accountId, inWindow, "t2", amountMinor = 42_000))
        // A credit inside the window and a debit outside it must both be excluded.
        dao.upsert(
            transaction(accountId, inWindow, "t3", amountMinor = 85_000_00, direction = TransactionDirection.CREDIT),
        )
        dao.upsert(transaction(accountId, before, "t4", amountMinor = 999_00))

        val total = dao.observeTotalMinorBetween(
            direction = TransactionDirection.DEBIT,
            fromInclusive = inWindow.dhakaMonthStart(),
            toExclusive = inWindow.dhakaNextMonthStart(),
        ).first()

        assertEquals(2_340_50L + 42_000L, total)
    }

    @Test
    fun `observeTotalMinorBetween returns zero rather than null when empty`() = runTest {
        // COALESCE matters: a null here would fail at the repository boundary on
        // a brand new install, which is the one moment the hub is guaranteed to
        // be shown.
        val now = Instant.parse("2026-08-10T06:00:00Z").toEpochMilli()

        val total = db.transactionDao().observeTotalMinorBetween(
            direction = TransactionDirection.DEBIT,
            fromInclusive = now.dhakaMonthStart(),
            toExclusive = now.dhakaNextMonthStart(),
        ).first()

        assertEquals(0L, total)
    }

    @Test
    fun `observeMostRecent ignores soft-deleted rows`() = runTest {
        val accountId = insertAccount()
        val older = Instant.parse("2026-08-10T06:00:00Z").toEpochMilli()
        val newer = Instant.parse("2026-08-20T06:00:00Z").toEpochMilli()
        val dao = db.transactionDao()

        dao.upsert(transaction(accountId, older, "old", merchantRaw = "Chaldal"))
        val newestId = dao.upsert(transaction(accountId, newer, "new", merchantRaw = "Daraz"))

        assertEquals("Daraz", dao.observeMostRecent().first()?.merchantRaw)

        dao.softDelete(newestId, deletedAt = newer)
        assertEquals("Chaldal", dao.observeMostRecent().first()?.merchantRaw)
    }

    @Test
    fun `day totals group debits by Dhaka day and ignore credits`() = runTest {
        val accountId = insertAccount()
        val dao = db.transactionDao()
        // Both are 29 August in Dhaka: 18:01 UTC on the 28th is 00:01 on the 29th.
        val justAfterDhakaMidnight = Instant.parse("2026-08-28T18:01:00Z").toEpochMilli()
        val laterSameDhakaDay = Instant.parse("2026-08-29T10:00:00Z").toEpochMilli()
        // 17:59 UTC on the 28th is still 28 August in Dhaka.
        val previousDhakaDay = Instant.parse("2026-08-28T17:59:00Z").toEpochMilli()

        dao.upsert(transaction(accountId, justAfterDhakaMidnight, "a", amountMinor = 100_00))
        dao.upsert(transaction(accountId, laterSameDhakaDay, "b", amountMinor = 250_00))
        dao.upsert(transaction(accountId, previousDhakaDay, "c", amountMinor = 900_00))
        dao.upsert(
            transaction(
                accountId,
                laterSameDhakaDay,
                "d",
                amountMinor = 5_000_00,
                direction = TransactionDirection.CREDIT,
            ),
        )

        val totals = dao.observeDayTotals().first().associate { it.dhakaDayIndex to it.spentMinor }

        assertEquals(100_00L + 250_00L, totals[justAfterDhakaMidnight.toDhakaDayIndex()])
        assertEquals(900_00L, totals[previousDhakaDay.toDhakaDayIndex()])
    }

    @Test
    fun `day totals exclude soft-deleted rows`() = runTest {
        val accountId = insertAccount()
        val dao = db.transactionDao()
        val day = Instant.parse("2026-08-29T10:00:00Z").toEpochMilli()

        dao.upsert(transaction(accountId, day, "keep", amountMinor = 100_00))
        val goneId = dao.upsert(transaction(accountId, day, "gone", amountMinor = 700_00))
        dao.softDelete(goneId, deletedAt = day)

        val totals = dao.observeDayTotals().first().associate { it.dhakaDayIndex to it.spentMinor }

        assertEquals(100_00L, totals[day.toDhakaDayIndex()])
    }

    @Test
    fun `paging is scoped to a half-open window`() = runTest {
        val accountId = insertAccount()
        val dao = db.transactionDao()
        val august = Instant.parse("2026-08-10T06:00:00Z").toEpochMilli()
        val july = Instant.parse("2026-07-10T06:00:00Z").toEpochMilli()
        val fromInclusive = august.dhakaMonthStart()
        val toExclusive = august.dhakaNextMonthStart()

        dao.upsert(transaction(accountId, august, "aug"))
        dao.upsert(transaction(accountId, july, "jul"))
        // Exactly on the boundaries: a > / <= bug (or the reverse) would pass
        // the whole-month-apart fixtures above without ever being caught.
        dao.upsert(transaction(accountId, fromInclusive, "at-from-inclusive"))
        dao.upsert(transaction(accountId, toExclusive, "at-to-exclusive"))

        val pager = TestPager(
            PagingConfig(pageSize = 10),
            dao.pagingSourceBetween(fromInclusive, toExclusive),
        )
        val page = pager.refresh() as PagingSource.LoadResult.Page

        assertEquals(setOf("aug", "at-from-inclusive"), page.data.map { it.uuid }.toSet())
    }

    @Test
    fun `search matches merchant and note, case-insensitively`() = runTest {
        val accountId = insertAccount()
        val dao = db.transactionDao()
        val day = Instant.parse("2026-08-29T06:00:00Z").toEpochMilli()

        dao.upsert(transaction(accountId, day, "m1", merchantRaw = "North End Coffee"))
        dao.upsert(transaction(accountId, day, "m2", merchantRaw = "Chaldal"))

        val pager = TestPager(PagingConfig(pageSize = 10), dao.pagingSourceMatching("coffee"))
        val page = pager.refresh() as PagingSource.LoadResult.Page

        assertEquals(listOf("m1"), page.data.map { it.uuid })
    }

    @Test
    fun `search escapes wildcards so a literal percent finds nothing`() = runTest {
        // Without escaping, a bare % matches every row -- which would turn a
        // failed search into "here is your entire ledger".
        val accountId = insertAccount()
        val dao = db.transactionDao()
        val day = Instant.parse("2026-08-29T06:00:00Z").toEpochMilli()

        dao.upsert(transaction(accountId, day, "m1", merchantRaw = "Chaldal"))

        val pager = TestPager(PagingConfig(pageSize = 10), dao.pagingSourceMatching("%"))
        val page = pager.refresh() as PagingSource.LoadResult.Page

        assertEquals(emptyList<String>(), page.data.map { it.uuid })
    }

    @Test
    fun `recent categories come back most recently used first`() = runTest {
        val bkash = insertAccount()
        val cash = db.accountDao().upsert(
            AccountEntity(
                uuid = "acc-cash",
                name = "Cash",
                type = AccountType.CASH,
                openingBalanceMinor = 0,
                currentBalanceMinor = 0,
                reportedBalanceMinor = null,
                reportedBalanceAt = null,
                includeInNetWorth = true,
                smsIdentifiers = "",
                createdAt = 1000,
                updatedAt = 1000,
            )
        )
        val older = Instant.parse("2026-08-01T10:00:00Z").toEpochMilli()
        val newer = Instant.parse("2026-09-01T10:00:00Z").toEpochMilli()

        db.transactionDao().upsert(transaction(cash, older, "t-old", categoryId = 7L))
        db.transactionDao().upsert(transaction(cash, newer, "t-new", categoryId = 9L))
        // Same category, wrong account: the cash sheet orders by cash habits.
        db.transactionDao().upsert(transaction(bkash, newer, "t-other", categoryId = 3L))

        val recent = db.transactionDao().observeRecentCategoryIds(cash, limit = 10).first()

        assertEquals(listOf(9L, 7L), recent)
    }

    @Test
    fun `a category present in only one month still appears, with zero for the other`() = runTest {
        val accountId = insertAccount()
        val thisMonth = Instant.parse("2026-09-10T06:00:00Z").toEpochMilli()
        val lastMonth = Instant.parse("2026-08-10T06:00:00Z").toEpochMilli()
        val dao = db.transactionDao()

        dao.upsert(transaction(accountId, thisMonth, "t-new", amountMinor = 500, categoryId = 1))
        dao.upsert(transaction(accountId, lastMonth, "t-gone", amountMinor = 900, categoryId = 2))
        dao.upsert(transaction(accountId, thisMonth, "t-both-a", amountMinor = 100, categoryId = 3))
        dao.upsert(transaction(accountId, lastMonth, "t-both-b", amountMinor = 300, categoryId = 3))

        val rows = dao.compareCategorySpend(
            thisFrom = thisMonth.dhakaMonthStart(),
            thisTo = thisMonth.dhakaNextMonthStart(),
            lastFrom = lastMonth.dhakaMonthStart(),
            lastTo = lastMonth.dhakaNextMonthStart(),
        ).first().associateBy { it.categoryId }

        // A category that appeared and one that vanished are both real answers; two
        // separate queries subtracted in the UI would drop whichever month lacked it.
        assertEquals(500L to 0L, rows[1]!!.let { it.thisMonthMinor to it.lastMonthMinor })
        assertEquals(0L to 900L, rows[2]!!.let { it.thisMonthMinor to it.lastMonthMinor })
        assertEquals(100L to 300L, rows[3]!!.let { it.thisMonthMinor to it.lastMonthMinor })
    }

    @Test
    fun `top merchants are ordered by spend and cut to the limit`() = runTest {
        val accountId = insertAccount()
        val at = Instant.parse("2026-09-10T06:00:00Z").toEpochMilli()
        val dao = db.transactionDao()

        dao.upsert(transaction(accountId, at, "m-1", amountMinor = 100, merchantRaw = "SMALL"))
        dao.upsert(transaction(accountId, at, "m-2", amountMinor = 900, merchantRaw = "BIG"))
        dao.upsert(transaction(accountId, at, "m-3", amountMinor = 400, merchantRaw = "MID"))

        val top = dao.observeTopMerchants(
            at.dhakaMonthStart(), at.dhakaNextMonthStart(), limit = 2,
        ).first()

        assertEquals(listOf("BIG", "MID"), top.map { it.merchantName })
    }
}
