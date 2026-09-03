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
