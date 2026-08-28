package com.wasif.khata.feature.hub

import com.wasif.khata.core.model.Money
import com.wasif.khata.domain.model.Transaction

data class ModulesUiState(
    val monthSpend: Money = Money.ZERO,
    /** Null when no budget is set — the ring is not drawn rather than drawn at zero. */
    val budgetFraction: Float? = null,
    val lastTransaction: Transaction? = null,
)
