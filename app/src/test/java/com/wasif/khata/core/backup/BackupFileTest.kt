package com.wasif.khata.core.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFileTest {

    private val passphrase = "correct horse battery staple"
    private val plain = "the whole database, pretend".toByteArray()

    private fun encrypted(schemaVersion: Int = 7): ByteArray {
        val salt = ByteArray(16) { it.toByte() }
        return BackupFile.write(plain, BackupFile.deriveKey(passphrase, salt), schemaVersion, salt)
    }

    @Test
    fun `what goes in comes back out`() {
        val bytes = encrypted()
        val key = BackupFile.deriveKey(passphrase, BackupFile.saltOf(bytes)!!)

        val result = BackupFile.read(bytes, key, appSchemaVersion = 7)

        assertArrayEquals(plain, (result as BackupResult.Restored).plain)
    }

    @Test
    fun `a wrong passphrase is refused as a wrong passphrase`() {
        val bytes = encrypted()
        val wrong = BackupFile.deriveKey("not the passphrase", BackupFile.saltOf(bytes)!!)

        // GCM authenticates, so this is a detection rather than garbage output.
        assertTrue(BackupFile.read(bytes, wrong, 7) is BackupResult.WrongPassphrase)
    }

    @Test
    fun `a file that is not a backup says so, rather than blaming the passphrase`() {
        assertTrue(BackupFile.read("just some bytes".toByteArray(), ByteArray(32), 7) is BackupResult.NotABackup)
    }

    @Test
    fun `a backup from a newer schema is refused`() {
        val bytes = encrypted(schemaVersion = 99)
        val key = BackupFile.deriveKey(passphrase, BackupFile.saltOf(bytes)!!)

        // Room migrates forward and never back, so opening this would fail cryptically
        // or corrupt. Refusing it is the only honest answer.
        assertTrue(BackupFile.read(bytes, key, appSchemaVersion = 7) is BackupResult.TooNew)
    }

    @Test
    fun `two backups of the same data are not the same bytes`() {
        // A fresh IV each time, so identical content does not produce identical files
        // -- which would leak that nothing had changed.
        assertFalse(encrypted().contentEquals(encrypted()))
    }
}
