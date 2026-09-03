# Khata Plan — Backup and Restore

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A nightly encrypted copy of the database that can be shared anywhere and read back on a different phone.

**Architecture:** One file, `BackupFile.kt`, holds the format, the crypto and the round trip — it is one flow and splitting it across a Crypto/Format/Writer trio would be three files to hold one idea. The database is checkpointed before it is read, because Room's WAL would otherwise leave the newest transactions out. Restore stages, verifies, swaps, and keeps the old file. Four tasks: the round trip, the passphrase in Settings, restore, then the nightly job and Share.

**Tech Stack:** Kotlin · `javax.crypto` (JDK) · Room · WorkManager · Hilt · DataStore · `androidx.core` FileProvider · JUnit4 + Robolectric.

**Spec:** `docs/superpowers/specs/2026-09-03-khata-backup-design.md`

## Global Constraints

Every task's requirements implicitly include this section.

- `minSdk 33`, `compileSdk` / `targetSdk 37`, package `com.wasif.khata`.
- **No new dependencies.** `javax.crypto` is in the JDK; `FileProvider` is in `androidx.core`, already present.
- **The passphrase is never stored.** Only the derived key is, in app-private DataStore.
- **A backup is checkpointed before it is read** — `PRAGMA wal_checkpoint(TRUNCATE)`. Skipping it produces a backup missing the newest transactions, and nothing notices until a restore.
- **Restore never swaps under a live database**, and never deletes the old one until the next successful launch.
- No passphrase means no backup, and Settings says so. No separate toggle.
- Comments carry a non-obvious *why*, never a restatement of *what*.
- Tests never hardcode a magic epoch-millis literal.
- **Ponytail is in force.** Reuse before writing: `SnapshotScheduler` is the shape a nightly job takes here, `IngestionWorker` shows the worker pattern, the Settings key field is the shape a secret field takes. One file for the format, not three.
- Run unit tests with: `./gradlew :app:testDebugUnitTest`
- Build with: `./gradlew :app:assembleDebug`
- `export JAVA_HOME="/e/Android/Android Studio/jbr"` and `export PATH="$JAVA_HOME/bin:$PATH"` per shell.

## File Structure

**Create:**
- `app/src/main/java/com/wasif/khata/core/backup/BackupFile.kt` — header, crypto, write, read. One flow, one file.
- `app/src/main/java/com/wasif/khata/core/backup/BackupRepository.kt` — checkpoint, produce a file, prune, restore.
- `app/src/main/java/com/wasif/khata/core/backup/BackupWorker.kt` — the nightly job and its scheduler.
- `app/src/main/res/xml/file_paths.xml`
- Tests: `BackupFileTest.kt`, `BackupRepositoryTest.kt`

**Modify:**
- `core/prefs/*` — the derived key.
- `feature/settings/SettingsScreen.kt`, `SettingsViewModel.kt` — passphrase, Back up now, Share, Restore.
- `AndroidManifest.xml` — the `FileProvider`.
- `KhataApplication.kt` — schedule the nightly job.

---

### Task 1: The round trip

Spec §2, §3. The crypto and the format, together, because they are read together.

**Files:**
- Create: `core/backup/BackupFile.kt`
- Test: `test/.../core/backup/BackupFileTest.kt`

**Interfaces:**
- Produces: `object BackupFile` with `fun deriveKey(passphrase: String, salt: ByteArray): ByteArray`, `fun write(plain: ByteArray, key: ByteArray, schemaVersion: Int): ByteArray`, `fun read(bytes: ByteArray, key: ByteArray, appSchemaVersion: Int): BackupResult`, `fun saltOf(bytes: ByteArray): ByteArray?`, and `sealed interface BackupResult`. Tasks 2–4 consume these.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.wasif.khata.core.backup

import org.junit.Assert.assertArrayEquals
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
        val notABackup = "just some bytes".toByteArray()

        assertTrue(BackupFile.read(notABackup, ByteArray(32), 7) is BackupResult.NotABackup)
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
        // A per-backup salt and IV, so identical content does not produce identical
        // files -- which would leak that nothing changed.
        assertTrue(!encrypted().contentEquals(encrypted()))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*BackupFileTest*"`
Expected: FAIL — "Unresolved reference 'BackupFile'".

- [ ] **Step 3: Write it**

```kotlin
package com.wasif.khata.core.backup

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

sealed interface BackupResult {
    data class Restored(val plain: ByteArray) : BackupResult
    data object NotABackup : BackupResult
    data object TooNew : BackupResult
    data object WrongPassphrase : BackupResult
}

/**
 * The backup format and its crypto, in one place because they are read together: the
 * header exists to make a refusal specific, and only the reader knows what specific
 * means.
 *
 * Header is plaintext by necessity — it is what lets a wrong file be reported as a
 * wrong file rather than as a wrong passphrase. It carries nothing about the money.
 */
object BackupFile {

    private val MAGIC = "KHATABK1".toByteArray()
    private const val FORMAT_VERSION = 1
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256

    // ponytail: 210k iterations, the OWASP floor for PBKDF2-HMAC-SHA256 at time of
    // writing. Raise it when a backup takes imperceptibly long on the target phone.
    private const val ITERATIONS = 210_000

    /** Header: magic, format version, schema version, salt, iv. */
    private const val HEADER_BYTES = 8 + 4 + 4 + SALT_BYTES + IV_BYTES

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    fun deriveKey(passphrase: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_BITS))
            .encoded

    /** The salt a file was written with, so its key can be derived to read it. */
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

        return ByteBuffer.allocate(HEADER_BYTES + plain.size + 16)
            .put(MAGIC)
            .putInt(FORMAT_VERSION)
            .putInt(schemaVersion)
            .put(salt)
            .put(iv)
            .put(cipher.doFinal(plain))
            .array()
    }

    fun read(bytes: ByteArray, key: ByteArray, appSchemaVersion: Int): BackupResult {
        if (bytes.size < HEADER_BYTES) return BackupResult.NotABackup
        val buffer = ByteBuffer.wrap(bytes)

        val magic = ByteArray(MAGIC.size).also { buffer.get(it) }
        if (!magic.contentEquals(MAGIC)) return BackupResult.NotABackup
        if (buffer.int != FORMAT_VERSION) return BackupResult.TooNew

        val schemaVersion = buffer.int
        // Room migrates forward, never back.
        if (schemaVersion > appSchemaVersion) return BackupResult.TooNew

        buffer.position(buffer.position() + SALT_BYTES)
        val iv = ByteArray(IV_BYTES).also { buffer.get(it) }
        val payload = ByteArray(buffer.remaining()).also { buffer.get(it) }

        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            }
            BackupResult.Restored(cipher.doFinal(payload))
        }.getOrElse {
            // GCM's tag check failing means the key is wrong or the bytes were
            // altered. Both are the same answer to the person holding the file.
            BackupResult.WrongPassphrase
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*BackupFileTest*"`
Expected: PASS, 5 tests. PBKDF2 at 210k iterations makes each test take a moment; that is the cost being paid deliberately.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/backup/BackupFile.kt \
        app/src/test/java/com/wasif/khata/core/backup/BackupFileTest.kt
git commit -m "feat(backup): a file format that refuses specifically"
```

---

### Task 2: Making one, and the checkpoint

Spec §1, §5.

**Files:**
- Create: `core/backup/BackupRepository.kt`
- Test: `test/.../core/backup/BackupRepositoryTest.kt`

**Interfaces:**
- Consumes: `BackupFile` (Task 1), `KhataDatabase`, `KhataClock`.
- Produces: `BackupRepository` with `suspend fun backUp(key: ByteArray): File?`, `suspend fun restore(bytes: ByteArray, key: ByteArray): BackupResult`, `fun latest(): File?`, `fun backupDir(): File`. Tasks 3 and 4 consume these.

- [ ] **Step 1: Write the failing test**

The checkpoint test is the one that matters: it is the failure that would otherwise appear only during a restore.

```kotlin
    @Test
    fun `a row written moments before the backup is in it`() = runTest {
        // Room runs in WAL mode: without a checkpoint this row is still in
        // khata.db-wal and the backup silently omits it. Nothing notices until a
        // restore, by which time the row is gone.
        db.accountDao().upsert(account("acc-late"))

        val file = repository.backUp(key)!!
        val restored = BackupFile.read(file.readBytes(), key, SCHEMA_VERSION)

        // SQLite stores text inline, so the uuid is findable in the raw page bytes.
        // Crude, and exactly right for this: it fails if and only if the checkpoint
        // did not run.
        val bytes = (restored as BackupResult.Restored).plain
        assertTrue(bytes.toString(Charsets.ISO_8859_1).contains("acc-late"))
    }

    @Test
    fun `seven are kept and the eighth is pruned`() = runTest {
        repeat(9) { repository.backUp(key) }

        assertEquals(7, repository.backupDir().listFiles()!!.size)
    }
```

- [ ] **Step 2: Write it**

```kotlin
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
const val SCHEMA_VERSION = 7

private const val KEEP = 7

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: KhataDatabase,
    private val clock: KhataClock,
) {
    fun backupDir(): File = File(context.filesDir, "backups").apply { mkdirs() }

    fun latest(): File? = backupDir().listFiles()?.maxByOrNull { it.name }

    suspend fun backUp(key: ByteArray): File? = withContext(Dispatchers.IO) {
        // Room runs in WAL mode, so the newest transactions live in khata.db-wal
        // until this folds them in. Without it the backup is silently missing exactly
        // the rows most likely to matter.
        db.openHelper.writableDatabase.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(TRUNCATE)")).close()

        val source = context.getDatabasePath("khata.db")
        if (!source.exists()) return@withContext null

        val file = File(backupDir(), "khata-${clock.now()}.kbk")
        file.writeBytes(BackupFile.write(source.readBytes(), key, SCHEMA_VERSION))

        // Oldest first by name, which sorts by the millis in it.
        backupDir().listFiles()
            ?.sortedBy { it.name }
            ?.dropLast(KEEP)
            ?.forEach { it.delete() }

        file
    }

    /**
     * Stages, swaps, and keeps the old database until the next successful launch. A
     * half-swapped database is worse than no restore.
     */
    suspend fun restore(bytes: ByteArray, key: ByteArray): BackupResult =
        withContext(Dispatchers.IO) {
            val result = BackupFile.read(bytes, key, SCHEMA_VERSION)
            if (result !is BackupResult.Restored) return@withContext result

            val target = context.getDatabasePath("khata.db")
            val staged = File(target.parentFile, "khata.db.staged")
            staged.writeBytes(result.plain)

            db.close()
            target.copyTo(File(target.parentFile, "khata.db.replaced"), overwrite = true)
            staged.copyTo(target, overwrite = true)
            staged.delete()
            // WAL and shm belong to the database that was just replaced; leaving them
            // would have SQLite apply the old journal over the new file.
            File(target.path + "-wal").delete()
            File(target.path + "-shm").delete()

            result
        }
}
```

- [ ] **Step 3: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests "*BackupRepositoryTest*"`
Expected: PASS. Build the repository against a real Room database opened at a file path — `Room.databaseBuilder` with a name, not `inMemoryDatabaseBuilder`, because the checkpoint and the file copy are the things under test. Delete the file in `@After`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/backup/BackupRepository.kt \
        app/src/test/java/com/wasif/khata/core/backup/BackupRepositoryTest.kt
git commit -m "feat(backup): checkpoint first, or the newest rows are missing"
```

---

### Task 3: The passphrase, and proving a round trip

Spec §2, §7.

**Files:**
- Modify: `core/prefs/KhataPreferences.kt`, `PreferencesRepository.kt`, `PreferencesRepositoryImpl.kt`, `feature/settings/SettingsScreen.kt`, `SettingsViewModel.kt`
- Test: `test/.../core/backup/BackupRepositoryTest.kt`

**Interfaces:**
- Produces: `KhataPreferences.backupKey: String?` (the derived key, Base64) and `PreferencesRepository.setBackupPassphrase(passphrase: String?)`.

- [ ] **Step 1: Write the round-trip test**

This is the load-bearing test of the whole feature.

```kotlin
    @Test
    fun `a database backed up and restored still holds its rows`() = runTest {
        db.accountDao().upsert(account("acc-survivor"))
        val file = repository.backUp(key)!!

        // Everything gone, as on a replacement phone.
        db.accountDao().getAll().forEach { db.accountDao().upsert(it.copy(deletedAt = 1)) }

        val result = repository.restore(file.readBytes(), key)

        assertTrue(result is BackupResult.Restored)
        val reopened = Room.databaseBuilder(context, KhataDatabase::class.java, "khata.db")
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            )
            .allowMainThreadQueries()
            .build()
        assertTrue(reopened.accountDao().getAll().any { it.uuid == "acc-survivor" })
        reopened.close()
    }
```

- [ ] **Step 2: Store the derived key, never the passphrase**

`KhataPreferences` gains:

```kotlin
    /**
     * The PBKDF2 output, Base64, not the passphrase. A nightly backup needs a key
     * without prompting; storing the phrase would hand a compromised device the thing
     * that unlocks every backup sitting elsewhere.
     *
     * Null means no backups are taken. There is no separate toggle.
     */
    val backupKey: String? = null,
```

`PreferencesRepository.setBackupPassphrase(passphrase: String?)` derives and stores, or clears:

```kotlin
    override suspend fun setBackupPassphrase(passphrase: String?) {
        store.edit { p ->
            if (passphrase.isNullOrBlank()) {
                p.remove(Keys.BackupKey)
                p.remove(Keys.BackupSalt)
            } else {
                // One salt, kept, so the same passphrase derives the same key every
                // night and every restore.
                val salt = p[Keys.BackupSalt]?.let { Base64.decode(it, Base64.NO_WRAP) }
                    ?: BackupFile.newSalt().also {
                        p[Keys.BackupSalt] = Base64.encodeToString(it, Base64.NO_WRAP)
                    }
                p[Keys.BackupKey] = Base64.encodeToString(
                    BackupFile.deriveKey(passphrase, salt),
                    Base64.NO_WRAP,
                )
            }
        }
    }
```

Add `override suspend fun setBackupPassphrase(passphrase: String?) = Unit` to the anonymous `PreferencesRepository` implementations in tests; the compiler names them.

**A restore on a new phone derives from the file's own salt**, not this one — `BackupFile.saltOf(bytes)`. That is why the salt is in the header.

- [ ] **Step 3: Add the Settings field**

Below the AI fallback section, a `SectionLabel("Backup")` and a passphrase field shaped exactly like `GeminiKeyField` — `PasswordVisualTransformation`, never rendering the stored value back. Its supporting text is the warning, in these words:

> "Set. **If you lose this passphrase, every backup becomes unreadable.** There is no way to recover them."

and when unset:

> "Not set. No backups are being taken."

- [ ] **Step 4: Run the suite and commit**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`

```bash
git add -A
git commit -m "feat(backup): a passphrase, and the warning it deserves"
```

---

### Task 4: Nightly, Share, and Restore

Spec §4, §5, §6.

**Files:**
- Create: `core/backup/BackupWorker.kt`, `res/xml/file_paths.xml`
- Modify: `AndroidManifest.xml`, `KhataApplication.kt`, `feature/settings/SettingsScreen.kt`, `SettingsViewModel.kt`

**Interfaces:**
- Consumes: `BackupRepository` (Task 2), `KhataPreferences.backupKey` (Task 3).

- [ ] **Step 1: The worker and its scheduler**

Copy the shape of `SnapshotWorker` exactly — a `@HiltWorker` plus a `@Singleton` scheduler with `enqueueUniquePeriodicWork` under `KEEP` and an initial delay to 02:00 Dhaka. No test, for the reason the snapshot job gave: every line is a `WorkManager` call.

```kotlin
    override suspend fun doWork(): Result {
        // No passphrase is the off switch, and it is not an error.
        val key = preferences.preferences.first().backupKey ?: return Result.success()
        return runCatching { repository.backUp(Base64.decode(key, Base64.NO_WRAP)) }
            .fold({ Result.success() }, { Result.retry() })
    }
```

Schedule it from `KhataApplication` beside `snapshots.scheduleNightly()`.

- [ ] **Step 2: The FileProvider**

`res/xml/file_paths.xml`:

```xml
<paths>
    <files-path name="backups" path="backups/" />
</paths>
```

In the manifest, inside `<application>`:

```xml
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.backups"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
```

- [ ] **Step 3: Three Settings actions**

`ActionRow`s under the Backup section:

- **"Back up now"** — enabled only when a passphrase is set; calls the repository directly.
- **"Share the latest backup"** — `FileProvider.getUriForFile`, then `Intent.ACTION_SEND` with `FLAG_GRANT_READ_URI_PERMISSION` in a chooser. Disabled when `latest()` is null.
- **"Restore from a file"** — `ActivityResultContracts.OpenDocument`, then a dialog for the passphrase, then `BackupFile.saltOf` → `deriveKey` → `repository.restore`.

The restore result maps to four distinct messages, and nothing else:

```kotlin
    val message = when (result) {
        is BackupResult.Restored -> "Restored. Khata will close so it can reopen the restored data."
        BackupResult.NotABackup -> "That is not a Khata backup file."
        BackupResult.TooNew -> "That backup was made by a newer version of Khata."
        BackupResult.WrongPassphrase -> "Wrong passphrase, or the file has been altered."
    }
```

On `Restored`, finish the activity: Room is holding a handle to a file that has been replaced underneath it, and only a fresh process is safe.

- [ ] **Step 4: Build, run the suite, verify on hardware**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`

Then, on the device:

1. Settings → Backup → set a passphrase. The warning is visible and says what it costs.
2. "Back up now". A file appears: `adb shell "run-as com.wasif.khata ls -la files/backups"`.
3. Add a transaction through the widget, back up again — two files now.
4. "Share the latest backup". A chooser appears.
5. "Restore from a file", pick the **first** backup, enter the passphrase. Khata closes; reopen it and the widget's transaction is gone — which is the restore having worked.
6. Restore again with a wrong passphrase: "Wrong passphrase, or the file has been altered."
7. Restore with any non-backup file: "That is not a Khata backup file."

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(backup): nightly, shareable, and restorable"
```

---

## Done when

- `./gradlew :app:testDebugUnitTest` passes, including the round trip and the checkpoint.
- The hardware walkthrough passes, especially step 5.
- The passphrase warning appears where the passphrase is set.
- No passphrase means no backup, and no error either.

## Deferred

Drive upload and Google Sign-In — the next plan, which uploads what this one produces. Also media, partial restore, and scheduled export to a chosen folder. All recorded in spec §8.
