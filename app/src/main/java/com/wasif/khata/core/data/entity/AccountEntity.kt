package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wasif.khata.core.model.AccountType

@Entity(tableName = "accounts", indices = [Index(value = ["uuid"], unique = true)])
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val name: String,
    val type: AccountType,
    val openingBalanceMinor: Long,
    val currentBalanceMinor: Long,
    val reportedBalanceMinor: Long?,
    val reportedBalanceAt: Long?,
    val includeInNetWorth: Boolean,
    val smsIdentifiers: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
