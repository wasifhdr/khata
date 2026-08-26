package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "merchants", indices = [Index(value = ["uuid"], unique = true)])
data class MerchantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val canonicalName: String,
    val categoryId: Long?,
    val placeId: Long?,
    val isUserConfirmed: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "merchant_aliases",
    indices = [Index(value = ["rawText"], unique = true), Index(value = ["merchantId"])],
)
data class MerchantAliasEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val merchantId: Long,
    val rawText: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
