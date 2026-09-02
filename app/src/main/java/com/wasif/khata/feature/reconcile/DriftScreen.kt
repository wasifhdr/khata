package com.wasif.khata.feature.reconcile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wasif.khata.core.data.repository.BalanceDrift
import com.wasif.khata.core.time.DHAKA
import com.wasif.khata.core.ui.component.collapseFraction
import com.wasif.khata.core.ui.component.CollapsingHeaderHeight
import com.wasif.khata.core.ui.component.CollapsingTopBar
import com.wasif.khata.core.ui.component.FieldScaffold
import com.wasif.khata.core.ui.theme.AmountTextStyle
import com.wasif.khata.core.ui.theme.LocalSpacing
import dev.chrisbanes.haze.hazeSource
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val driftDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH)

@Composable
fun DriftScreen(
    onBack: () -> Unit,
    viewModel: DriftViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DriftContent(
        state = state,
        onBack = onBack,
        onRecordAdjustment = viewModel::onRecordAdjustment,
    )
}

@Composable
fun DriftContent(
    state: DriftUiState,
    onBack: () -> Unit,
    onRecordAdjustment: (BalanceDrift) -> Unit,
) {
    val spacing = LocalSpacing.current
    val scroll = rememberScrollState()

    FieldScaffold(Modifier.fillMaxSize()) { haze ->
        Box(Modifier.fillMaxSize()) {

            Column(
                Modifier
                    .fillMaxSize()
                    .hazeSource(haze)
                    .imePadding()
                    .verticalScroll(scroll)
                    .padding(top = CollapsingHeaderHeight, bottom = spacing.xxl)
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {

                state.error?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.md),
                    )
                }

                if (state.isReconciled) {
                    ReconciledNote()
                } else {
                    state.drifts.forEach { drift ->
                        DriftRow(
                            drift = drift,
                            isRecording = state.recordingFor == drift.accountId,
                            onRecord = { onRecordAdjustment(drift) },
                        )
                    }
                }
            }

            CollapsingTopBar(
                heading = "Reconcile",
                subline = if (state.isReconciled) {
                    "Every taka is accounted for"
                } else {
                    "${state.drifts.size} account${if (state.drifts.size == 1) "" else "s"} with money unaccounted for"
                },
                collapse = scroll.collapseFraction(),
                hazeState = haze,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun ReconciledNote() {
    val spacing = LocalSpacing.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.lg),
    ) {
        Text(
            text = "Every balance matches",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Khata's running total agrees with the balance your banks last reported. " +
                "A gap here usually means cash you spent without recording it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = spacing.sm),
        )
    }
}

@Composable
private fun DriftRow(
    drift: BalanceDrift,
    isRecording: Boolean,
    onRecord: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val short = drift.gap.abs().format()
    // The sign is a word, not only a colour: DESIGN.md rule 3.
    val direction = if (drift.gap.minor > 0) "arrived" else "left"
    val since = Instant.ofEpochMilli(drift.reportedAt).atZone(DHAKA).toLocalDate()
        .format(driftDateFormatter)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.md),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = drift.accountName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = short,
                style = AmountTextStyle,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Text(
            // The balance itself is not in question -- it comes from the bank's own
            // messages. What this says is how much of it has no message behind it.
            text = "$short $direction with no message to explain it, as of $since. " +
                "Your balance is right; these transactions are missing.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = spacing.xs),
        )

        Box(
            Modifier
                .padding(top = spacing.md)
                .fillMaxWidth()
                .height(spacing.minTouchTarget)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable(enabled = !isRecording, onClick = onRecord)
                .clearAndSetSemantics {
                    contentDescription = "Add the unaccounted ${drift.accountName} money as an adjustment"
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isRecording) "Adding…" else "Add it as an adjustment",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
