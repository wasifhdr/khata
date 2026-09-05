package com.wasif.khata.feature.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.data.dao.RestaurantHubStats
import com.wasif.khata.core.data.dao.VehicleHubStats
import com.wasif.khata.core.data.dao.WatchlistHubStats
import com.wasif.khata.core.data.repository.MonthLimits
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.note.NotesStats
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.time.dhakaMonthStart
import com.wasif.khata.core.time.dhakaNextMonthStart
import com.wasif.khata.core.time.toDhakaLocalDate
import com.wasif.khata.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * How many rows are waiting to be settled as transfers, as a function type -- the
 * shape MonthLimits and RecentCategoryIds already use, so the hub's test needs one
 * lambda rather than a whole TransactionDao.
 */
fun interface PendingReviewCount {
    operator fun invoke(): Flow<Int>
}

/** What the four module tiles show, gathered in one place. */
data class ModuleSnapshot(
    val restaurants: RestaurantHubStats,
    val watchlist: WatchlistHubStats,
    val vehicle: VehicleHubStats,
    val notes: NotesStats,
)

/**
 * The four modules' figures as a function type, for the reason PendingReviewCount is one: this
 * view model's test wants a lambda, not four DAOs and a Room database behind them.
 */
fun interface ModuleStats {
    operator fun invoke(): Flow<ModuleSnapshot>
}

private val DayFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

/** Absent rather than invented: no rows summed is not ৳0.00, and no last visit is not a date. */
private const val NOTHING = "—"

private fun money(minor: Long?): String = minor?.let { Money(it).format() } ?: NOTHING

private fun day(millis: Long?): String =
    millis?.toDhakaLocalDate()?.format(DayFormat) ?: NOTHING

@HiltViewModel
class ModulesViewModel @Inject constructor(
    transactions: TransactionRepository,
    limits: MonthLimits,
    pendingReviews: PendingReviewCount,
    moduleStats: ModuleStats,
    clock: KhataClock,
) : ViewModel() {

    private val now = clock.now()

    private val wallet = combine(
        transactions.observeSpentBetween(now.dhakaMonthStart(), now.dhakaNextMonthStart()),
        transactions.observeMostRecent(),
        limits(now.dhakaMonthStart()),
        pendingReviews(),
    ) { spend, last, monthLimits, pending ->
        // The sum of what every category is allowed, which is the only total there
        // is now -- the single global budget it replaced could disagree with the
        // categories underneath it about the same month.
        val total = if (monthLimits.isEmpty()) null else monthLimits.values.sum()
        ModulesUiState(
            monthSpend = spend,
            hasPendingReview = pending > 0,
            budgetFraction = when {
                // Absent, not zero: no limits set means no ring, as before.
                total == null -> null
                // Any spend against a zero total is over it. Dividing would
                // produce infinity and the ring would refuse to draw.
                total <= 0L -> 1f
                // Clamped, because overspending is real and must read as a full
                // ring rather than 150% of a circle.
                else -> (spend.minor.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            },
            lastTransaction = last,
        )
    }

    val state: StateFlow<ModulesUiState> = combine(
        wallet,
        moduleStats(),
    ) { base, modules ->
        base.copy(
            restaurants = modules.restaurants.tile(),
            watchlist = modules.watchlist.tile(),
            vehicle = modules.vehicle.tile(),
            notes = modules.notes.tile(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ModulesUiState(),
    )

    // Every total here is all time. "This month" belongs to the wallet card alone, because it
    // is the only figure on this screen with a budget behind it to be measured against.
    private fun RestaurantHubStats.tile() = ModuleTileState(
        listOf(
            TileStat("Last visit", lastName ?: NOTHING),
            TileStat("Visited", visitedCount.toString()),
            TileStat("Spent", money(spentMinor)),
        ),
    )

    private fun WatchlistHubStats.tile() = ModuleTileState(
        listOf(
            TileStat("Last watched", lastName ?: NOTHING),
            TileStat("Watched", watchedCount.toString()),
            TileStat("Watchlist", queuedCount.toString()),
        ),
    )

    private fun VehicleHubStats.tile() = ModuleTileState(
        listOf(
            TileStat("Car", carName ?: NOTHING),
            TileStat("Last service", day(lastServicedAt)),
            TileStat("Spent", money(spentMinor)),
        ),
    )

    private fun NotesStats.tile() = ModuleTileState(
        listOf(
            TileStat("Last edited", lastTitle ?: NOTHING),
            TileStat("Total", total.toString()),
            TileStat("To do", toDo.toString()),
        ),
    )
}
