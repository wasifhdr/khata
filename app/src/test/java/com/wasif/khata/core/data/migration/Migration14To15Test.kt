package com.wasif.khata.core.data.migration

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.model.TransactionKind
import java.io.File
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val TEST_DB = "migration-14-15-test.db"
private const val SCHEMA_DIR = "schemas/com.wasif.khata.core.data.KhataDatabase"

@RunWith(RobolectricTestRunner::class)
class Migration14To15Test {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun setUp() = deleteTestDb()
    @After fun tearDown() = deleteTestDb()

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
        assertNotNull("cannot locate $version.json", file)
        return JSONObject(file!!.readText()).getJSONObject("database")
    }

    private fun createV14Database(): SupportSQLiteDatabase {
        val database = schemaJson(14)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(TEST_DB)
                .callback(object : SupportSQLiteOpenHelper.Callback(14) {
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
        db.version = 14
        return db
    }

    @Test
    fun `legacy owed kinds migrate to NORMAL with owedMinor equal to amountMinor`() = runTest {
        createV14Database().use { db ->
            db.execSQL(
                "INSERT INTO transactions (uuid, accountId, amountMinor, direction, kind, " +
                    "occurredAt, merchantRaw, note, counterparty, transferReviewPending, " +
                    "source, confidence, createdAt, updatedAt) VALUES " +
                    "('t1', 1, 50000, 'DEBIT', 'LENT', 1000, NULL, NULL, 'Rafi', 0, 'MANUAL', 'HIGH', 1, 1), " +
                    "('t2', 1, 20000, 'CREDIT', 'BORROWED', 2000, NULL, NULL, 'Sadia', 0, 'MANUAL', 'HIGH', 1, 1)",
            )
        }

        val db = Room.databaseBuilder(context, KhataDatabase::class.java, TEST_DB)
            .addMigrations(MIGRATION_14_15)
            .allowMainThreadQueries()
            .build()

        val rafi = db.transactionDao().findById(1)!!
        assertEquals(TransactionKind.NORMAL, rafi.kind)
        assertEquals(TransactionDirection.DEBIT, rafi.direction)
        assertEquals(50_000L, rafi.owedMinor)

        val sadia = db.transactionDao().findById(2)!!
        assertEquals(TransactionKind.NORMAL, sadia.kind)
        assertEquals(TransactionDirection.CREDIT, sadia.direction)
        assertEquals(20_000L, sadia.owedMinor)

        db.close()
    }
}
