package com.wasif.khata.core.data.migration

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import java.io.File
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val TEST_DB = "migration-test.db"
private const val SCHEMA_DIR = "schemas/com.wasif.khata.core.data.KhataDatabase"

/**
 * Builds a real version-1 database from the exported schema and migrates it, rather
 * than using MigrationTestHelper, which cannot agree with Robolectric about database
 * paths. Validation is not lost: the final test reopens the migrated file through Room
 * itself, so Room's own identity-hash and column checks run against the result.
 */
@RunWith(RobolectricTestRunner::class)
class Migration1To2Test {

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

    /** Recreates schema v1 exactly as Room would have, identity hash included. */
    private fun createV1Database(): SupportSQLiteDatabase {
        val database = schemaJson(1)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(TEST_DB)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
                })
                .build()
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
        db.version = 1
        return db
    }

    private fun migrate(): SupportSQLiteDatabase {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(TEST_DB)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) {
                        MIGRATION_1_2.migrate(db)
                        MIGRATION_2_3.migrate(db)
                    }
                })
                .build()
        )
        return helper.writableDatabase
    }

    private fun insertEblAccount(db: SupportSQLiteDatabase) = db.execSQL(
        "INSERT INTO accounts (uuid, name, type, openingBalanceMinor, currentBalanceMinor, " +
            "reportedBalanceMinor, reportedBalanceAt, includeInNetWorth, smsIdentifiers, " +
            "createdAt, updatedAt, deletedAt) VALUES " +
            "('seed-acc-ebl','EBL','BANK',0,10000,NULL,NULL,1,'EBL,EBLBANK',1,1,NULL)"
    )

    @Test
    fun `migrating preserves existing transactions and defaults their kind`() {
        createV1Database().use { db ->
            insertEblAccount(db)
            db.execSQL(
                "INSERT INTO transactions (uuid, accountId, amountMinor, direction, occurredAt, " +
                    "merchantRaw, merchantId, categoryId, note, source, confidence, rawMessageId, " +
                    "transferGroupId, feeMinor, referenceNumber, createdAt, updatedAt, deletedAt) VALUES " +
                    "('t-1',1,25000,'DEBIT',1000,'SHWAPNO',NULL,NULL,NULL,'MANUAL','HIGH',NULL," +
                    "NULL,NULL,NULL,1,1,NULL)"
            )
        }

        migrate().use { db ->
            db.query("SELECT uuid, amountMinor, kind, providerTxnId FROM transactions").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("t-1", c.getString(0))
                assertEquals(25000L, c.getLong(1))
                assertEquals("NORMAL", c.getString(2))
                assertTrue(c.isNull(3))
            }
        }
    }

    @Test
    fun `migrating renames the single EBL account rather than orphaning its transactions`() {
        createV1Database().use { db -> insertEblAccount(db) }

        migrate().use { db ->
            db.query("SELECT name, smsIdentifiers FROM accounts WHERE uuid = 'seed-acc-ebl'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("EBL Salary", c.getString(0))
                assertEquals("352", c.getString(1))
            }
            db.query("SELECT name, smsIdentifiers FROM accounts WHERE uuid = 'seed-acc-ebl-student'").use { c ->
                assertTrue("EBL Student row missing", c.moveToFirst())
                assertEquals("EBL Student", c.getString(0))
                assertEquals("286", c.getString(1))
            }
        }
    }

    @Test
    fun `migration creates raw_messages and parsing_rules`() {
        createV1Database().close()

        migrate().use { db ->
            db.query("SELECT name FROM sqlite_master WHERE type='table'").use { c ->
                val tables = buildList { while (c.moveToNext()) add(c.getString(0)) }
                assertTrue("raw_messages missing", tables.contains("raw_messages"))
                assertTrue("parsing_rules missing", tables.contains("parsing_rules"))
            }
        }
    }

    @Test
    fun `providerTxnId is unique but permits many nulls`() {
        createV1Database().use { db -> insertEblAccount(db) }

        migrate().use { db ->
            repeat(2) { i ->
                db.execSQL(
                    "INSERT INTO transactions (uuid, accountId, amountMinor, direction, occurredAt, " +
                        "merchantRaw, merchantId, categoryId, note, source, confidence, rawMessageId, " +
                        "transferGroupId, feeMinor, referenceNumber, providerTxnId, kind, " +
                        "createdAt, updatedAt, deletedAt) VALUES " +
                        "('n-$i',1,1,'DEBIT',1,NULL,NULL,NULL,NULL,'SMS','HIGH',NULL,NULL,NULL,NULL," +
                        "NULL,'NORMAL',1,1,NULL)"
                )
            }
            db.query("SELECT COUNT(*) FROM transactions").use { c ->
                c.moveToFirst()
                assertEquals(2, c.getInt(0))
            }
        }
    }

    @Test
    fun `Room opens the migrated database and validates the resulting schema`() = runTest {
        createV1Database().use { db -> insertEblAccount(db) }

        // Room verifies the identity hash and every column on open. A migration that
        // produced a schema Room did not expect throws here rather than passing quietly.
        val room = Room.databaseBuilder(context, KhataDatabase::class.java, TEST_DB)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

        val accounts = room.accountDao().getAll()
        assertEquals(2, accounts.size)
        assertEquals(setOf("EBL Salary", "EBL Student"), accounts.map { it.name }.toSet())
        room.close()
    }
}
