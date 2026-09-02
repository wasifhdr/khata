package com.wasif.khata.feature.ruleeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.data.dao.AccountDao
import com.wasif.khata.core.data.dao.ParsingRuleDao
import com.wasif.khata.core.data.dao.RawMessageDao
import com.wasif.khata.core.data.entity.ParsingRuleEntity
import com.wasif.khata.core.model.RuleKind
import com.wasif.khata.core.model.TransactionDirection
import com.wasif.khata.core.sms.DerivedRule
import com.wasif.khata.core.sms.FieldKind
import com.wasif.khata.core.sms.IngestProgress
import com.wasif.khata.core.sms.IngestSummary
import com.wasif.khata.core.sms.LabelledSpan
import com.wasif.khata.core.sms.namesSender
import com.wasif.khata.core.sms.ReparseUseCase
import com.wasif.khata.core.sms.derivePattern
import com.wasif.khata.core.time.KhataClock
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Token(val text: String, val start: Int, val endExclusive: Int)

data class RuleEditorUiState(
    val sender: String = "",
    val body: String = "",
    val tokens: List<Token> = emptyList(),
    val pending: IntRange? = null,
    val spans: List<LabelledSpan> = emptyList(),
    val direction: TransactionDirection = TransactionDirection.DEBIT,
    val kind: RuleKind = RuleKind.NORMAL,
    val name: String = "",
    val isSaving: Boolean = false,
    /**
     * False when the sender alone cannot say which account a message is about, as
     * with EBL. Such a rule matches and is then thrown away at the account lookup,
     * so it must not be saveable.
     */
    val senderNamesAccount: Boolean = true,
    val progress: IngestProgress? = null,
    val savedRuleId: Long? = null,
    val reparseSummary: String? = null,
    val error: String? = null,
) {
    /**
     * What the save button says. Re-reading thousands of stored messages takes the
     * better part of a minute, and a button stuck on "Saving..." for that long
     * reads as a hang.
     */
    val saveLabel: String
        get() = when {
            reparseSummary != null -> "Saved"
            progress != null -> "Re-reading ${progress.processed} of ${progress.total}"
            isSaving -> "Saving…"
            else -> "Save and re-read history"
        }

    private val hasAccount: Boolean get() = spans.any { it.kind == FieldKind.ACCOUNT }

    val derived: DerivedRule
        get() = when {
            spans.isEmpty() && pending == null ->
                DerivedRule("", emptyMap(), "Tap the amount in the message, then say what it is.")

            else -> {
                val rule = derivePattern(body, spans)
                // Caught here rather than after saving: without an account this rule
                // would match every message it was written for and record none of
                // them, and the only sign would be "0 recorded".
                if (rule.error == null && !senderNamesAccount && !hasAccount) {
                    rule.copy(
                        error = "Tap the account number too — Khata cannot tell which " +
                            "account $sender means without it.",
                    )
                } else {
                    rule
                }
            }
        }

    /** True while a run of words is selected but not yet labelled. */
    val awaitingLabel: Boolean get() = pending != null

    fun kindOfToken(index: Int): FieldKind? {
        val token = tokens.getOrNull(index) ?: return null
        return spans.firstOrNull { token.start >= it.start && token.endExclusive <= it.endExclusive }?.kind
    }

    val canSave: Boolean
        get() = derived.error == null && !isSaving && name.isNotBlank() && pending == null
}

@HiltViewModel(assistedFactory = RuleEditorViewModel.Factory::class)
class RuleEditorViewModel @AssistedInject constructor(
    private val rawMessageDao: RawMessageDao,
    private val ruleDao: ParsingRuleDao,
    private val accountDao: AccountDao,
    private val reparse: ReparseUseCase,
    private val clock: KhataClock,
    @Assisted private val rawMessageId: Long,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(rawMessageId: Long): RuleEditorViewModel
    }

    private val _state = MutableStateFlow(RuleEditorUiState())
    val state: StateFlow<RuleEditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val raw = rawMessageDao.findById(rawMessageId) ?: return@launch
            val named = accountDao.getAll().any { namesSender(it.smsIdentifiers, raw.sender) }
            _state.update {
                it.copy(
                    sender = raw.sender,
                    body = raw.body,
                    tokens = tokenise(raw.body),
                    senderNamesAccount = named,
                    name = "${raw.sender} rule",
                )
            }
        }
    }

    /**
     * Tapping extends a contiguous run rather than starting over, because the fields
     * worth capturing are almost always several words long. Tapping inside an already
     * labelled span clears it.
     */
    fun onTokenTapped(index: Int) = _state.update { current ->
        val token = current.tokens.getOrNull(index) ?: return@update current
        val existing = current.spans.firstOrNull {
            token.start >= it.start && token.endExclusive <= it.endExclusive
        }
        when {
            existing != null -> current.copy(spans = current.spans - existing, error = null)
            current.pending == null -> current.copy(pending = index..index, error = null)
            else -> {
                val lo = minOf(current.pending.first, index)
                val hi = maxOf(current.pending.last, index)
                current.copy(pending = lo..hi, error = null)
            }
        }
    }

    fun onFieldChosen(kind: FieldKind) = _state.update { current ->
        val pending = current.pending ?: return@update current
        val start = current.tokens[pending.first].start
        val end = current.tokens[pending.last].endExclusive
        current.copy(
            spans = current.spans + LabelledSpan(start, end, kind),
            pending = null,
        )
    }

    fun onSelectionCleared() = _state.update { it.copy(pending = null) }

    fun onDirectionChanged(direction: TransactionDirection) =
        _state.update { it.copy(direction = direction) }

    fun onKindChanged(kind: RuleKind) = _state.update { it.copy(kind = kind) }

    fun onNameChanged(name: String) = _state.update { it.copy(name = name) }

    fun onSave() {
        val current = _state.value
        if (!current.canSave) return
        _state.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            runCatching {
                val now = clock.now()
                // Keyed on the message, not the moment: teaching the same message a
                // second time is a revision of that rule, not a second rule. The
                // timestamp used to be in here, so every save stacked another copy.
                val uuid = "user-${current.body.hashCode()}"
                val existing = ruleDao.findByUuid(uuid)
                // Appended after every existing rule, so a hand-made rule can never
                // outrank an IGNORE rule. An OTP message carries a real amount and a
                // real merchant; letting one of these jump the queue would silently
                // double every OTP-carrying payment. A revision keeps the place it
                // already earned rather than being sent to the back each time.
                val priority = existing?.priority
                    ?: ((ruleDao.allIncludingDisabled().maxOfOrNull { it.priority } ?: 0) + 1)
                ruleDao.upsertAll(
                    listOf(
                        ParsingRuleEntity(
                            id = existing?.id ?: 0,
                            uuid = uuid,
                            name = current.name.trim(),
                            senderPattern = Regex.escape(current.sender),
                            bodyPattern = current.derived.pattern,
                            direction = current.direction,
                            kind = current.kind,
                            priority = priority,
                            origin = "USER",
                            isEnabled = true,
                            sampleMessage = current.body,
                            createdAt = existing?.createdAt ?: now,
                            updatedAt = now,
                        ),
                    ),
                )
                // Reparse is idempotent, so offering it freely is safe — and it is what
                // turns a new rule into retroactively corrected history.
                var last: IngestProgress? = null
                reparse.run().collect {
                    last = it
                    _state.update { state -> state.copy(progress = it) }
                }
                last?.summary ?: IngestSummary()
            }.fold(
                onSuccess = { summary ->
                    _state.update {
                        it.copy(
                            isSaving = false,
                            progress = null,
                            savedRuleId = 1,
                            reparseSummary = "${summary.recorded} recorded · ${summary.unmatched} still unread",
                        )
                    }
                },
                onFailure = {
                    _state.update {
                        it.copy(
                            isSaving = false,
                            progress = null,
                            error = "Could not save the rule. Please try again.",
                        )
                    }
                },
            )
        }
    }
}

/** Whitespace-delimited words with their offsets, so a tap maps back to the body. */
fun tokenise(body: String): List<Token> {
    val tokens = mutableListOf<Token>()
    var i = 0
    while (i < body.length) {
        if (body[i].isWhitespace()) {
            i++
            continue
        }
        val start = i
        while (i < body.length && !body[i].isWhitespace()) i++
        tokens += Token(body.substring(start, i), start, i)
    }
    return tokens
}
