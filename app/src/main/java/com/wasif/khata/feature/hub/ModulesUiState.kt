package com.wasif.khata.feature.hub

import com.wasif.khata.core.model.Money
import com.wasif.khata.domain.model.Transaction

/**
 * One line on a module tile: a small label and its value.
 *
 * [value] is already formatted, because what "empty" means differs by line and the tile is the
 * wrong place to decide it. A count of zero is a true measurement and reads as "0"; a total over
 * no rows, or a "last …" with nothing behind it, reads as "—" rather than inventing ৳0.00 or a
 * date that never happened.
 */
data class TileStat(val label: String, val value: String)

data class ModuleTileState(val stats: List<TileStat> = emptyList())

data class ModulesUiState(
    val monthSpend: Money = Money.ZERO,
    /** Null when no budget is set — the ring is not drawn rather than drawn at zero. */
    val budgetFraction: Float? = null,
    val lastTransaction: Transaction? = null,
    /** Something is waiting to be settled as a transfer or not. */
    val hasPendingReview: Boolean = false,

    val restaurants: ModuleTileState = ModuleTileState(),
    val watchlist: ModuleTileState = ModuleTileState(),
    val vehicle: ModuleTileState = ModuleTileState(),
    val notes: ModuleTileState = ModuleTileState(),
)
