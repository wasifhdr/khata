package com.wasif.khata.core.data.migration

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.data.entity.TitleEntity
import com.wasif.khata.core.data.entity.TitleKind
import com.wasif.khata.core.data.entity.WatchEntity
import java.io.File
import kotlinx.coroutines.flow.first
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

private const val TEST_DB = "migration-12-13-test.db"
private const val SCHEMA_DIR = "schemas/com.wasif.khata.core.data.KhataDatabase"

/**
 * Builds a real version-12 database from the exported schema and migrates it, rather
 * than using MigrationTestHelper, which cannot agree with Robolectric about database
 * paths. Validation is not lost: the test reopens the migrated file through Room
 * itself, so Room's own identity-hash and column checks run against the result.
 */
@RunWith(RobolectricTestRunner::class)
class Migration12To13Test {

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

    /** Recreates schema v12 exactly as Room would have, identity hash included. */
    private fun createV12Database(): SupportSQLiteDatabase {
        val database = schemaJson(12)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(TEST_DB)
                .callback(object : SupportSQLiteOpenHelper.Callback(12) {
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
        db.version = 12
        return db
    }

    @Test
    fun `the two tables exist and Room agrees with the schema they were created from`() = runTest {
        createV12Database().close()

        // Room verifies the identity hash and every column on open. A migration whose
        // SQL drifted from the exported schema throws here rather than passing quietly.
        val db = Room.databaseBuilder(context, KhataDatabase::class.java, TEST_DB)
            .addMigrations(MIGRATION_12_13, MIGRATION_13_14)
            .build()

        val dao = db.watchlistDao()
        val titleId = dao.upsert(
            TitleEntity(
                uuid = "t-1",
                name = "Heat",
                year = 1995,
                kind = TitleKind.FILM,
                tmdbId = 949,
                tmdbRating = 7.9,
                tmdbRatingAt = 1,
                createdAt = 1,
                updatedAt = 1,
            ),
        )
        dao.upsertWatch(
            WatchEntity(uuid = "w-1", titleId = titleId, watchedAt = 2, rating = 5, createdAt = 1, updatedAt = 1),
        )

        assertEquals(TitleKind.FILM, dao.findTitle(titleId)?.kind)
        assertEquals(5.0, dao.observeSummary(titleId).first()!!.verdict!!, 0.001)
        assertNull(dao.findTitle(titleId)?.deletedAt)

        db.close()
    }

    @Test
    fun `tmdbId is unique, so the same entry cannot become two rows`() = runTest {
        createV12Database().close()
        val db = Room.databaseBuilder(context, KhataDatabase::class.java, TEST_DB)
            .addMigrations(MIGRATION_12_13, MIGRATION_13_14)
            .build()

        val dao = db.watchlistDao()
        dao.upsert(
            TitleEntity(uuid = "t-1", name = "Heat", kind = TitleKind.FILM, tmdbId = 949, createdAt = 1, updatedAt = 1),
        )
        // @Upsert resolves the unique-index conflict by updating rather than throwing,
        // which is the outcome that matters: one TMDB entry, one row, whichever way it
        // was reached. The repository resolves by tmdbId before writing anyway.
        dao.upsert(
            TitleEntity(uuid = "t-2", name = "Heat again", kind = TitleKind.FILM, tmdbId = 949, createdAt = 1, updatedAt = 1),
        )
        assertEquals(1, dao.allIdsForIndex().size)
        assertNotNull(dao.findByTmdbId(949))

        // NULLs are distinct in a SQLite unique index, so hand-typed titles are free
        // to coexist -- which is what makes the manual path usable at all.
        dao.upsert(
            TitleEntity(uuid = "t-3", name = "A home video", kind = TitleKind.FILM, createdAt = 1, updatedAt = 1),
        )
        dao.upsert(
            TitleEntity(uuid = "t-4", name = "Another home video", kind = TitleKind.FILM, createdAt = 1, updatedAt = 1),
        )
        assertEquals(3, dao.allIdsForIndex().size)

        db.close()
    }
}
