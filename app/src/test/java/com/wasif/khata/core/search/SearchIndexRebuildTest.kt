package com.wasif.khata.core.search

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.entity.MerchantAliasEntity
import com.wasif.khata.core.data.entity.MerchantEntity
import com.wasif.khata.core.data.entity.TransactionEntity
import com.wasif.khata.core.model.Confidence
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionKind
import com.wasif.khata.core.model.TransactionSource
import com.wasif.khata.core.prefs.HomeView
import com.wasif.khata.core.prefs.KhataPreferences
import com.wasif.khata.core.prefs.PreferencesRepository
import com.wasif.khata.core.ui.theme.ThemeSpec
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchIndexRebuildTest {

    private lateinit var db: KhataDatabase
    private lateinit var prefs: FakePreferences

    /** Remembers the version, because running at most once is the thing under test. */
    private class FakePreferences : PreferencesRepository {
        val state = MutableStateFlow(KhataPreferences.Default)
        var writes = 0

        override val preferences: Flow<KhataPreferences> = state

        override suspend fun setSearchIndexVersion(version: Int) {
            writes++
            state.value = state.value.copy(searchIndexVersion = version)
        }

        override suspend fun setTheme(spec: ThemeSpec) = Unit
        override suspend fun resetTheme() = Unit
        override suspend fun setHomeView(view: HomeView) = Unit
        override suspend fun setMonthlyBudget(minor: Long?) = Unit
        override suspend fun setSmsPermissionRequested() = Unit
        override suspend fun setBackfilled() = Unit
        override suspend fun setGeminiKey(key: String?) = Unit
        override suspend fun setTmdbKey(key: String?) = Unit
        override suspend fun setBackupPassphrase(passphrase: String?) = Unit
        override suspend fun setDriveConnected(connected: Boolean) = Unit
        override suspend fun setDriveFolderId(id: String?) = Unit
        override suspend fun setDriveUploaded(at: Long) = Unit
        override suspend fun setDriveNeedsReconnect() = Unit
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            KhataDatabase::class.java,
        ).allowMainThreadQueries().build()
        prefs = FakePreferences()
    }

    @After
    fun tearDown() = db.close()

    private fun rebuild() = SearchIndexRebuild(prefs, searchIndex(db))

    /**
     * A row whose raw text says FP*8823 and which resolves to Foodpanda -- exactly
     * what the migration's SQL backfill could see only half of.
     */
    private suspend fun givenResolvedTransaction(): Long {
        val merchantId = db.merchantDao().upsert(
            MerchantEntity(
                uuid = UUID.randomUUID().toString(),
                canonicalName = "Foodpanda",
                categoryId = null,
                placeId = null,
                isUserConfirmed = true,
                createdAt = 1,
                updatedAt = 1,
            ),
        )
        db.merchantDao().upsertAlias(
            MerchantAliasEntity(
                uuid = UUID.randomUUID().toString(),
                merchantId = merchantId,
                rawText = "FP*8823",
                createdAt = 1,
                updatedAt = 1,
            ),
        )
        return db.transactionDao().upsert(
            TransactionEntity(
                uuid = UUID.randomUUID().toString(),
                accountId = 1,
                amountMinor = 8560,
                direction = TransactionDirection.DEBIT,
                occurredAt = 1000,
                merchantRaw = "FP*8823",
                merchantId = merchantId,
                categoryId = null,
                note = null,
                counterparty = null,
                source = TransactionSource.SMS,
                confidence = Confidence.HIGH,
                kind = TransactionKind.NORMAL,
                rawMessageId = null,
                transferGroupId = null,
                feeMinor = null,
                referenceNumber = null,
                createdAt = 1000,
                updatedAt = 1000,
            ),
        )
    }

    /** As the 7->8 migration leaves it: the three raw columns and nothing more. */
    private suspend fun givenMigrationBackfill(id: Long) = db.searchDao().insert(
        com.wasif.khata.core.data.entity.SearchFtsEntity(
            entityType = "transaction",
            entityId = id,
            text = "FP*8823  ",
        ),
    )

    private suspend fun search(term: String) =
        db.searchDao().idsMatching("transaction", ftsQuery(term)!!)

    @Test
    fun `the rebuild reaches the merchant name the migration could not`() = runTest {
        val id = givenResolvedTransaction()
        givenMigrationBackfill(id)
        assertFalse("precondition: the backfill cannot see the resolved name", search("foodpanda").contains(id))

        rebuild().runIfNeeded()

        assertTrue(search("foodpanda").contains(id))
        assertEquals(SearchIndexRebuild.VERSION, prefs.state.value.searchIndexVersion)
    }

    @Test
    fun `it runs once, not on every launch`() = runTest {
        val id = givenResolvedTransaction()
        givenMigrationBackfill(id)

        repeat(3) { rebuild().runIfNeeded() }

        assertEquals(1, prefs.writes)
        // And one row per entity, not three -- a rebuild that ran again would have
        // to delete first, and this is what proves it did.
        assertEquals(listOf(id), search("foodpanda"))
    }
}
