package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media",
    indices = [Index(value = ["uuid"], unique = true), Index(value = ["sha256"], unique = true)],
)
data class MediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    /** Of the stored bytes. Names the file, so an absolute path never has to be stored. */
    val sha256: String,
    val mimeType: String,
    val widthPx: Int,
    val heightPx: Int,
    val byteSize: Long,
    val capturedAt: Long? = null,
    /** A link back to the gallery, never a dependency. Dead links cost nothing. */
    val originalUri: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
