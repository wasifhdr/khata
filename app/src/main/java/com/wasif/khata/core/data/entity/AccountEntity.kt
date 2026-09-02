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
    /**
     * Money that moved without a message. Every bank message states the balance
     * after it, so when a statement disagrees with what the recorded transactions
     * predicted, the difference is a message that never arrived. Accumulated here
     * rather than silently absorbed, because it is the one honest measure of how
     * complete the SMS history is.
     */
    val unexplainedMinor: Long = 0,
    val includeInNetWorth: Boolean,
    val smsIdentifiers: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
