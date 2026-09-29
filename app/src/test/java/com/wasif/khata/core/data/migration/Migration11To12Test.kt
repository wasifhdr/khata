package com.wasif.khata.core.data.migration

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.entity.ServiceEntity
import com.wasif.khata.core.data.entity.ServiceItemEntity
import com.wasif.khata.core.data.entity.VehicleEntity
import java.io.File
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val TEST_DB = "migration-11-12-test.db"
private const val SCHEMA_DIR = "schemas/com.wasif.khata.core.data.KhataDatabase"

/**
 * Builds a real version-11 database from the exported schema and migrates it, rather
 * than using MigrationTestHelper, which cannot agree with Robolectric about database
 * paths. Validation is not lost: the test reopens the migrated file through Room
 * itself, so Room's own identity-hash and column checks run against the result.
 */
@RunWith(RobolectricTestRunner::class)
class Migration11To12Test {

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

    /** Recreates schema v11 exactly as Room would have, identity hash included. */
    private fun createV11Database(): SupportSQLiteDatabase {
        val database = schemaJson(11)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(TEST_DB)
                .callback(object : SupportSQLiteOpenHelper.Callback(11) {
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
        db.version = 11
        return db
    }

    @Test
    fun `the three tables exist and Room agrees with the schema they were created from`() = runTest {
        createV11Database().close()

        // Room verifies the identity hash and every column on open. A migration whose
        // SQL drifted from the exported schema throws here rather than passing quietly.
        val db = Room.databaseBuilder(context, KhataDatabase::class.java, TEST_DB)
            .addMigrations(MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
            .build()

        val dao = db.vehicleDao()
        val vehicleId = dao.upsert(
            VehicleEntity(uuid = "v-1", name = "Car", createdAt = 1, updatedAt = 1),
        )
        val serviceId = dao.upsertService(
            ServiceEntity(
                uuid = "s-1",
                vehicleId = vehicleId,
                servicedAt = 1,
                odometerKm = 42_000,
                costMinor = 500_00L,
                createdAt = 1,
                updatedAt = 1,
            ),
        )
        dao.upsertItem(
            ServiceItemEntity(
                uuid = "i-1",
                serviceId = serviceId,
                name = "Oil filter",
                costMinor = 120_00L,
                createdAt = 1,
                updatedAt = 1,
            ),
        )

        assertEquals(listOf("Oil filter"), dao.itemsFor(serviceId).map { it.name })
        assertEquals(42_000, dao.findService(serviceId)?.odometerKm)

        // The migration seeds nothing: a fresh install runs no migration at all, so
        // the vehicle row is the repository's job on both paths.
        assertEquals("Car", dao.firstVehicle()?.name)
        assertNull(dao.findService(serviceId)?.deletedAt)

        db.close()
    }
}
