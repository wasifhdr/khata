package com.wasif.khata.feature.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.ui.component.FieldScaffold
import com.wasif.khata.core.ui.component.MoneyText
import com.wasif.khata.core.ui.component.NavCircle
import com.wasif.khata.core.ui.component.SectionLabel
import com.wasif.khata.core.ui.theme.LocalSpacing

@Composable
fun CostsScreen(
    onBack: () -> Unit,
    viewModel: VehicleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CostsContent(state = state, onBack = onBack)
}

/**
 * What the car has cost, and on what. Two lists and no chart: with a handful of
 * services a year, the numbers are the picture, and a chart library would be a
 * dependency to draw six bars.
 */
@Composable
fun CostsContent(state: VehicleUiState, onBack: () -> Unit) {
    val spacing = LocalSpacing.current

    FieldScaffold(Modifier.fillMaxSize()) { _ ->
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavCircle(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
                Text(
                    text = "COSTS",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(Modifier.padding(end = spacing.sm))
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = spacing.xxl),
            ) {
                item {
                    Column(
                        Modifier.padding(
                            horizontal = spacing.screenHorizontal,
                            vertical = spacing.sm,
                        ),
                    ) {
                        Text(
                            text = "TOTAL",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (state.totalMinor == null) {
                            Text(
                                text = "—",
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        } else {
                            MoneyText(
                                money = Money(state.totalMinor),
                                direction = null,
                                style = MaterialTheme.typography.displaySmall,
                            )
                        }
                    }
                }

                if (state.spendByItem.isNotEmpty()) {
                    item { SectionLabel("By item", top = spacing.lg) }
                    items(state.spendByItem, key = { it.name }) { row ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = spacing.screenHorizontal,
                                    vertical = spacing.sm,
                                ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = row.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${row.occurrences} ${if (row.occurrences == 1) "time" else "times"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            MoneyText(money = Money(row.totalMinor), direction = null)
                        }
                    }
                }

                if (state.spendByYear.isNotEmpty()) {
                    item { SectionLabel("By year", top = spacing.lg) }
                    items(state.spendByYear, key = { it.year }) { row ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = spacing.screenHorizontal,
                                    vertical = spacing.sm,
                                ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                // Years are bucketed in Asia/Dhaka by the query, not
                                // in UTC, so a late-December service lands where the
                                // user remembers it.
                                text = row.year,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            MoneyText(money = Money(row.totalMinor), direction = null)
                        }
                    }
                }

                if (state.spendByItem.isEmpty() && state.spendByYear.isEmpty()) {
                    item {
                        Text(
                            text = "Nothing priced yet. Costs appear once a service carries an amount.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = spacing.screenHorizontal,
                                vertical = spacing.lg,
                            ),
                        )
                    }
                }
            }
        }
    }
}
