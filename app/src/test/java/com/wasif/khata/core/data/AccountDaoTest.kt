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

    private fun account(name: String, uuid: String) = AccountEntity(
        uuid = uuid,
        name = name,
        type = AccountType.MFS,
        openingBalanceMinor = 0,
        currentBalanceMinor = 0,
        reportedBalanceMinor = null,
        reportedBalanceAt = null,
        includeInNetWorth = true,
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
}
