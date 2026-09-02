package com.wasif.khata.feature.unmatched

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.data.dao.RawMessageDao
import com.wasif.khata.core.model.RawMessageStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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
) {
    val isEmpty: Boolean get() = messages.isEmpty()
}

@HiltViewModel
class UnmatchedViewModel @Inject constructor(
    rawMessageDao: RawMessageDao,
) : ViewModel() {

    val state: StateFlow<UnmatchedUiState> = rawMessageDao
        .observeByStatus(RawMessageStatus.UNMATCHED)
        .map { rows ->
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
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UnmatchedUiState())
}
