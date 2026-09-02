package com.wasif.khata.feature.unmatched

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.data.dao.ParsingRuleDao
import com.wasif.khata.core.data.dao.RawMessageDao
import com.wasif.khata.core.data.entity.ParsingRuleEntity
import com.wasif.khata.core.model.RawMessageStatus
import com.wasif.khata.core.model.RuleKind
import com.wasif.khata.core.sms.IngestProgress
import com.wasif.khata.core.sms.ReparseUseCase
import com.wasif.khata.core.sms.deriveIgnorePattern
import com.wasif.khata.core.time.KhataClock
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UnmatchedMessage(
    val id: Long,
    val sender: String,
    val body: String,
    val receivedAt: Long,
) {
    /**
     * Instrument Sans has no Bengali glyphs and would fall through to whatever the
     * device ships — the per-device variance the bundled cut exists to remove. A raw
     * SMS body is unpredictable, so the style is chosen per message.
     */
    val isBengali: Boolean get() = body.any { it.code in 0x0980..0x09FF }
}

data class UnmatchedUiState(
    val messages: List<UnmatchedMessage> = emptyList(),
    val isLoaded: Boolean = false,
    /** What the last action did, in words. Cleared when another one starts. */
    val notice: String? = null,
    val isWorking: Boolean = false,
    val progress: IngestProgress? = null,
) {
    val isEmpty: Boolean get() = messages.isEmpty()

    /** Progress while it runs, the outcome once it is done. One line, either way. */
    val line: String?
        get() = progress?.let { "Re-reading ${it.processed} of ${it.total}…" } ?: notice
}

@HiltViewModel
class UnmatchedViewModel @Inject constructor(
    rawMessageDao: RawMessageDao,
    private val ruleDao: ParsingRuleDao,
    private val reparse: ReparseUseCase,
    private val clock: KhataClock,
) : ViewModel() {

    private val _notice = MutableStateFlow<String?>(null)
    private val _working = MutableStateFlow(false)

    private val _progress = MutableStateFlow<IngestProgress?>(null)

    val state: StateFlow<UnmatchedUiState> = combine(
        rawMessageDao.observeByStatus(RawMessageStatus.UNMATCHED),
        _notice,
        _working,
        _progress,
    ) { rows, notice, working, progress ->
        UnmatchedUiState(
            messages = rows.map {
                UnmatchedMessage(
                    id = it.id,
                    sender = it.sender,
                    body = it.body,
                    receivedAt = it.receivedAt,
                )
            },
            isLoaded = true,
            notice = notice,
            isWorking = working,
            progress = progress,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UnmatchedUiState())

    /**
     * Writes an IGNORE rule for this message's shape. Most of what a bank sends is
     * not a transaction -- verification codes, marketing, service notices -- and
     * none of it can be taught as one, so without this the review list can never
     * be finished.
     *
     * Appended below every existing rule, like a hand-written transaction rule.
     * The engine takes the first rule that matches, so an IGNORE written here can
     * never outrank a rule that actually reads the message: at worst it silences
     * something nothing else could read anyway.
     */
    fun onNotATransaction(id: Long) {
        if (_working.value) return
        val message = state.value.messages.firstOrNull { it.id == id } ?: return
        val pattern = deriveIgnorePattern(message.body)
        if (pattern == null) {
            _notice.value = "That message opens with too few words to be sure about. " +
                "Write a rule for it instead."
            return
        }

        _working.value = true
        _notice.value = null
        viewModelScope.launch {
            // Counted before the reparse, while these rows are still on screen.
            val like = state.value.messages.count {
                it.sender == message.sender && Regex(pattern).containsMatchIn(it.body)
            }
            runCatching {
                val now = clock.now()
                val priority = (ruleDao.allIncludingDisabled().maxOfOrNull { it.priority } ?: 0) + 1
                ruleDao.upsertAll(
                    listOf(
                        ParsingRuleEntity(
                            uuid = "ignore-${message.body.hashCode()}-$now",
                            name = "Not a transaction",
                            senderPattern = Regex.escape(message.sender),
                            bodyPattern = pattern,
                            direction = null,
                            kind = RuleKind.IGNORE,
                            priority = priority,
                            origin = "USER",
                            isEnabled = true,
                            sampleMessage = message.body,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    ),
                )
                reparse.run().collect { _progress.value = it }
            }.fold(
                onSuccess = {
                    _notice.value = if (like == 1) {
                        "Hidden."
                    } else {
                        "Hidden, along with $like others like it."
                    }
                },
                onFailure = { _notice.value = "Could not hide that one. Please try again." },
            )
            _progress.value = null
            _working.value = false
        }
    }

    fun onNoticeShown() {
        _notice.value = null
    }
}
