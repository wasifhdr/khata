package com.wasif.khata.feature.unmatched

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wasif.khata.core.time.DHAKA
import com.wasif.khata.core.ui.component.CollapsingHeaderHeight
import com.wasif.khata.core.ui.component.CollapsingTopBar
import com.wasif.khata.core.ui.component.collapseFraction
import com.wasif.khata.core.ui.component.FieldScaffold
import com.wasif.khata.core.ui.component.KhataIcons
import com.wasif.khata.core.ui.component.Pill
import com.wasif.khata.core.ui.theme.BengaliBodyStyle
import com.wasif.khata.core.ui.theme.LocalSpacing
import dev.chrisbanes.haze.hazeSource
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val receivedFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

@Composable
fun UnmatchedScreen(
    onBack: () -> Unit,
    onWriteRule: (Long) -> Unit,
    viewModel: UnmatchedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UnmatchedContent(
        state = state,
        onBack = onBack,
        onWriteRule = onWriteRule,
        onNotATransaction = viewModel::onNotATransaction,
        onAskGemini = viewModel::onAskGemini,
        canAskGemini = state.canAskGemini,
    )
}

@Composable
fun UnmatchedContent(
    state: UnmatchedUiState,
    onBack: () -> Unit,
    onWriteRule: (Long) -> Unit,
    onNotATransaction: (Long) -> Unit,
    onAskGemini: (Long) -> Unit,
    canAskGemini: Boolean,
) {
    val spacing = LocalSpacing.current
    val listState = rememberLazyListState()

    FieldScaffold(Modifier.fillMaxSize()) { haze ->
        Box(Modifier.fillMaxSize()) {
          Column(
              Modifier
                  .fillMaxSize()
                  .hazeSource(haze)
                  .imePadding()
                  .windowInsetsPadding(WindowInsets.navigationBars),
          ) {
            // The header offset belongs to whichever child comes first. Giving it to
            // both the notice and the list below stacked two full header heights of
            // empty space, with the notice stranded in the middle of it.
            val topInset = if (state.line == null) CollapsingHeaderHeight else spacing.sm

            state.line?.let { notice ->
                Text(
                    text = notice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = spacing.screenHorizontal,
                        end = spacing.screenHorizontal,
                        top = CollapsingHeaderHeight,
                    ),
                )
            }

            if (state.isEmpty) {
                Box(Modifier.padding(top = topInset)) { EmptyNote() }
            } else {
                // No glass on rows: one blur pass per row per frame (DESIGN.md §5).
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = topInset,
                        bottom = spacing.xxl,
                    ),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        UnmatchedRow(
                            message = message,
                            enabled = !state.isWorking,
                            onWriteRule = { onWriteRule(message.id) },
                            onNotATransaction = { onNotATransaction(message.id) },
                            onAskGemini = { onAskGemini(message.id) },
                            canAskGemini = canAskGemini,
                        )
                    }
                }
            }
          }

            CollapsingTopBar(
                heading = "Unread",
                subline = if (state.isEmpty) {
                    "Every message has a rule"
                } else {
                    "${state.messages.size} message${if (state.messages.size == 1) "" else "s"} without a rule"
                },
                collapse = listState.collapseFraction(),
                hazeState = haze,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun EmptyNote() {
    val spacing = LocalSpacing.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.lg),
    ) {
        Text(
            text = "Nothing unread",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Every message Khata has seen matched a rule. If your bank changes its " +
                "wording, the messages it no longer understands will collect here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = spacing.sm),
        )
    }
}

@Composable
private fun UnmatchedRow(
    message: UnmatchedMessage,
    enabled: Boolean,
    onWriteRule: () -> Unit,
    onNotATransaction: () -> Unit,
    onAskGemini: () -> Unit,
    canAskGemini: Boolean,
) {
    val spacing = LocalSpacing.current
    val received = Instant.ofEpochMilli(message.receivedAt).atZone(DHAKA).toLocalDate()
        .format(receivedFormatter)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.md),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = message.sender,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = received,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = message.body,
            // Instrument Sans has no Bengali glyphs; a raw SMS body is unpredictable.
            style = if (message.isBengali) BengaliBodyStyle else MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = spacing.xs),
        )

        // Two answers, because most of what a bank sends is not a transaction at
        // all. Teaching is the emphasised one; hiding is quieter but present, or
        // the list of verification codes never ends.
        //
        // Ask Gemini appears only with a key set. The automatic fallback fires as a
        // message arrives, so anything already sitting here when the key was added
        // would never be offered otherwise.
        Row(
            Modifier.padding(top = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Pill("Write a rule", selected = true, enabled = enabled, onClick = onWriteRule)
            if (canAskGemini) {
                Pill(
                    text = "Ask Gemini",
                    selected = false,
                    enabled = enabled,
                    leadingIcon = KhataIcons.Sparkle,
                    onClick = onAskGemini,
                )
            }
            Pill("Not a transaction", selected = false, enabled = enabled, onClick = onNotATransaction)
        }
    }
}
