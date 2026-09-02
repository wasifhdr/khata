package com.wasif.khata.feature.unmatched

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wasif.khata.core.time.DHAKA
import com.wasif.khata.core.ui.component.ContextHeader
import com.wasif.khata.core.ui.component.FieldScaffold
import com.wasif.khata.core.ui.theme.BengaliBodyStyle
import com.wasif.khata.core.ui.theme.LocalSpacing
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
    )
}

@Composable
fun UnmatchedContent(
    state: UnmatchedUiState,
    onBack: () -> Unit,
    onWriteRule: (Long) -> Unit,
    onNotATransaction: (Long) -> Unit,
) {
    val spacing = LocalSpacing.current

    FieldScaffold(Modifier.fillMaxSize()) { _ ->
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = spacing.xs)) {
                Box(
                    Modifier
                        .size(spacing.minTouchTarget)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            ContextHeader(
                heading = "Unread",
                subline = if (state.isEmpty) {
                    "Every message has a rule"
                } else {
                    "${state.messages.size} message${if (state.messages.size == 1) "" else "s"} without a rule"
                },
            )

            state.line?.let { notice ->
                Text(
                    text = notice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = spacing.screenHorizontal,
                        end = spacing.screenHorizontal,
                        top = spacing.sm,
                    ),
                )
            }

            if (state.isEmpty) {
                EmptyNote()
            } else {
                // No glass on rows: one blur pass per row per frame (DESIGN.md §5).
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = spacing.md,
                        bottom = spacing.xxl,
                    ),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        UnmatchedRow(
                            message = message,
                            enabled = !state.isWorking,
                            onWriteRule = { onWriteRule(message.id) },
                            onNotATransaction = { onNotATransaction(message.id) },
                        )
                    }
                }
            }
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
        Row(
            Modifier.padding(top = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Box(
                Modifier
                    .height(spacing.minTouchTarget)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable(enabled = enabled, onClick = onWriteRule)
                    .padding(horizontal = spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Write a rule",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            Box(
                Modifier
                    .height(spacing.minTouchTarget)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable(enabled = enabled, onClick = onNotATransaction)
                    .padding(horizontal = spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Not a transaction",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
