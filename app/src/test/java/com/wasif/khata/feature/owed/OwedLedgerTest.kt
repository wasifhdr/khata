package com.wasif.khata.feature.owed

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.entity.TransactionEntity
import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionKind
import com.wasif.khata.core.model.TransactionSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Who owes whom, once everything between two people has cancelled out. */
@RunWith(RobolectricTestRunner::class)
class OwedLedgerTest {

    private lateinit var db: KhataDatabase
    private var seq = 0

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun row(
        amount: Long,
        who: String?,
        direction: TransactionDirection = TransactionDirection.DEBIT,
        owed: Long = amount,
        kind: TransactionKind = TransactionKind.NORMAL,
    ) = db.transactionDao().upsert(
        TransactionEntity(
            uuid = "t${seq++}",
            accountId = if (kind == TransactionKind.IOU) 0 else 1,
            amountMinor = amount,
            direction = direction,
            occurredAt = 1_000L + seq,
            merchantRaw = null,
            merchantId = null,
            categoryId = null,
            note = null,
            counterparty = who,
            owedMinor = owed,
            source = TransactionSource.MANUAL,
            confidence = Confidence.HIGH,
            rawMessageId = null,
            transferGroupId = null,
            feeMinor = null,
            referenceNumber = null,
            kind = kind,
            createdAt = 1,
            updatedAt = 1,
        )
    )

    private suspend fun owed() = db.transactionDao().observeOwedByPerson().first()

    @Test
    fun `lending money puts someone in the owes-you column`() = runTest {
        row(50_000, "Rafi")

        val rafi = owed().single()
        assertEquals("Rafi", rafi.name)
        assertEquals(50_000L, rafi.netMinor)
    }

    @Test
    fun `getting it back settles them out of the list entirely`() = runTest {
        row(50_000, "Rafi")
        row(50_000, "Rafi", TransactionDirection.CREDIT)

        // Netting to zero means settled, which is not the same as owing zero and
        // still being listed.
        assertTrue(owed().isEmpty())
    }

    @Test
    fun `a partial repayment leaves the remainder`() = runTest {
        row(50_000, "Rafi")
        row(20_000, "Rafi", TransactionDirection.CREDIT)

        assertEquals(30_000L, owed().single().netMinor)
    }

    @Test
    fun `borrowing puts you in the you-owe column`() = runTest {
        row(50_000, "Rafi", TransactionDirection.CREDIT)

        assertEquals(-50_000L, owed().single().netMinor)
    }

    @Test
    fun `paying back what you borrowed clears it`() = runTest {
        row(50_000, "Rafi", TransactionDirection.CREDIT)
        row(50_000, "Rafi")

        assertTrue(owed().isEmpty())
    }

    @Test
    fun `a bill you covered is owed to you, and their share settles it`() = runTest {
        row(80_000, "Rafi")
        row(80_000, "Rafi", TransactionDirection.CREDIT)

        assertTrue(owed().isEmpty())
    }

    @Test
    fun `lending and borrowing with the same person nets to one figure`() = runTest {
        row(50_000, "Rafi")
        row(20_000, "Rafi", TransactionDirection.CREDIT)

        assertEquals(30_000L, owed().single().netMinor)
    }

    @Test
    fun `a name is one person however it was typed`() = runTest {
        row(50_000, "Rafi")
        row(30_000, " rafi ")

        val all = owed()
        assertEquals("case and stray spaces must not split a debt in two", 1, all.size)
        assertEquals(80_000L, all.single().netMinor)
    }

    @Test
    fun `ordinary spending never appears, whoever it names`() = runTest {
        row(50_000, "Rafi", owed = 0)

        assertTrue(owed().isEmpty())
    }

    @Test
    fun `loans with no name yet are listed separately so they can be named`() = runTest {
        row(50_000, null)
        row(30_000, "  ")
        row(20_000, "Rafi")

        assertEquals(2, db.transactionDao().observeUnnamedOwed().first().size)
        assertEquals(1, owed().size)
    }

    @Test
    fun `two people are kept apart`() = runTest {
        row(50_000, "Rafi")
        row(20_000, "Sadia", TransactionDirection.CREDIT)

        val all = owed().associateBy { it.name }
        assertEquals(50_000L, all["Rafi"]!!.netMinor)
        assertEquals(-20_000L, all["Sadia"]!!.netMinor)
    }

    @Test
    fun `splitting a bill counts only the owed share on the person's tab`() = runTest {
        row(100_000, "Rafi", owed = 40_000)

        val rafi = owed().single()
        assertEquals("Rafi", rafi.name)
        assertEquals(40_000L, rafi.netMinor)
    }

    @Test
    fun `an IOU where someone else paid puts you in the you-owe column`() = runTest {
        row(50_000, "Rafi", kind = TransactionKind.IOU)

        val rafi = owed().single()
        assertEquals("Rafi", rafi.name)
        assertEquals(-50_000L, rafi.netMinor)
    }

    @Test
    fun `observeOwedEntriesForPerson matches case-insensitively and trims whitespace`() = runTest {
        row(50_000, "Rafi")
        row(30_000, " rafi ", kind = TransactionKind.IOU)
        row(20_000, "Sadia")

        val entries = db.transactionDao().observeOwedEntriesForPerson("RAFI").first()
        assertEquals(2, entries.size)
        assertEquals(listOf(30_000L, 50_000L), entries.map { it.owedMinor })
    }

    @Test
    fun `observeRecentCounterparties returns distinct trimmed names newest first`() = runTest {
        row(10_000, "Rafi")
        row(20_000, "Sadia")
        row(30_000, " rafi ")

        val recent = db.transactionDao().observeRecentCounterparties(6).first()
        assertEquals(2, recent.size)
        assertEquals("rafi", recent[0].lowercase())
        assertEquals("Sadia", recent[1])
    }
}
