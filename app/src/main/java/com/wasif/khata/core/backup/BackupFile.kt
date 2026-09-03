package com.wasif.khata.core.backup

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

sealed interface BackupResult {
    class Restored(val plain: ByteArray) : BackupResult
    data object NotABackup : BackupResult
    data object TooNew : BackupResult
    data object WrongPassphrase : BackupResult
}

/**
 * The backup format and its crypto, in one place because they are read together: the
 * header exists to make a refusal specific, and only the reader knows what specific
 * means.
 *
 * The header is plaintext by necessity -- it is what lets a wrong file be reported as a
 * wrong file rather than as a wrong passphrase. It carries nothing about the money.
 */
object BackupFile {

    private val MAGIC = "KHATABK1".toByteArray()
    private const val FORMAT_VERSION = 1
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256

    // ponytail: 210k iterations, the OWASP floor for PBKDF2-HMAC-SHA256 at time of
    // writing. Raise it when a backup still feels instant on the target phone.
    private const val ITERATIONS = 210_000

    /** magic + format version + schema version + salt + iv. */
    private const val HEADER_BYTES = 8 + 4 + 4 + SALT_BYTES + IV_BYTES

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    fun deriveKey(passphrase: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_BITS))
            .encoded

    /**
     * The salt a file was written with, so its key can be derived to read it. This is
     * why a restore works on a phone that has never seen this passphrase before.
     */
    fun saltOf(bytes: ByteArray): ByteArray? {
        if (bytes.size < HEADER_BYTES) return null
        if (!bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) return null
        return bytes.copyOfRange(16, 16 + SALT_BYTES)
    }

    fun write(
        plain: ByteArray,
        key: ByteArray,
        schemaVersion: Int,
        salt: ByteArray = newSalt(),
    ): ByteArray {
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        }
        val payload = cipher.doFinal(plain)

        return ByteBuffer.allocate(HEADER_BYTES + payload.size)
            .put(MAGIC)
            .putInt(FORMAT_VERSION)
            .putInt(schemaVersion)
            .put(salt)
            .put(iv)
            .put(payload)
            .array()
    }

    fun read(bytes: ByteArray, key: ByteArray, appSchemaVersion: Int): BackupResult {
        if (bytes.size < HEADER_BYTES) return BackupResult.NotABackup
        val buffer = ByteBuffer.wrap(bytes)

        val magic = ByteArray(MAGIC.size).also { buffer.get(it) }
        if (!magic.contentEquals(MAGIC)) return BackupResult.NotABackup
        if (buffer.int != FORMAT_VERSION) return BackupResult.TooNew

        // Room migrates forward and never back, so a backup from a later version would
        // either fail cryptically or corrupt.
        if (buffer.int > appSchemaVersion) return BackupResult.TooNew

        buffer.position(buffer.position() + SALT_BYTES)
        val iv = ByteArray(IV_BYTES).also { buffer.get(it) }
        val payload = ByteArray(buffer.remaining()).also { buffer.get(it) }

        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            }
            BackupResult.Restored(cipher.doFinal(payload))
        }.getOrElse {
            // GCM's tag check failing means the key is wrong or the bytes were altered.
            // Both are the same answer to the person holding the file.
            BackupResult.WrongPassphrase
        }
    }
}
