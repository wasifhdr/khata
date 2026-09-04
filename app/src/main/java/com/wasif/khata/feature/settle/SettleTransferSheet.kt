package com.wasif.khata.feature.settle

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wasif.khata.core.ui.component.SectionLabel
import com.wasif.khata.core.ui.theme.LocalSpacing

/**
 * The question the notification asks, asked again where it can be answered properly:
 * with the original message in front of you and the accounts to choose from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleTransferSheet(
    transactionId: Long,
    onDismiss: () -> Unit,
    viewModel: SettleTransferViewModel = hiltViewModel(
        creationCallback = { factory: SettleTransferViewModel.Factory ->
            factory.create(transactionId)
        },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    val sheetState = rememberModalBottomSheetState()

    // Only an answer closes it, so a stray swipe cannot look like one.
    LaunchedEffect(state.done) { if (state.done) onDismiss() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screenHorizontal),
        ) {
            Text(
                text = "Where did this go?",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${state.amount.format()} · ${state.merchantRaw ?: "Transfer"}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacing.xs),
            )

            // "EBL Account Transfer" alone cannot say whether this went to another of
            // your accounts or to someone else. This is where that is settled.
            state.originalMessage?.let { message ->
                SectionLabel(top = spacing.md, text = "Original message")
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionLabel(top = spacing.md, text = "It moved to one of my accounts")
            state.choices.forEach { account ->
                OutlinedButton(
                    onClick = { viewModel.onAccountChosen(account.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.xs),
                ) {
                    Text(account.name)
                }
            }

            TextButton(
                onClick = viewModel::onNotMine,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.sm, bottom = spacing.xl),
            ) {
                Text("It left my accounts")
            }
        }
    }
}
