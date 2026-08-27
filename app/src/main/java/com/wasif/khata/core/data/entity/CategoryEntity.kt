package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "categories", indices = [Index(value = ["uuid"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val name: String,
    val icon: String,
    val colorToken: String,
    val parentId: Long?,
    val isSystem: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
