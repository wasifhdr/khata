package com.wasif.khata.core.drive

import android.util.Base64
import com.wasif.khata.core.backup.BackupFile
import com.wasif.khata.core.backup.BackupResult
import com.wasif.khata.core.backup.SCHEMA_VERSION
import com.wasif.khata.core.data.dao.MediaDao
import com.wasif.khata.core.media.MediaStore
import com.wasif.khata.core.media.sha256
import com.wasif.khata.core.prefs.PreferencesRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Each photo once, ever. The Drive name is the sha256 of the *plaintext*, not of the
 * ciphertext -- a random IV per encryption means ciphertext differs every time, and
 * dedup depends on the name being stable.
 */
private const val BLOB_SUFFIX = ".kbm"

@Singleton
class MediaSync @Inject constructor(
    private val drive: DriveMedia,
    private val store: MediaStore,
    private val dao: MediaDao,
    private val preferences: PreferencesRepository,
) {
    suspend fun push(): Int {
        val prefs = preferences.preferences.first()
        // No passphrase is not an error: nothing is encrypted yet, so nothing can go
        // up. The next run after one is set picks everything up.
        val key = prefs.backupKey?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return 0
        val salt = prefs.backupSalt?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return 0

        val there = drive.blobNames().toSet()
        return dao.allLive()
            .filterNot { "${it.sha256}$BLOB_SUFFIX" in there }
            .count { media ->
                val plain = store.fileFor(media.sha256).takeIf { it.exists() }?.readBytes()
                plain != null && drive.putBlob(
                    "${media.sha256}$BLOB_SUFFIX",
                    BackupFile.write(plain, key, SCHEMA_VERSION, salt),
                )
            }
    }

    /** After a restore: fetch only what the restored rows reference and this phone lacks. */
    suspend fun pull(hashes: List<String>): Int {
        val prefs = preferences.preferences.first()
        val key = prefs.backupKey?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return 0

        return hashes
            .filterNot { store.fileFor(it).exists() }
            .count { hash -> fetch(hash, key) }
    }

    private suspend fun fetch(hash: String, key: ByteArray): Boolean {
        val envelope = drive.getBlob("$hash$BLOB_SUFFIX") ?: return false
        val plain = (BackupFile.read(envelope, key, SCHEMA_VERSION) as? BackupResult.Restored)
            ?.plain
            ?: return false

        // Re-hashing is free integrity checking: the name IS the hash, so bytes that
        // do not hash to it are not the photo the rows point at, and writing them
        // would put a corrupt file under a name everything trusts.
        if (sha256(plain) != hash) return false

        val temp = File(store.dir(), "$hash.pull.tmp")
        temp.writeBytes(plain)
        // Same atomic temp-and-rename as MediaStore, for the same reason.
        return temp.renameTo(store.fileFor(hash)).also { if (!it) temp.delete() }
    }
}
