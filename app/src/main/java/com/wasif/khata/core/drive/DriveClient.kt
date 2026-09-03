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
import org.json.JSONArray
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
        val token = when (val result = auth.token()) {
            is TokenResult.Token -> result.value
            TokenResult.NotConnected -> return@withContext UploadOutcome.SKIPPED
            TokenResult.NeedsReconnect -> {
                preferences.setDriveNeedsReconnect()
                // Not FAILED: retrying cannot mend a grant, only the user can.
                return@withContext UploadOutcome.SKIPPED
            }
        }

        val folder = folderId(token) ?: return@withContext UploadOutcome.FAILED

        val metadata = JSONObject()
            .put("name", file.name)
            .put("parents", JSONArray().put(folder))
            .toString()
        val boundary = "khata-${clock.now()}"

        val created = post(
            url = UPLOAD,
            token = token,
            contentType = "multipart/related; boundary=$boundary",
            body = multipartBody(metadata, file.readBytes(), boundary),
        ) ?: return@withContext UploadOutcome.FAILED

        if (parseFileId(created) == null) return@withContext UploadOutcome.FAILED

        preferences.setDriveUploaded(clock.now())

        // Only once the new copy is up. Pruning first would trade seven backups for
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
     * The cached folder, or a new one. A miss means it was moved or deleted in Drive,
     * and a stale id must not be a dead feature.
     */
    private suspend fun folderId(token: String): String? {
        val cached = preferences.preferences.first().driveFolderId
        if (cached != null && get("$FILES/$cached?fields=id", token) != null) return cached

        val body = JSONObject()
            .put("name", FOLDER_NAME)
            .put("mimeType", FOLDER_MIME)
            .toString()
        val created = post(FILES, token, "application/json; charset=UTF-8", body.toByteArray())
            ?: return null

        return parseFileId(created)?.also { preferences.setDriveFolderId(it) }
    }

    private fun listFiles(token: String, folder: String): List<DriveFile> {
        val query = URLEncoder.encode("'$folder' in parents and trashed = false", "UTF-8")
        val fields = URLEncoder.encode("files(id,name)", "UTF-8")
        val json = get("$FILES?q=$query&fields=$fields&pageSize=100", token) ?: return emptyList()
        return parseFileList(json.toString(Charsets.UTF_8))
    }

    private fun delete(token: String, id: String) {
        val connection = open("$FILES/$id", token) ?: return
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
            val code = connection.responseCode
            if (code !in 200..299) {
                // The one line worth keeping. Drive refuses for reasons the UI cannot
                // usefully distinguish -- an API not enabled on the project, a quota,
                // a revoked grant -- and they all surface as "the upload failed".
                // Without this they are indistinguishable from a flaky connection too.
                android.util.Log.w("KhataDrive", "POST " + url.take(60) + " -> " + code)
                null
            } else {
                connection.inputStream.bufferedReader().readText()
            }
        } catch (e: IOException) {
            // Null rather than a throw, everywhere: the worker turns this into a
            // retry with backoff, which is the right answer to a flaky connection,
            // and a crash is not.
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
