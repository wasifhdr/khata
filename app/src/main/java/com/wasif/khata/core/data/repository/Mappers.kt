package com.wasif.khata.core.data.repository

import com.wasif.khata.core.data.entity.AccountEntity
import com.wasif.khata.core.data.entity.CategoryEntity
import com.wasif.khata.core.data.entity.TransactionEntity
import com.wasif.khata.core.model.Money
import com.wasif.khata.domain.model.Account
import com.wasif.khata.domain.model.Category
import com.wasif.khata.domain.model.Transaction

fun TransactionEntity.toDomain() = Transaction(
    id = id,
    uuid = uuid,
    accountId = accountId,
    amount = Money(amountMinor),
    direction = direction,
    occurredAt = occurredAt,
    merchantRaw = merchantRaw,
    merchantId = merchantId,
    categoryId = categoryId,
    note = note,
    counterparty = counterparty,
    owed = Money(owedMinor),
    kind = kind,
    source = source,
    confidence = confidence,
    transferGroupId = transferGroupId,
    rawMessageId = rawMessageId,
    updatedAt = updatedAt,
)

fun AccountEntity.toDomain() = Account(
    id = id,
    uuid = uuid,
    name = name,
    type = type,
    currentBalance = Money(currentBalanceMinor),
    reportedBalance = reportedBalanceMinor?.let { Money(it) },
    includeInNetWorth = includeInNetWorth,
)

fun CategoryEntity.toDomain() = Category(
    id = id,
    uuid = uuid,
    name = name,
    colorToken = colorToken,
    parentId = parentId,
    isSystem = isSystem,
)
