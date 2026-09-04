package com.wasif.khata.core.data.migration

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.model.Confidence

import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val TEST_DB = "migration-9-10-test.db"
private const val SCHEMA_DIR = "schemas/com.wasif.khata.core.data.KhataDatabase"

/**
 * Builds a real version-9 database from the exported schema and migrates it, rather
 * than using MigrationTestHelper, which cannot agree with Robolectric about database
 * paths. Validation is not lost: the test reopens the migrated file through Room
 * itself, so Room's own identity-hash and column checks run against the result.
 */
@RunWith(RobolectricTestRunner::class)
class Migration9To10Test {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = deleteTestDb()

    @After
    fun tearDown() = deleteTestDb()

    private fun deleteTestDb() {
        context.getDatabasePath(TEST_DB).let { f ->
            f.delete()
            File(f.path + "-shm").delete()
            File(f.path + "-wal").delete()
        }
    }

    private fun schemaJson(version: Int): JSONObject {
        val candidates = listOf(File("$SCHEMA_DIR/$version.json"), File("app/$SCHEMA_DIR/$version.json"))
        val file = candidates.firstOrNull { it.exists() }
        assertNotNull("cannot locate $version.json; looked in ${candidates.map { it.absolutePath }}", file)
        return JSONObject(file!!.readText()).getJSONObject("database")
    }

    /** Recreates schema v9 exactly as Room would have, identity hash included. */
    private fun createV9Database(): SupportSQLiteDatabase {
        val database = schemaJson(9)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(TEST_DB)
                .callback(object : SupportSQLiteOpenHelper.Callback(9) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
                })
                .build(),
        )
        val db = helper.writableDatabase

        val entities = database.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            entity.optJSONArray("indices")?.let { indices ->
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
        }

        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
        db.execSQL(
            "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)",
            arrayOf(database.getString("identityHash")),
        )
        db.version = 9
        return db
    }

    @Test
    fun `every existing row starts settled, because history is not backfilled`() = runTest {
        createV9Database().use { db ->
            db.execSQL(
                "INSERT INTO transactions (uuid, accountId, amountMinor, direction, kind, " +
                    "occurredAt, merchantRaw, note, counterparty, source, confidence, " +
                    "createdAt, updatedAt) VALUES " +
                    "('t1', 1, 500000, 'DEBIT', 'NORMAL', 1000, 'EBL Account Transfer', NULL, " +
                    "NULL, 'SMS', 'HIGH', 1000, 1000)",
            )
        }

        val db = Room.databaseBuilder(context, KhataDatabase::class.java, TEST_DB)
            .addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
            .allowMainThreadQueries()
            .build()

        // Transfer-shaped, unpaired, and still silent: the ledger's past is left alone
        // on purpose, so nothing arrives asking about a transfer from four years ago.
        assertFalse(db.transactionDao().findById(1)!!.transferReviewPending)
        assertEquals(0, db.transactionDao().observePendingReviewCount().first())
        db.close()
    }
}
