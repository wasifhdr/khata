package com.wasif.khata.feature.settings

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.prefs.HomeView
import com.wasif.khata.core.prefs.KhataPreferences
import com.wasif.khata.core.data.dao.AccountDao
import com.wasif.khata.core.data.dao.RawMessageDao
import com.wasif.khata.core.model.RawMessageStatus
import com.wasif.khata.core.permission.SmsPermissionRepository
import com.wasif.khata.core.permission.SmsPermissionState
import com.wasif.khata.core.model.AccountType
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.prefs.PreferencesRepository
import com.wasif.khata.core.sms.IngestProgress
import com.wasif.khata.core.sms.BackfillUseCase
import com.wasif.khata.core.sms.IngestSummary
import com.wasif.khata.core.sms.ReparseUseCase
import com.wasif.khata.core.ui.theme.FieldIntensity
import com.wasif.khata.core.ui.theme.FieldPalette
import com.wasif.khata.core.ui.theme.ThemeSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Everything the MESSAGES section needs, in one value. Grouped so adding a row
 * there does not add a parameter to [SettingsContent] each time.
 */
data class IngestionState(
    val permission: SmsPermissionState = SmsPermissionState.NOT_REQUESTED,
    val unmatchedCount: Int = 0,
    val backfill: IngestProgress? = null,
    val lastRun: String? = null,
    val cashBalance: Money = Money.ZERO,
) {
    val isWorking: Boolean get() = backfill != null && !backfill.isComplete
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: PreferencesRepository,
    private val smsPermission: SmsPermissionRepository,
    private val backfill: BackfillUseCase,
    private val reparse: ReparseUseCase,
    rawMessageDao: RawMessageDao,
    private val accountDao: AccountDao,
    private val transactions: com.wasif.khata.domain.repository.TransactionRepository,
    private val clock: com.wasif.khata.core.time.KhataClock,
) : ViewModel() {

    private val _backfill = MutableStateFlow<IngestProgress?>(null)
    private val _lastRun = MutableStateFlow<String?>(null)

    val ingestion: StateFlow<IngestionState> = combine(
        smsPermission.observe(),
        // Drives the row's own count, so it says how much is waiting before you open it.
        rawMessageDao.observeByStatus(RawMessageStatus.UNMATCHED).map { it.size },
        _backfill,
        _lastRun,
        accountDao.observeAll().map { accounts ->
            Money(accounts.firstOrNull { it.type == AccountType.CASH }?.currentBalanceMinor ?: 0L)
        },
    ) { permission, unmatched, progress, lastRun, cash ->
        IngestionState(permission, unmatched, progress, lastRun, cash)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = IngestionState(),
    )

    /**
     * Records that the dialog was shown. Without this the repository cannot tell
     * "never asked" from "asked and refused", and every refusal would look like
     * a first run.
     */
    fun onPermissionRequested() = viewModelScope.launch { smsPermission.onRequested() }

    /**
     * Reading a multi-year inbox is thousands of messages, so progress is
     * collected rather than awaited -- a silent block reads as a hang.
     */
    fun onBackfill() = runPass(backfill.run(), "Could not read messages. Please try again.")

    /**
     * Six years of ATM withdrawals with no cash spending entered against them leave
     * the cash account holding money that is long gone. This writes that off as of
     * today so the figure starts meaning something.
     */
    fun onResetCash() {
        viewModelScope.launch {
            val cash = accountDao.getAll().firstOrNull { it.type == AccountType.CASH } ?: return@launch
            transactions.resetToZero(cash.id, clock.now()).fold(
                onSuccess = { _lastRun.value = "Cash starts again from zero" },
                onFailure = { _lastRun.value = "Could not reset cash. Please try again." },
            )
        }
    }

    fun onReparse() = runPass(reparse.run(), "Could not re-read messages. Please try again.")

    /** One whole-inbox pass, reported as progress then as a summary. */
    private fun runPass(pass: Flow<IngestProgress>, failure: String) {
        if (_backfill.value?.isComplete == false) return
        _backfill.value = null
        _lastRun.value = null
        viewModelScope.launch {
            runCatching {
                pass.collect { _backfill.value = it }
            }.fold(
                onSuccess = { _lastRun.value = _backfill.value?.summary?.inWords() },
                onFailure = {
                    _backfill.value = null
                    _lastRun.value = failure
                },
            )
        }
    }

    val state: StateFlow<KhataPreferences> = repository.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = KhataPreferences.Default,
    )

    // Each setter edits one axis and carries the other three forward. The tuner
    // is four independent controls over one value.
    fun onFieldSelected(field: FieldPalette) = save { it.copy(field = field) }

    fun onGroundSelected(ground: Color) = save { it.copy(ground = ground) }

    fun onAccentSelected(accent: Color) = save { it.copy(accent = accent) }

    fun onIntensitySelected(intensity: FieldIntensity) = save { it.copy(intensity = intensity) }

    fun onResetTheme() = viewModelScope.launch { repository.resetTheme() }

    /**
     * Wired to the HOME VIEW control in Settings since Task 6. Writing here
     * only changes what `KhataNavHost` resolves as its start destination on the
     * *next* process launch (I6) — it freezes that value for the lifetime of
     * the current one, because rebuilding the graph under a live back stack
     * would pop it and eject the user out of Settings mid-interaction. The
     * supporting text under HOME VIEW says as much.
     */
    fun onHomeViewSelected(view: HomeView) = viewModelScope.launch { repository.setHomeView(view) }

    fun onMonthlyBudgetChanged(minor: Long?) = viewModelScope.launch {
        repository.setMonthlyBudget(minor)
    }

    // Reads the store rather than state.value. `state` is WhileSubscribed, so
    // with no collector it never leaves its initial value -- and a save made
    // before the first collection would then overwrite the stored theme with
    // the default plus one edited axis.
    private fun save(edit: (ThemeSpec) -> ThemeSpec) = viewModelScope.launch {
        repository.setTheme(edit(repository.preferences.first().themeSpec))
    }
}

/** Numbers a person can act on, rather than a struct dump. */
fun IngestSummary.inWords(): String = buildList {
    add("$recorded recorded")
    if (updated > 0) add("$updated updated")
    if (unmatched > 0) add("$unmatched unread")
    if (ignored > 0) add("$ignored ignored")
}.joinToString(" · ")
