package com.wasif.khata.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.entity.AccountEntity
import com.wasif.khata.core.model.AccountType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AccountDaoTest {

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

    private fun account(
        name: String,
        uuid: String,
        balanceMinor: Long = 0,
        include: Boolean = true,
    ) = AccountEntity(
        uuid = uuid,
        name = name,
        type = AccountType.MFS,
        openingBalanceMinor = 0,
        currentBalanceMinor = balanceMinor,
        reportedBalanceMinor = null,
        reportedBalanceAt = null,
        includeInNetWorth = include,
        smsIdentifiers = name,
        createdAt = 1000,
        updatedAt = 1000,
    )

    @Test
    fun `observeAll orders account names case-insensitively`() = runTest {
        db.accountDao().upsert(account("bKash", uuid = "acc-bkash"))
        db.accountDao().upsert(account("EBL", uuid = "acc-ebl"))
        db.accountDao().upsert(account("Cash", uuid = "acc-cash"))

        val names = db.accountDao().observeAll().first().map { it.name }

        assertEquals(listOf("bKash", "Cash", "EBL"), names)
    }

    @Test
    fun `net worth sums only accounts flagged for inclusion`() = runTest {
        val dao = db.accountDao()
        dao.upsert(account(uuid = "in-1", name = "bKash", balanceMinor = 8_214_30, include = true))
        dao.upsert(account(uuid = "in-2", name = "EBL", balanceMinor = 2_98_606_00, include = true))
        // A credit card or a tracked-but-excluded pot must not inflate net worth.
        dao.upsert(account(uuid = "out", name = "Excluded", balanceMinor = 99_999_00, include = false))

        assertEquals(8_214_30L + 2_98_606_00L, dao.observeNetWorthMinor().first())
    }

    @Test
    fun `net worth is zero rather than null on an empty database`() = runTest {
        // Runs on every first launch, before any account exists.
        assertEquals(0L, db.accountDao().observeNetWorthMinor().first())
    }
}
