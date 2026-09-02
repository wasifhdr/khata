package com.wasif.khata.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wasif.khata.core.model.RuleKind
import com.wasif.khata.core.model.TransactionDirection

@Entity(
    tableName = "parsing_rules",
    indices = [Index(value = ["uuid"], unique = true), Index(value = ["priority"])],
)
data class ParsingRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val name: String,
    val senderPattern: String,
    val bodyPattern: String,
    // Null for IGNORE rules, which extract nothing and so have no direction.
    val direction: TransactionDirection?,
    val kind: RuleKind,
    val priority: Int,
    val origin: String,
    val isEnabled: Boolean,
    val sampleMessage: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
