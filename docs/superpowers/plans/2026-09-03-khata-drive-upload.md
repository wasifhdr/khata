# Drive Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every backup Khata writes is also uploaded to a private Google Drive folder, and any of them can be downloaded and restored from inside the app.

**Architecture:** One new dependency (`play-services-auth`) supplies the account and an hour-long access token; Drive itself is four `HttpURLConnection` calls against `drive/v3`, following `GeminiClient`'s precedent rather than adding the Google API client stack. The existing `BackupWorker` gains an upload step after it writes. Restore is untouched — Drive only supplies bytes to the `onRestore` already proven on hardware.

**Tech Stack:** Kotlin, Hilt, WorkManager, DataStore Preferences, `HttpURLConnection` + `org.json`, `com.google.android.gms:play-services-auth:22.0.0`, Robolectric + JUnit4.

**Spec:** `docs/superpowers/specs/2026-09-03-khata-drive-upload-design.md`

## Global Constraints

- **One new dependency only:** `com.google.android.gms:play-services-auth:22.0.0`. Do **not** add `google-api-services-drive` or any Google API client library. Drive is plain HTTP.
- **Scope string, exactly one:** `https://www.googleapis.com/auth/drive.file`. Never `drive`, never `drive.appdata`.
- **Colour literals are forbidden outside `core/ui/theme`** (DESIGN.md §1.1, test-enforced). Use `MaterialTheme.colorScheme`.
- **Every Material role is set explicitly** (DESIGN.md §1.6). An unset text colour renders near-black on Khata's dark ground — this has already caused one bug on the Insights screen.
- **The local backup is written first and never depends on the upload.** A failed upload must leave the seven local files untouched and must not prune anything.
- **Nothing is stored but the account name and the folder id.** No access token, no refresh token, no passphrase.
- Khata is single-user, sideloaded, Asia/Dhaka, BDT. Existing tests and public signatures keep working.
- Ponytail is active: shortest diff that works, no abstraction with one implementation, stdlib before dependencies.

---

## File Structure

**Created**

| File | Responsibility |
|---|---|
| `app/src/main/java/com/wasif/khata/core/drive/DriveWire.kt` | Pure functions with no Android and no network: the multipart body, the two response parsers, the prune decision. Everything that can be unit-tested lives here |
| `app/src/main/java/com/wasif/khata/core/drive/DriveAuth.kt` | The only file that touches Play Services. Turns a stored account name into a token, and runs the interactive consent |
| `app/src/main/java/com/wasif/khata/core/drive/DriveClient.kt` | The four HTTP calls, plus the two narrow `fun interface`s its consumers depend on |
| `app/src/test/java/com/wasif/khata/core/drive/DriveWireTest.kt` | Tests for `DriveWire` |
| `app/src/test/java/com/wasif/khata/core/backup/BackupWorkerTest.kt` | The worker's upload behaviour, against a fake uploader |
| `keystore.properties` | Local, gitignored. Points at the signing keystore |

**Modified**

| File | Change |
|---|---|
| `app/src/main/AndroidManifest.xml:10` | Add `INTERNET` |
| `gradle/libs.versions.toml` | `playServicesAuth` version and library entry |
| `app/build.gradle.kts` | `signingConfig` used by both build types; the new dependency |
| `.gitignore` | `keystore.properties`, `*.jks` |
| `core/prefs/KhataPreferences.kt` | Four Drive fields |
| `core/prefs/PreferencesRepository.kt` | Four setters |
| `core/prefs/PreferencesRepositoryImpl.kt` | Four keys, their mapping, their setters |
| `core/backup/BackupWorker.kt` | Upload after a successful backup |
| `feature/settings/SettingsViewModel.kt` | Connect, disconnect, status, Drive list, Drive download |
| `feature/settings/SettingsScreen.kt` | A Drive row in `BackupSection`; Drive backups in the restore chooser |
| `app/src/test/java/com/wasif/khata/core/prefs/PreferencesRepositoryTest.kt` | Drive fields round-trip; disconnect clears everything |

---

### Task 1: Make the network reachable, and the build signable

Nothing in this feature can work until two things are true: the app is allowed to open a socket, and the APK is signed by the certificate registered with Google.

**The `INTERNET` fix is not cosmetic.** The merged manifest currently has no `INTERNET` permission, so every network call in the app fails. `GeminiClient` catches `IOException` and returns null, and a blocked socket surfaces as one — so the AI fallback fails silently and looks exactly like Gemini declining to answer. This task repairs that too.

**Files:**
- Modify: `app/src/main/AndroidManifest.xml:10`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `.gitignore`
- Create: `keystore.properties`

**Interfaces:**
- Consumes: nothing.
- Produces: a debug APK signed with SHA-1 `6C:8E:26:F4:50:9C:46:C8:B2:C7:15:7A:C0:6C:79:E4:3F:B0:7D:54`, and `libs.play.services.auth` on the compile classpath.

- [ ] **Step 1: Add the permission**

In `app/src/main/AndroidManifest.xml`, beside the existing SMS permissions:

```xml
    <!-- Absent until now, which silently disabled the AI fallback: a blocked
         socket raises an IOException, which GeminiClient catches and turns into
         "no rule", indistinguishable from the model declining. -->
    <uses-permission android:name="android.permission.INTERNET" />
```

- [ ] **Step 2: Verify it reached the merged manifest**

```bash
./gradlew :app:processDebugMainManifest
```

Then:

```bash
grep -c "android.permission.INTERNET" app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml
```

Expected: `1`. If it is `0`, the edit landed in the wrong manifest.

- [ ] **Step 3: Commit the permission on its own**

It fixes an existing bug and should be reviewable without the build changes.

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "fix(sms): the AI fallback never had permission to reach the network

INTERNET was missing from the manifest, so every socket in the app failed.
GeminiClient catches IOException and answers null, and a blocked socket
raises one -- so the fallback failed silently and read as the model
declining. Nothing caught it because the only verified case was the kill
switch, which looks the same.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

- [ ] **Step 4: Add the dependency to the version catalog**

In `gradle/libs.versions.toml`, under `[versions]`:

```toml
playServicesAuth = "22.0.0"
```

Under `[libraries]`:

```toml
play-services-auth = { module = "com.google.android.gms:play-services-auth", version.ref = "playServicesAuth" }
```

- [ ] **Step 5: Add it to the app, beside the other network-facing code**

In `app/build.gradle.kts`, in `dependencies`, after `implementation(libs.androidx.datastore.preferences)`:

```kotlin
  // The account and an hour-long Drive token. Deliberately NOT
  // google-api-services-drive: Drive itself is four HttpURLConnection calls,
  // and the client library would be the largest thing in this app.
  implementation(libs.play.services.auth)
```

- [ ] **Step 6: Create `keystore.properties`**

At the repository root. Use the password chosen when the keystore was generated:

```properties
storeFile=C:/Users/Wasif/.android/khata-release.jks
storePassword=<the password you chose>
keyAlias=khata
keyPassword=<the same password>
```

Forward slashes, even on Windows — Gradle reads this as a properties file, where a backslash is an escape character.

- [ ] **Step 7: Keep it out of the repository**

Append to `.gitignore`:

```gitignore
# Signing. *.keystore above does not match a .jks.
keystore.properties
*.jks
```

- [ ] **Step 8: Verify it is actually ignored**

```bash
git status --porcelain --ignored | grep keystore.properties
```

Expected: a line beginning `!!`, meaning ignored. If it shows `??` the file is untracked but *not* ignored, and committing it would publish the signing password.

- [ ] **Step 9: Wire the signing config**

In `app/build.gradle.kts`, above `android {`:

```kotlin
import java.util.Properties

// Drive authorises by package name plus signing certificate, so both build types
// must be signed by the key registered in the Cloud Console. Absent on a machine
// without the keystore, in which case the build still works and only Drive does
// not -- better than a build that cannot run at all.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
```

Inside `android { }`, before `buildTypes`:

```kotlin
    signingConfigs {
        create("khata") {
            if (keystoreProperties.isNotEmpty()) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }
```

Replace the existing `buildTypes { }` block with:

```kotlin
    buildTypes {
        getByName("debug") {
            if (keystoreProperties.isNotEmpty()) signingConfig = signingConfigs.getByName("khata")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProperties.isNotEmpty()) signingConfig = signingConfigs.getByName("khata")
        }
    }
```

- [ ] **Step 10: Build and prove the APK carries the right certificate**

```bash
./gradlew :app:assembleDebug
```

Then print the signer, using the JDK already on this machine:

```bash
"C:\Users\Wasif\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2\bin\keytool.exe" -printcert -jarfile app/build/outputs/apk/debug/app-debug.apk
```

Expected: a `SHA1:` line reading exactly

```
6C:8E:26:F4:50:9C:46:C8:B2:C7:15:7A:C0:6C:79:E4:3F:B0:7D:54
```

If it prints a different fingerprint, the build fell back to the debug keystore — `keystore.properties` is missing, misspelled, or has a Windows-style path with backslashes.

- [ ] **Step 11: Reinstall, because the signing key changed**

Android refuses an update signed by a different key, so the existing install must go. **This wipes Khata's data on the device.** Take a backup first if the emulator holds anything worth keeping.

```bash
adb uninstall com.wasif.khata
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 12: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts .gitignore
git commit -m "build(drive): one key signs both build types, and play-services-auth

Drive authorises by package name plus signing certificate, so the app needs
a key that does not change. There was none: release was unsigned and debug
used the generated debug keystore.

The config is skipped when keystore.properties is absent, so a checkout
without the keystore still builds -- only Drive stops working, which beats a
build that cannot run.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 2: The wire format, which is where the silent corruption lives

Everything in this task is a pure function with no Android and no network, which is exactly why it can carry the tests that matter.

**The load-bearing one is `multipartBody`.** A backup is AES-GCM ciphertext — uniformly random bytes, most of them invalid UTF-8. Building the request body by string concatenation would replace every invalid sequence with U+FFFD, producing an upload of roughly the right size and entirely the wrong bytes. Nothing would notice until a restore failed, months later. This is the same failure shape as the WAL checkpoint and the backup salt, and it gets the same treatment: a test that fails if and only if the bytes are mangled.

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/drive/DriveWire.kt`
- Test: `app/src/test/java/com/wasif/khata/core/drive/DriveWireTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `data class DriveFile(val id: String, val name: String)`
  - `fun multipartBody(metadata: String, content: ByteArray, boundary: String): ByteArray`
  - `fun parseFileList(json: String): List<DriveFile>`
  - `fun parseFileId(json: String): String?`
  - `fun toDelete(files: List<DriveFile>, keep: Int): List<DriveFile>`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/wasif/khata/core/drive/DriveWireTest.kt`:

```kotlin
package com.wasif.khata.core.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric only for org.json, which is a stub in a plain JVM test. */
@RunWith(RobolectricTestRunner::class)
class DriveWireTest {

    @Test
    fun `the payload survives the multipart body byte for byte`() {
        // A backup is AES-GCM output: uniformly random, mostly invalid UTF-8.
        // Building this body as a String would replace each invalid sequence with
        // U+FFFD and upload a file of roughly the right size and entirely the wrong
        // bytes -- undetectable until a restore fails. These four bytes are chosen
        // to break exactly that: a null, a CR, an LF, and a lone 0xFF.
        val payload = byteArrayOf(0x00, 0x0D, 0x0A, 0xFF.toByte())

        val body = multipartBody("""{"name":"x"}""", payload, "BOUND")

        val marker = "application/octet-stream\r\n\r\n".toByteArray()
        val start = body.indexOfSub(marker) + marker.size
        assertEquals(
            payload.toList(),
            body.copyOfRange(start, start + payload.size).toList(),
        )
    }

    @Test
    fun `the multipart body opens and closes on the boundary`() {
        val body = multipartBody("{}", byteArrayOf(1), "BOUND").toString(Charsets.ISO_8859_1)

        assertEquals(true, body.startsWith("--BOUND\r\n"))
        assertEquals(true, body.endsWith("\r\n--BOUND--"))
    }

    @Test
    fun `a file list becomes ids and names`() {
        val json = """{"files":[{"id":"a1","name":"khata-2.kbk"},{"id":"b2","name":"khata-1.kbk"}]}"""

        assertEquals(
            listOf(DriveFile("a1", "khata-2.kbk"), DriveFile("b2", "khata-1.kbk")),
            parseFileList(json),
        )
    }

    @Test
    fun `a malformed or empty list is empty, not a crash`() {
        // Drive answers with an error object rather than a file list when a token
        // has expired. A crash here would take down the worker.
        assertEquals(emptyList<DriveFile>(), parseFileList("""{"error":{"code":401}}"""))
        assertEquals(emptyList<DriveFile>(), parseFileList("not json at all"))
        assertEquals(emptyList<DriveFile>(), parseFileList("""{"files":[]}"""))
    }

    @Test
    fun `an entry missing an id is dropped rather than faked`() {
        val json = """{"files":[{"name":"khata-1.kbk"},{"id":"b2","name":"khata-2.kbk"}]}"""

        assertEquals(listOf(DriveFile("b2", "khata-2.kbk")), parseFileList(json))
    }

    @Test
    fun `a created file yields its id`() {
        assertEquals("folder-1", parseFileId("""{"id":"folder-1","name":"Khata"}"""))
        assertNull(parseFileId("""{"error":{"code":403}}"""))
        assertNull(parseFileId("garbage"))
    }

    @Test
    fun `pruning keeps seven and names the eighth`() {
        // Names sort by the millis in them, exactly as BackupRepository prunes
        // locally -- the two must agree or Drive drifts from the phone.
        val files = (1..9).map { DriveFile("id-$it", "khata-100$it.kbk") }

        val doomed = toDelete(files, keep = 7)

        assertEquals(listOf("khata-1001.kbk", "khata-1002.kbk"), doomed.map { it.name })
    }

    @Test
    fun `pruning below the limit deletes nothing`() {
        val files = (1..3).map { DriveFile("id-$it", "khata-100$it.kbk") }

        assertEquals(emptyList<DriveFile>(), toDelete(files, keep = 7))
    }

    private fun ByteArray.indexOfSub(needle: ByteArray): Int =
        (0..size - needle.size).first { i ->
            needle.indices.all { this[i + it] == needle[it] }
        }
}
```

- [ ] **Step 2: Run them to verify they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "*DriveWireTest*"
```

Expected: FAIL — unresolved references to `multipartBody`, `parseFileList`, `parseFileId`, `toDelete` and `DriveFile`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/wasif/khata/core/drive/DriveWire.kt`:

```kotlin
package com.wasif.khata.core.drive

import org.json.JSONObject

/** A backup as Drive sees it. */
data class DriveFile(val id: String, val name: String)

/**
 * A `multipart/related` body: JSON metadata, then the file.
 *
 * Assembled as bytes rather than as a String on purpose. The payload is AES-GCM
 * ciphertext -- uniformly random, and mostly invalid UTF-8 -- so a String round trip
 * would replace each invalid sequence with U+FFFD and upload something the right
 * size and the wrong bytes. That failure is invisible until a restore, which is the
 * worst moment to discover it.
 */
fun multipartBody(metadata: String, content: ByteArray, boundary: String): ByteArray {
    val head = (
        "--$boundary\r\n" +
            "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
            metadata + "\r\n" +
            "--$boundary\r\n" +
            "Content-Type: application/octet-stream\r\n\r\n"
        ).toByteArray()
    val tail = "\r\n--$boundary--".toByteArray()

    return head + content + tail
}

/**
 * Empty on anything that is not a file list. Drive answers an expired token with an
 * error object rather than a list, and a throw here would take the worker down with
 * it -- a retry is the right answer to that, and an empty list produces one.
 */
fun parseFileList(json: String): List<DriveFile> = runCatching {
    val files = JSONObject(json).optJSONArray("files") ?: return emptyList()
    (0 until files.length()).mapNotNull { i ->
        val entry = files.optJSONObject(i) ?: return@mapNotNull null
        val id = entry.optString("id").ifBlank { return@mapNotNull null }
        val name = entry.optString("name").ifBlank { return@mapNotNull null }
        DriveFile(id, name)
    }
}.getOrElse { emptyList() }

/** The id of a just-created file or folder; null if the response was not one. */
fun parseFileId(json: String): String? =
    runCatching { JSONObject(json).optString("id").ifBlank { null } }.getOrNull()

/**
 * Sorted by name and dropping the newest [keep], which matches BackupRepository's
 * local prune exactly. The two must agree, or Drive drifts from the phone.
 */
fun toDelete(files: List<DriveFile>, keep: Int): List<DriveFile> =
    files.sortedBy { it.name }.dropLast(keep)
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "*DriveWireTest*"
```

Expected: PASS, 8 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/drive/DriveWire.kt app/src/test/java/com/wasif/khata/core/drive/DriveWireTest.kt
git commit -m "feat(drive): the wire format, and the test that catches mangled bytes

multipart/related assembled as bytes, never as a String. A backup is AES-GCM
output -- uniformly random and mostly invalid UTF-8 -- so a String round trip
would swap every invalid sequence for U+FFFD and upload a file of roughly the
right size and entirely the wrong bytes, undetectable until a restore failed.
The first test fails if and only if that happens.

Parsing answers empty rather than throwing: Drive returns an error object for
an expired token, and a throw would take the worker down when a retry is the
right answer.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 3: Four preferences, and a disconnect that really disconnects

**Files:**
- Modify: `app/src/main/java/com/wasif/khata/core/prefs/KhataPreferences.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/prefs/PreferencesRepository.kt`
- Modify: `app/src/main/java/com/wasif/khata/core/prefs/PreferencesRepositoryImpl.kt`
- Test: `app/src/test/java/com/wasif/khata/core/prefs/PreferencesRepositoryTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces, on `KhataPreferences`: `driveAccount: String?`, `driveFolderId: String?`, `driveLastUploadAt: Long?`, `driveNeedsReconnect: Boolean`. On `PreferencesRepository`: `suspend fun setDriveAccount(email: String?)`, `suspend fun setDriveFolderId(id: String?)`, `suspend fun setDriveUploaded(at: Long)`, `suspend fun setDriveNeedsReconnect()`.

- [ ] **Step 1: Write the failing tests**

Append to `app/src/test/java/com/wasif/khata/core/prefs/PreferencesRepositoryTest.kt`, inside the existing class:

```kotlin
    @Test
    fun `drive settings round trip`() = runTest {
        repository.setDriveAccount("someone@gmail.com")
        repository.setDriveFolderId("folder-1")
        repository.setDriveUploaded(1_700_000_000_000L)

        val prefs = repository.preferences.first()

        assertEquals("someone@gmail.com", prefs.driveAccount)
        assertEquals("folder-1", prefs.driveFolderId)
        assertEquals(1_700_000_000_000L, prefs.driveLastUploadAt)
        assertEquals(false, prefs.driveNeedsReconnect)
    }

    @Test
    fun `a successful upload clears the reconnect flag`() = runTest {
        repository.setDriveAccount("someone@gmail.com")
        repository.setDriveNeedsReconnect()
        assertEquals(true, repository.preferences.first().driveNeedsReconnect)

        repository.setDriveUploaded(1_700_000_000_000L)

        // An upload that worked is proof the grant is fine. Leaving the warning up
        // would tell the user to fix something that is not broken.
        assertEquals(false, repository.preferences.first().driveNeedsReconnect)
    }

    @Test
    fun `disconnecting leaves nothing behind`() = runTest {
        repository.setDriveAccount("someone@gmail.com")
        repository.setDriveFolderId("folder-1")
        repository.setDriveUploaded(1_700_000_000_000L)
        repository.setDriveNeedsReconnect()

        repository.setDriveAccount(null)

        // A stale folder id would have the next connection upload into a folder the
        // new account cannot see, and a stale timestamp would claim an offsite copy
        // that is no longer reachable.
        val prefs = repository.preferences.first()
        assertNull(prefs.driveAccount)
        assertNull(prefs.driveFolderId)
        assertNull(prefs.driveLastUploadAt)
        assertEquals(false, prefs.driveNeedsReconnect)
    }
```

If `assertNull` is not already imported in that file, add `import org.junit.Assert.assertNull`.

- [ ] **Step 2: Run them to verify they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "*PreferencesRepositoryTest*"
```

Expected: FAIL — unresolved references to `setDriveAccount` and the `drive*` properties.

- [ ] **Step 3: Add the fields to the model**

In `KhataPreferences.kt`, after `backupSalt`:

```kotlin
    /**
     * The Google account backups are uploaded to; null means Drive is off. The
     * account name is the only thing stored -- no token, no refresh token. Play
     * Services holds the grant, so a stolen phone yields no lasting Drive access.
     */
    val driveAccount: String? = null,
    /** The Khata folder in Drive. Recreated if it 404s, so a stale id is not fatal. */
    val driveFolderId: String? = null,
    /** When the last upload succeeded. Null with an account set means none yet. */
    val driveLastUploadAt: Long? = null,
    /**
     * Set when a background run found the grant gone. A worker cannot show consent,
     * so it records this and Settings offers the reconnection, where an Activity
     * exists to run it.
     */
    val driveNeedsReconnect: Boolean = false,
```

And in `companion object Default`, after `backupSalt = null,`:

```kotlin
            driveAccount = null,
            driveFolderId = null,
            driveLastUploadAt = null,
            driveNeedsReconnect = false,
```

- [ ] **Step 4: Add the setters to the interface**

In `PreferencesRepository.kt`, after `setBackupPassphrase`:

```kotlin
    suspend fun setDriveAccount(email: String?)
    suspend fun setDriveFolderId(id: String?)
    suspend fun setDriveUploaded(at: Long)
    suspend fun setDriveNeedsReconnect()
```

- [ ] **Step 5: Implement them**

In `PreferencesRepositoryImpl.kt`, inside `object Keys`, after `BackupSalt`:

```kotlin
        val DriveAccount = stringPreferencesKey("drive_account")
        val DriveFolderId = stringPreferencesKey("drive_folder_id")
        val DriveLastUploadAt = longPreferencesKey("drive_last_upload_at")
        val DriveNeedsReconnect = intPreferencesKey("drive_needs_reconnect")
```

In the `map { p -> KhataPreferences(...) }` block, after `backupSalt = p[Keys.BackupSalt],`:

```kotlin
                driveAccount = p[Keys.DriveAccount],
                driveFolderId = p[Keys.DriveFolderId],
                driveLastUploadAt = p[Keys.DriveLastUploadAt],
                driveNeedsReconnect = p[Keys.DriveNeedsReconnect] == 1,
```

And after `setBackupPassphrase`:

```kotlin
    /**
     * Clearing the account clears everything that depended on it. A stale folder id
     * would have the next connection upload into a folder the new account cannot
     * see, and a stale timestamp would claim an offsite copy that is gone.
     */
    override suspend fun setDriveAccount(email: String?) {
        store.edit { p ->
            if (email.isNullOrBlank()) {
                p.remove(Keys.DriveAccount)
                p.remove(Keys.DriveFolderId)
                p.remove(Keys.DriveLastUploadAt)
                p.remove(Keys.DriveNeedsReconnect)
            } else {
                p[Keys.DriveAccount] = email
                p.remove(Keys.DriveNeedsReconnect)
            }
        }
    }

    override suspend fun setDriveFolderId(id: String?) {
        store.edit { p -> if (id == null) p.remove(Keys.DriveFolderId) else p[Keys.DriveFolderId] = id }
    }

    /** An upload that worked is proof the grant is fine, so the warning comes down. */
    override suspend fun setDriveUploaded(at: Long) {
        store.edit { p ->
            p[Keys.DriveLastUploadAt] = at
            p.remove(Keys.DriveNeedsReconnect)
        }
    }

    override suspend fun setDriveNeedsReconnect() {
        store.edit { it[Keys.DriveNeedsReconnect] = 1 }
    }
```

- [ ] **Step 6: Run the tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "*PreferencesRepositoryTest*"
```

Expected: PASS, once the other implementers compile. Three test files stand up an
anonymous `PreferencesRepository` and each needs the four new methods added:

- `app/src/test/java/com/wasif/khata/feature/settings/SettingsViewModelTest.kt:52`
- `app/src/test/java/com/wasif/khata/MainViewModelTest.kt`
- `app/src/test/java/com/wasif/khata/core/permission/SmsPermissionRepositoryTest.kt`

In each, after `setBackupPassphrase`, add:

```kotlin
        override suspend fun setDriveAccount(email: String?) = Unit
        override suspend fun setDriveFolderId(id: String?) = Unit
        override suspend fun setDriveUploaded(at: Long) = Unit
        override suspend fun setDriveNeedsReconnect() = Unit
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/prefs/ app/src/test/java/com/wasif/khata/core/prefs/
git commit -m "feat(drive): remember the account, the folder, and when it last worked

Four preferences, and two couplings that matter: clearing the account clears
the folder id and timestamp with it, and a successful upload clears the
reconnect warning. Otherwise a disconnected app claims an offsite copy it
cannot reach, and a working one nags about a grant that is fine.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 4: The token, and the only file that knows about Play Services

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/drive/DriveAuth.kt`

**Interfaces:**
- Consumes: `PreferencesRepository.setDriveAccount`, `KhataPreferences.driveAccount` (Task 3).
- Produces:
  - `const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"`
  - `sealed interface TokenResult { data class Token(val value: String); data object NotConnected; data object NeedsReconnect }`
  - `class DriveAuth`, with `suspend fun token(): TokenResult`, `suspend fun beginConnect(activity: Activity): IntentSender?`, `suspend fun completeConnect(data: Intent?): Boolean`

- [ ] **Step 1: Write it**

There is no unit test for the Play Services calls — they need a real device with Google services, exactly as the live Gemini call is unverified in unit tests. The one branch that *is* testable without them is covered in Task 6, where the worker skips because nothing is connected.

Create `app/src/main/java/com/wasif/khata/core/drive/DriveAuth.kt`:

```kotlin
package com.wasif.khata.core.drive

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.wasif.khata.core.prefs.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

sealed interface TokenResult {
    /** Valid for about an hour, and never persisted. */
    data class Token(val value: String) : TokenResult
    data object NotConnected : TokenResult
    data object NeedsReconnect : TokenResult
}

/**
 * The only file in Khata that touches Play Services.
 *
 * Two paths into one AuthorizationClient. Consent needs an Activity and happens in
 * Settings; the nightly upload has none, which is why the Context overload of
 * getAuthorizationClient matters -- without it an unattended upload is impossible.
 */
@Singleton
class DriveAuth @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: PreferencesRepository,
) {

    private fun request(account: Account?) = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
        .apply { account?.let(::setAccount) }
        .build()

    /**
     * A token for the stored account, without UI. Called from the worker.
     *
     * setAccount is what stops an account picker appearing at 02:00, where nothing
     * could answer it.
     */
    suspend fun token(): TokenResult = withContext(Dispatchers.IO) {
        val email = preferences.preferences.first().driveAccount
            ?: return@withContext TokenResult.NotConnected

        val result = runCatching {
            Tasks.await(
                Identity.getAuthorizationClient(context)
                    .authorize(request(Account(email, "com.google"))),
            )
        }.getOrElse { return@withContext TokenResult.NeedsReconnect }

        // hasResolution means consent is needed again, and a worker cannot show it.
        val token = result.accessToken
        if (result.hasResolution() || token.isNullOrBlank()) {
            TokenResult.NeedsReconnect
        } else {
            TokenResult.Token(token)
        }
    }

    /**
     * Null when access was already granted -- in which case the account has just been
     * stored and there is nothing to show. Otherwise the sender the caller must
     * launch for consent.
     */
    suspend fun beginConnect(activity: Activity): IntentSender? = withContext(Dispatchers.IO) {
        val result = runCatching {
            Tasks.await(Identity.getAuthorizationClient(activity).authorize(request(null)))
        }.getOrElse { return@withContext null }

        if (result.hasResolution()) {
            result.pendingIntent?.intentSender
        } else {
            remember(result)
            null
        }
    }

    /** Stores the account from a completed consent. False if it did not complete. */
    suspend fun completeConnect(data: Intent?): Boolean = withContext(Dispatchers.IO) {
        val result = runCatching {
            Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
        }.getOrElse { return@withContext false }

        remember(result)
    }

    private suspend fun remember(result: AuthorizationResult): Boolean {
        val email = result.toGoogleSignInAccount()?.email ?: return false
        preferences.setDriveAccount(email)
        return true
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. A failure on `getAuthorizationClient(context)` would mean the dependency from Task 1 did not resolve.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/drive/DriveAuth.kt
git commit -m "feat(drive): an account, and a token that lasts an hour

The only file that touches Play Services. Consent runs from Settings where an
Activity exists; the nightly upload uses the Context overload of
getAuthorizationClient, which is what makes an unattended run possible at all,
with setAccount so no picker appears at 02:00 where nothing could answer it.

Only the account name is stored. Play Services holds the grant, so a stolen
phone yields no lasting Drive access.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 5: The four calls

**Files:**
- Create: `app/src/main/java/com/wasif/khata/core/drive/DriveClient.kt`

**Interfaces:**
- Consumes: `DriveWire` (Task 2), `DriveAuth` and `TokenResult` (Task 4), `PreferencesRepository.setDriveFolderId` / `setDriveUploaded` / `setDriveNeedsReconnect` (Task 3), `KhataClock`.
- Produces:
  - `enum class UploadOutcome { UPLOADED, SKIPPED, FAILED }`
  - `fun interface DriveUploader { suspend fun upload(file: File): UploadOutcome }`
  - `interface DriveBackups { suspend fun list(): List<DriveFile>; suspend fun download(id: String): ByteArray? }`
  - `class DriveClient : DriveUploader, DriveBackups`

- [ ] **Step 1: Write it**

Create `app/src/main/java/com/wasif/khata/core/drive/DriveClient.kt`:

```kotlin
package com.wasif.khata.core.drive

import com.wasif.khata.core.prefs.PreferencesRepository
import com.wasif.khata.core.time.KhataClock
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class UploadOutcome {
    UPLOADED,

    /** Nothing to do: Drive is not connected. Not an error, and not a retry. */
    SKIPPED,

    /** Worth another go: no network, a bad gateway, a quota. */
    FAILED,
}

/** The worker's whole view of Drive, so its tests need neither network nor account. */
fun interface DriveUploader {
    suspend fun upload(file: File): UploadOutcome
}

/** Settings' view of Drive: what is up there, and the bytes of one of them. */
interface DriveBackups {
    suspend fun list(): List<DriveFile>
    suspend fun download(id: String): ByteArray?
}

private const val FILES = "https://www.googleapis.com/drive/v3/files"
private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
private const val FOLDER_MIME = "application/vnd.google-apps.folder"
private const val FOLDER_NAME = "Khata"

/** The same seven the phone keeps, so Drive does not drift from it. */
private const val KEEP = 7

@Singleton
class DriveClient @Inject constructor(
    private val auth: DriveAuth,
    private val preferences: PreferencesRepository,
    private val clock: KhataClock,
) : DriveUploader, DriveBackups {

    override suspend fun upload(file: File): UploadOutcome = withContext(Dispatchers.IO) {
        val token = when (val t = auth.token()) {
            is TokenResult.Token -> t.value
            TokenResult.NotConnected -> return@withContext UploadOutcome.SKIPPED
            TokenResult.NeedsReconnect -> {
                preferences.setDriveNeedsReconnect()
                // Not FAILED: retrying cannot fix a grant, only the user can.
                return@withContext UploadOutcome.SKIPPED
            }
        }

        val folder = folderId(token) ?: return@withContext UploadOutcome.FAILED

        val metadata = JSONObject()
            .put("name", file.name)
            .put("parents", org.json.JSONArray().put(folder))
            .toString()
        val boundary = "khata-${clock.now()}"

        val body = multipartBody(metadata, file.readBytes(), boundary)
        val created = post(
            url = UPLOAD,
            token = token,
            contentType = "multipart/related; boundary=$boundary",
            body = body,
        ) ?: return@withContext UploadOutcome.FAILED

        if (parseFileId(created) == null) return@withContext UploadOutcome.FAILED

        preferences.setDriveUploaded(clock.now())

        // Only after the new copy is up. Pruning first would trade seven backups for
        // six and a failure.
        toDelete(listFiles(token, folder), KEEP).forEach { delete(token, it.id) }

        UploadOutcome.UPLOADED
    }

    override suspend fun list(): List<DriveFile> = withContext(Dispatchers.IO) {
        val token = (auth.token() as? TokenResult.Token)?.value ?: return@withContext emptyList()
        val folder = folderId(token) ?: return@withContext emptyList()
        listFiles(token, folder).sortedByDescending { it.name }
    }

    override suspend fun download(id: String): ByteArray? = withContext(Dispatchers.IO) {
        val token = (auth.token() as? TokenResult.Token)?.value ?: return@withContext null
        get(url = "$FILES/$id?alt=media", token = token)
    }

    /**
     * The cached folder, or a new one. A 404 means it was moved or deleted in Drive,
     * and a stale id must not be a dead feature.
     */
    private suspend fun folderId(token: String): String? {
        val cached = preferences.preferences.first().driveFolderId
        if (cached != null && exists(token, cached)) return cached

        val body = JSONObject()
            .put("name", FOLDER_NAME)
            .put("mimeType", FOLDER_MIME)
            .toString()
        val created = post(FILES, token, "application/json; charset=UTF-8", body.toByteArray())
            ?: return null

        return parseFileId(created)?.also { preferences.setDriveFolderId(it) }
    }

    private fun exists(token: String, id: String): Boolean =
        get("$FILES/$id?fields=id", token) != null

    private fun listFiles(token: String, folder: String): List<DriveFile> {
        val query = URLEncoder.encode("'$folder' in parents and trashed = false", "UTF-8")
        val fields = URLEncoder.encode("files(id,name)", "UTF-8")
        val json = get("$FILES?q=$query&fields=$fields&pageSize=100", token) ?: return emptyList()
        return parseFileList(json.toString(Charsets.UTF_8))
    }

    private fun delete(token: String, id: String) {
        open("$FILES/$id", token)?.let { connection ->
            try {
                connection.requestMethod = "DELETE"
                connection.responseCode
            } catch (e: IOException) {
                // A backup that outlives its turn is clutter, not a fault. The next
                // upload prunes it.
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun get(url: String, token: String): ByteArray? {
        val connection = open(url, token) ?: return null
        return try {
            if (connection.responseCode !in 200..299) null
            else connection.inputStream.use { it.readBytes() }
        } catch (e: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun post(url: String, token: String, contentType: String, body: ByteArray): String? {
        val connection = open(url, token) ?: return null
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", contentType)
            connection.outputStream.use { it.write(body) }
            if (connection.responseCode !in 200..299) null
            else connection.inputStream.bufferedReader().readText()
        } catch (e: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, token: String): HttpURLConnection? = runCatching {
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $token")
        }
    }.getOrNull()
}
```

- [ ] **Step 2: Bind the interfaces for Hilt**

`DriveClient` is a concrete class implementing both interfaces, so Hilt needs to be
told which one to hand out. Add to
`app/src/main/java/com/wasif/khata/core/data/di/RepositoryModule.kt`, alongside the
existing `@Binds` methods in the `abstract class RepositoryModule` body (not in its
`companion object`):

```kotlin
    @Binds
    @Singleton
    abstract fun bindDriveUploader(impl: DriveClient): DriveUploader

    @Binds
    @Singleton
    abstract fun bindDriveBackups(impl: DriveClient): DriveBackups
```

with these imports added at the top of that file:

```kotlin
import com.wasif.khata.core.drive.DriveBackups
import com.wasif.khata.core.drive.DriveClient
import com.wasif.khata.core.drive.DriveUploader
```

- [ ] **Step 3: Verify it compiles**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/drive/DriveClient.kt
git add $(grep -rln "driveUploader" app/src/main/java/com/wasif/khata/ | head -1)
git commit -m "feat(drive): four calls, and a folder that comes back if you delete it

Upload, list, download, delete, over HttpURLConnection -- the pattern
GeminiClient set, and no reason for a second one.

Pruning runs only after the new copy is up, so a failed upload cannot trade
seven backups for six and an error. A cached folder id is checked before use
and recreated on a miss, so moving the folder in Drive does not silently end
the uploads.

A grant that needs consent answers SKIPPED, not FAILED: retrying cannot fix a
grant, only the user can.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 6: The nightly worker uploads

**Files:**
- Modify: `app/src/main/java/com/wasif/khata/core/backup/BackupWorker.kt`
- Test: `app/src/test/java/com/wasif/khata/core/backup/BackupWorkerTest.kt`

**Interfaces:**
- Consumes: `DriveUploader`, `UploadOutcome` (Task 5).
- Produces: nothing new.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/wasif/khata/core/backup/BackupWorkerTest.kt`:

`BackupRepository` is final and its constructor needs a real database, so this uses
the real one against a file-backed Room database — the same setup
`BackupRepositoryTest` uses. That makes these integration tests rather than pure unit
tests, which is the right trade here: the thing worth proving is that a real backup
is what gets handed to the uploader.

```kotlin
package com.wasif.khata.core.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.wasif.khata.core.data.KhataDatabase
import com.wasif.khata.core.drive.DriveUploader
import com.wasif.khata.core.drive.UploadOutcome
import com.wasif.khata.core.prefs.HomeView
import com.wasif.khata.core.prefs.KhataPreferences
import com.wasif.khata.core.prefs.PreferencesRepository
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.ui.theme.ThemeSpec
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Base64 of "key" and "salt"; the worker only decodes them, it never derives. */
private const val KEY = "a2V5"
private const val SALT = "c2FsdA=="

@RunWith(RobolectricTestRunner::class)
class BackupWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = object : KhataClock { override fun now(): Long = 1_000L }

    private var uploads = 0
    private var outcome = UploadOutcome.UPLOADED
    private val uploader = DriveUploader { _ -> uploads++; outcome }

    private lateinit var db: KhataDatabase
    private lateinit var repository: BackupRepository

    @Before
    fun setUp() {
        deleteAll()
        // File-backed, not in-memory: backUp copies the database file, and an
        // in-memory one has none to copy.
        db = Room.databaseBuilder(context, KhataDatabase::class.java, "khata.db")
            .allowMainThreadQueries()
            .build()
        repository = BackupRepository(context, db, clock)
    }

    @After
    fun tearDown() {
        db.close()
        deleteAll()
    }

    private fun deleteAll() {
        context.getDatabasePath("khata.db").let { f ->
            f.delete()
            File(f.path + "-wal").delete()
            File(f.path + "-shm").delete()
        }
        File(context.filesDir, "backups").deleteRecursively()
    }

    private class FakePreferences(
        private val key: String?,
        private val salt: String?,
    ) : PreferencesRepository {
        override val preferences: Flow<KhataPreferences> =
            flowOf(KhataPreferences.Default.copy(backupKey = key, backupSalt = salt))

        override suspend fun setTheme(spec: ThemeSpec) = Unit
        override suspend fun resetTheme() = Unit
        override suspend fun setHomeView(view: HomeView) = Unit
        override suspend fun setMonthlyBudget(minor: Long?) = Unit
        override suspend fun setSmsPermissionRequested() = Unit
        override suspend fun setBackfilled() = Unit
        override suspend fun setGeminiKey(key: String?) = Unit
        override suspend fun setBackupPassphrase(passphrase: String?) = Unit
        override suspend fun setDriveAccount(email: String?) = Unit
        override suspend fun setDriveFolderId(id: String?) = Unit
        override suspend fun setDriveUploaded(at: Long) = Unit
        override suspend fun setDriveNeedsReconnect() = Unit
    }

    private fun run(key: String? = KEY, salt: String? = SALT): ListenableWorker.Result = runTest {
        TestListenableWorkerBuilder<BackupWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ) = BackupWorker(
                    appContext,
                    workerParameters,
                    repository,
                    FakePreferences(key, salt),
                    uploader,
                )
            })
            .build()
            .doWork()
    }

    @Test
    fun `no passphrase means no backup and no upload`() {
        assertEquals(ListenableWorker.Result.success(), run(key = null))

        assertEquals(0, uploads)
        assertEquals(false, File(context.filesDir, "backups").exists())
    }

    @Test
    fun `the backup that was just written is the one uploaded`() {
        assertEquals(ListenableWorker.Result.success(), run())

        assertEquals(1, uploads)
        assertEquals(1, repository.backupDir().listFiles()!!.size)
    }

    @Test
    fun `Drive not being connected is not a failure`() {
        // Otherwise WorkManager would retry with backoff forever on a phone that has
        // deliberately never connected Drive.
        outcome = UploadOutcome.SKIPPED

        assertEquals(ListenableWorker.Result.success(), run())
    }

    @Test
    fun `a failed upload retries, and the local copy survives it`() {
        outcome = UploadOutcome.FAILED

        assertEquals(ListenableWorker.Result.retry(), run())

        // The whole point of writing locally first: Drive being down must not cost
        // tonight's backup.
        assertEquals(1, repository.backupDir().listFiles()!!.size)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

```bash
./gradlew :app:testDebugUnitTest --tests "*BackupWorkerTest*"
```

Expected: FAIL — `BackupWorker` takes four constructor parameters, not five.

- [ ] **Step 3: Add the upload to the worker**

Replace `BackupWorker`'s constructor and `doWork` in `core/backup/BackupWorker.kt`:

```kotlin
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: BackupRepository,
    private val preferences: PreferencesRepository,
    private val uploader: DriveUploader,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // No passphrase is the off switch, and it is not an error.
        val prefs = preferences.preferences.first()
        val key = prefs.backupKey ?: return Result.success()
        val salt = prefs.backupSalt ?: return Result.success()

        val file = runCatching {
            repository.backUp(
                Base64.decode(key, Base64.NO_WRAP),
                Base64.decode(salt, Base64.NO_WRAP),
            )
            // Retry: tonight's copy is worth having, and a failed write leaves the
            // previous seven untouched.
        }.getOrElse { return Result.retry() } ?: return Result.success()

        // The local copy is already on disk and stays there whatever happens next.
        // A retry re-runs a backup that already succeeded, which is cheap; the
        // alternative is a night with no offsite copy.
        return when (uploader.upload(file)) {
            UploadOutcome.UPLOADED, UploadOutcome.SKIPPED -> Result.success()
            UploadOutcome.FAILED -> Result.retry()
        }
    }
}
```

Add the imports:

```kotlin
import com.wasif.khata.core.drive.DriveUploader
import com.wasif.khata.core.drive.UploadOutcome
```

- [ ] **Step 4: Add the network constraint to the nightly job**

In `BackupScheduler.scheduleNightly()`, on the `PeriodicWorkRequestBuilder`:

```kotlin
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
```

with `import androidx.work.Constraints` and `import androidx.work.NetworkType`.

Because `scheduleNightly` uses `ExistingPeriodicWorkPolicy.KEEP`, an install that already has the old constraint-free job keeps it. That is correct: the old job still backs up locally and uploads whenever there is a connection. It is worth knowing rather than being surprised by.

- [ ] **Step 5: Run the tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests "*BackupWorkerTest*"
```

Expected: PASS, 5 tests.

- [ ] **Step 6: Run the whole suite**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL. `SettingsViewModelTest` and any other test constructing a `PreferencesRepository` fake must have gained Task 3's four methods.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/wasif/khata/core/backup/BackupWorker.kt app/src/test/java/com/wasif/khata/core/backup/BackupWorkerTest.kt
git commit -m "feat(drive): the nightly backup goes offsite

Upload after the local write, never before it, so a Drive outage cannot cost
the copy on the phone.

Not connected answers success, not retry: a phone that deliberately never
connected Drive would otherwise retry with backoff forever. Only a genuine
failure -- no network, a bad gateway, a quota -- retries.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 7: Settings connects, says so, and restores from Drive

**Files:**
- Modify: `app/src/main/java/com/wasif/khata/feature/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/wasif/khata/feature/settings/SettingsScreen.kt:563` (`BackupSection`)

**Interfaces:**
- Consumes: `DriveAuth` (Task 4), `DriveBackups`, `DriveFile` (Tasks 2 and 5), the four preferences (Task 3).
- Produces: nothing later tasks rely on.

- [ ] **Step 1: Add the view model methods**

In `SettingsViewModel`, add to the constructor:

```kotlin
    private val driveAuth: com.wasif.khata.core.drive.DriveAuth,
    private val driveBackups: com.wasif.khata.core.drive.DriveBackups,
```

and the methods, beside `onBackUpNow`:

```kotlin
    /**
     * [launch] receives the consent sender when one is needed. Null means access was
     * already granted and the account is stored, so there is nothing to show.
     */
    fun onConnectDrive(activity: android.app.Activity, launch: (android.content.IntentSender) -> Unit) =
        viewModelScope.launch { driveAuth.beginConnect(activity)?.let(launch) }

    fun onConnectResult(data: android.content.Intent?) =
        viewModelScope.launch { driveAuth.completeConnect(data) }

    fun onDisconnectDrive() = viewModelScope.launch { repository.setDriveAccount(null) }

    suspend fun driveBackupList(): List<com.wasif.khata.core.drive.DriveFile> = driveBackups.list()

    suspend fun downloadFromDrive(id: String): ByteArray? = driveBackups.download(id)
```

- [ ] **Step 2: Add the Drive row to `BackupSection`**

In `SettingsScreen.kt`, inside `BackupSection`, after the "Share the latest backup" `ActionRow`:

```kotlin
    val connect = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onConnectResult(result.data) }

    ActionRow(
        title = if (prefs.driveAccount == null) "Connect Google Drive" else "Disconnect Google Drive",
        // Not decoration. A backup system that quietly stops is worse than one that
        // never existed, because it is trusted -- this line is the only thing that
        // says the offsite copy is real.
        subtitle = when {
            prefs.driveAccount == null -> "Not connected. Backups stay on this phone."
            prefs.driveNeedsReconnect ->
                "Uploads have stopped. Tap to reconnect, then tap again to connect."
            prefs.driveLastUploadAt != null ->
                "${prefs.driveAccount} · last uploaded ${stamp(prefs.driveLastUploadAt)}"
            else -> "${prefs.driveAccount} · nothing uploaded yet"
        },
        onClick = {
            if (prefs.driveAccount != null && !prefs.driveNeedsReconnect) {
                viewModel.onDisconnectDrive()
            } else {
                (context as? Activity)?.let { activity ->
                    viewModel.onConnectDrive(activity) { sender ->
                        connect.launch(IntentSenderRequest.Builder(sender).build())
                    }
                }
            }
        },
    )
```

Add the imports `androidx.activity.result.IntentSenderRequest` and, beside `backupLabel`, the timestamp helper:

```kotlin
/** "3 Sep, 02:00" — the same shape as backupLabel, without the year. */
private fun stamp(millis: Long): String = java.time.Instant.ofEpochMilli(millis)
    .atZone(com.wasif.khata.core.time.DHAKA)
    .format(java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm", java.util.Locale.ENGLISH))
```

- [ ] **Step 3: Put Drive backups in the restore chooser**

In `BackupSection`, add above the `if (choosing)` block:

```kotlin
    val scope = rememberCoroutineScope()
    var driveFiles by remember { mutableStateOf<List<DriveFile>?>(null) }

    LaunchedEffect(choosing) {
        driveFiles = if (choosing && prefs.driveAccount != null) viewModel.driveBackupList() else null
    }
```

Then replace the chooser's `text = { Column { ... } }` body with:

```kotlin
                Column {
                    SectionLabel("On this phone")
                    if (local.isEmpty()) {
                        Text(
                            text = "This phone has no backups yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        local.forEach { file ->
                            Text(
                                text = backupLabel(file),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        pending = file.readBytes()
                                        choosing = false
                                    }
                                    .padding(vertical = spacing.sm),
                            )
                        }
                    }

                    if (prefs.driveAccount != null) {
                        SectionLabel("In Drive")
                        when {
                            driveFiles == null -> Text(
                                text = "Looking…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            driveFiles!!.isEmpty() -> Text(
                                text = "Nothing in Drive, or Drive is unreachable.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            else -> driveFiles!!.forEach { file ->
                                Text(
                                    text = driveLabel(file.name),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            choosing = false
                                            scope.launch {
                                                pending = viewModel.downloadFromDrive(file.id)
                                            }
                                        }
                                        .padding(vertical = spacing.sm),
                                )
                            }
                        }
                    }
                }
```

And beside `backupLabel`, its Drive twin — the local one takes a `File`, this one a name:

```kotlin
/** "3 Sep 2026, 19:56" from the millis in a Drive file's name. */
private fun driveLabel(name: String): String {
    val millis = name.removePrefix("khata-").removeSuffix(".kbk").toLongOrNull() ?: return name
    return java.time.Instant.ofEpochMilli(millis)
        .atZone(com.wasif.khata.core.time.DHAKA)
        .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", java.util.Locale.ENGLISH))
}
```

Add the imports `androidx.compose.runtime.LaunchedEffect`, `androidx.compose.runtime.rememberCoroutineScope`, `kotlinx.coroutines.launch` and `com.wasif.khata.core.drive.DriveFile`.

- [ ] **Step 4: Build and run the whole suite**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL. `SettingsViewModelTest` constructs the view model directly and will need the two new constructor arguments — pass a `DriveBackups` returning `emptyList()` and `null`, and a `DriveAuth` built with the test's fake preferences.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/wasif/khata/feature/settings/
git commit -m "feat(drive): connect it, see that it worked, restore from it

The status line is the feature, not the decoration: a backup system that
quietly stops is worse than one that never existed, because it is trusted.
Three states -- not connected, last uploaded, reconnect -- from two stored
values.

Drive backups join the restore chooser under their own heading and hand their
bytes to the same onRestore as a local file, so every refusal, the kept
khata.db.replaced and the process restart are the code already proven on
hardware.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Hardware walkthrough

Unit tests cannot reach Play Services, the consent screen, or Drive. This is what proves the feature, and it is where the last two bugs in this area were actually found.

1. **Fingerprint.** `keytool -printcert -jarfile app-debug.apk` prints SHA-1 `6C:8E:…:7D:54`. If it does not, nothing below can work.
2. **Not connected is quiet.** With Drive unconnected, "Back up now" still writes a local file and reports "Backed up". Settings reads "Not connected. Backups stay on this phone."
3. **Connect.** Tap "Connect Google Drive" → the Google consent sheet appears asking for Drive access → accept. The row now shows the account and "nothing uploaded yet".
4. **Upload.** "Back up now". The row becomes "last uploaded <time>". On drive.google.com a **Khata** folder holds `khata-<millis>.kbk`, and its size matches `run-as com.wasif.khata ls -l files/backups`.
5. **The bytes are the bytes.** Download that file from drive.google.com and compare its SHA-256 against the phone's copy. They must be identical — this is the check that catches a mangled multipart body, and nothing in the UI would show it.
6. **Restore from Drive.** Delete a transaction (`run-as com.wasif.khata sqlite3 databases/khata.db "DELETE FROM transactions WHERE id=<max>;"`), then "Restore a backup" → the Drive heading → that file → passphrase → the count comes back, `khata.db.replaced` exists, the process dies, and the relaunch shows the restored row.
7. **Prune.** Back up eight times. Drive holds seven, and the oldest is gone.
8. **Disconnect.** Tap "Disconnect Google Drive". The row returns to "Not connected", and `files/datastore/khata_settings.preferences_pb` no longer contains `drive_folder_id` or `drive_last_upload_at`.

## Done when

- `./gradlew :app:assembleDebug :app:testDebugUnitTest` passes.
- The walkthrough passes, especially steps 5 and 6.
- `git status --porcelain --ignored | grep keystore.properties` reports it ignored, and `git log -p` contains no signing password.
- A phone with Drive unconnected backs up exactly as before, with no error anywhere.

## Deferred

Media, partial restore, more than one account, sharing the folder, resumable upload, and any sync of Drive back down that the user did not ask for. All recorded in spec §10.
