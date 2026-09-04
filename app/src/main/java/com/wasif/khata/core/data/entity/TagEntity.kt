package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tags", indices = [Index(value = ["uuid"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    /**
     * Matched case-insensitively by TagDao.findByName. Without that, autocomplete
     * starts offering three spellings of one person and "everywhere I went with
     * Rafi" returns a third of the answer.
     */
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
