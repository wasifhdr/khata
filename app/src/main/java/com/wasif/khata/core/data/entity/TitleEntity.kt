package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TitleKind { FILM, SERIES }

/**
 * A film or a series. There is no season or episode model: without progress tracking
 * a series is a title watched on some dates, which is all this module records.
 */
@Entity(
    tableName = "titles",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["tmdbId"], unique = true),
        Index(value = ["name"]),
    ],
)
data class TitleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val name: String,
    val year: Int? = null,
    val kind: TitleKind,
    /**
     * Unique when present, so a hand-typed title later matched to TMDB cannot become a
     * second row, and the same TMDB entry cannot be added twice. SQLite treats NULLs as
     * distinct in a unique index, so any number of hand-typed titles coexist.
     */
    val tmdbId: Int? = null,
    /** TMDB's own 0-10 scale, stored as given. Never blended with the user's stars. */
    val tmdbRating: Double? = null,
    /** When that number was taken. A dated number from elsewhere never poses as current. */
    val tmdbRatingAt: Long? = null,
    /** A pointer into media, never a second copy of the file. */
    val posterMediaId: Long? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
