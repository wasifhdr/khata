package com.wasif.khata.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.wasif.khata.core.data.dao.MediaDao
import com.wasif.khata.core.data.entity.MediaEntity
import com.wasif.khata.core.data.entity.MediaLinkEntity
import com.wasif.khata.core.time.KhataClock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 2048 on the long edge, per 2026-08-26 D10: ~400 KB against ~4 MB, and indistinguishable. */
const val MAX_EDGE_PX = 2048

private const val JPEG_QUALITY = 85

fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

fun needsDownscale(mimeType: String, longEdgePx: Int): Boolean =
    mimeType != "image/jpeg" || longEdgePx > MAX_EDGE_PX

@Singleton
class MediaStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: MediaDao,
    private val clock: KhataClock,
) {
    fun dir(): File = File(context.filesDir, "media").apply { mkdirs() }

    fun fileFor(sha256: String): File = File(dir(), "$sha256.jpg")

    /**
     * pick -> decode -> scale -> encode -> hash -> write -> row -> link.
     *
     * Content-addressed and write-once: if the file is there the work is done, and if
     * a row already carries the hash only a link is written. The same photo in two
     * visits costs one file and one row.
     */
    suspend fun import(uri: Uri, entityType: String, entityId: Long): MediaEntity? =
        withContext(Dispatchers.IO) {
            val source = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull() ?: return@withContext null

            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"

            // ImageDecoder, never BitmapFactory: it applies EXIF orientation and
            // BitmapFactory does not, and the failure mode is every food photo on
            // its side. Decoded even when the bytes are kept, because the row needs
            // real pixel dimensions.
            val bitmap = runCatching {
                ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(context.contentResolver, uri),
                ) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            }.getOrNull() ?: return@withContext null

            val longEdge = maxOf(bitmap.width, bitmap.height)
            val reencode = needsDownscale(mimeType, longEdge)
            val scaled = if (reencode) scale(bitmap, longEdge) else bitmap
            val bytes = if (reencode) encode(scaled) else source

            val hash = sha256(bytes)
            val file = fileFor(hash)
            // Write-once. A file already under this name holds these exact bytes,
            // because the name is their hash.
            if (!file.exists()) {
                val temp = File(dir(), "$hash.tmp")
                temp.writeBytes(bytes)
                // Atomic, so a killed process never leaves a half file under a name
                // that claims to be complete.
                if (!temp.renameTo(file)) {
                    temp.delete()
                    return@withContext null
                }
            }

            val now = clock.now()
            val media = dao.findByHash(hash) ?: MediaEntity(
                uuid = UUID.randomUUID().toString(),
                sha256 = hash,
                mimeType = if (reencode) "image/jpeg" else mimeType,
                widthPx = scaled.width,
                heightPx = scaled.height,
                byteSize = bytes.size.toLong(),
                capturedAt = capturedAt(uri),
                originalUri = uri.toString(),
                createdAt = now,
                updatedAt = now,
            ).let { it.copy(id = dao.upsert(it)) }

            if (dao.linkCount(media.id, entityType, entityId) == 0) {
                dao.upsertLink(
                    MediaLinkEntity(
                        uuid = UUID.randomUUID().toString(),
                        mediaId = media.id,
                        entityType = entityType,
                        entityId = entityId,
                        sortOrder = dao.mediaFor(entityType, entityId).size,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
            media
        }

    suspend fun detach(mediaId: Long, entityType: String, entityId: Long) =
        dao.detachLink(mediaId, entityType, entityId, clock.now())

    private fun scale(bitmap: Bitmap, longEdge: Int): Bitmap {
        if (longEdge <= MAX_EDGE_PX) return bitmap
        val ratio = MAX_EDGE_PX.toFloat() / longEdge
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun encode(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        out.toByteArray()
    }

    /**
     * Null is a fine answer. Not androidx.exifinterface, which is a whole dependency
     * for one nullable convenience.
     */
    private fun capturedAt(uri: Uri): Long? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(android.provider.MediaStore.MediaColumns.DATE_TAKEN),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
    }.getOrNull()
}
