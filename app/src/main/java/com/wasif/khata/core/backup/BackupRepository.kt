package com.wasif.khata.core.backup

import android.content.Context
import androidx.sqlite.db.SimpleSQLiteQuery
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.time.KhataClock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bumped with the Room version; a backup records which one wrote it. */
const val SCHEMA_VERSION = 11

/**
 * A single rolling file would mean a corruption written last night is the only copy.
 * Seven days is enough to notice and reach back past it, at a few hundred kilobytes
 * each.
 */
private const val KEEP = 7

private const val DB_NAME = "khata.db"

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: KhataDatabase,
    private val clock: KhataClock,
) {
    fun backupDir(): File = File(context.filesDir, "backups").apply { mkdirs() }

    /** Newest by name, which sorts by the millis in it. */
    fun latest(): File? = backupDir().listFiles()?.maxByOrNull { it.name }

    /**
     * [salt] must be the salt [key] was derived from: it goes in the header, and a
     * restore derives its key from it. Writing a fresh salt here while the key came
     * from another makes every backup unopenable by passphrase -- which is the only
     * way a backup is ever opened on a replacement phone.
     */
    suspend fun backUp(key: ByteArray, salt: ByteArray): File? = withContext(Dispatchers.IO) {
        // Room runs in WAL mode, so the newest transactions live in khata.db-wal until
        // this folds them in. Without it the backup silently omits exactly the rows most
        // likely to matter, and nothing notices until a restore.
        //
        // moveToFirst, not just close: Android's query() is lazy and the statement does
        // not execute until the cursor is read. Closing it unread made this a silent
        // no-op, which is the bug the test above exists to catch.
        db.openHelper.writableDatabase
            .query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(TRUNCATE)"))
            .use { it.moveToFirst() }

        val source = context.getDatabasePath(DB_NAME)
        if (!source.exists()) return@withContext null

        val file = File(backupDir(), "khata-${clock.now()}.kbk")
        file.writeBytes(BackupFile.write(source.readBytes(), key, SCHEMA_VERSION, salt))

        backupDir().listFiles()
            ?.sortedBy { it.name }
            ?.dropLast(KEEP)
            ?.forEach { it.delete() }

        file
    }

    /**
     * Stages, swaps, and keeps the old database as khata.db.replaced. A half-swapped
     * database is the one outcome worse than no restore.
     *
     * The caller must not keep using the app afterwards: Room is holding a handle to a
     * file that has been replaced underneath it, and only a fresh process is safe.
     */
    suspend fun restore(bytes: ByteArray, key: ByteArray): BackupResult =
        withContext(Dispatchers.IO) {
            val result = BackupFile.read(bytes, key, SCHEMA_VERSION)
            if (result !is BackupResult.Restored) return@withContext result

            val target = context.getDatabasePath(DB_NAME)

            db.close()
            // The old database is copied aside before a byte of the new one is written,
            // so a write that dies halfway leaves something to go back to. That makes a
            // staging file unnecessary -- the plain bytes are already in memory.
            if (target.exists()) {
                target.copyTo(File(target.parentFile, "$DB_NAME.replaced"), overwrite = true)
            }
            target.writeBytes(result.plain)

            // The journal belongs to the database that was just replaced; leaving it
            // would have SQLite apply the old one over the new file.
            File(target.path + "-wal").delete()
            File(target.path + "-shm").delete()

            result
        }
}
