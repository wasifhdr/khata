package com.wasif.khata.feature.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wasif.khata.core.ui.component.CollapsingHeaderHeight
import com.wasif.khata.core.ui.component.CollapsingTopBar
import com.wasif.khata.core.ui.component.FieldScaffold
import com.wasif.khata.core.ui.component.MoneyText
import com.wasif.khata.core.ui.component.SectionLabel
import com.wasif.khata.core.ui.component.collapseFraction
import com.wasif.khata.core.ui.theme.AmountTextStyle
import com.wasif.khata.core.ui.theme.LocalSpacing
import dev.chrisbanes.haze.hazeSource
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun InsightsScreen(
    onBack: () -> Unit,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    InsightsContent(state = state, onBack = onBack)
}

/** "Up 24% on last month", or the honest absence of a comparison. */
private fun changeInWords(fraction: Float?, spendsMore: Boolean): String = when (fraction) {
    null -> "No comparison yet"
    else -> {
        val percent = (abs(fraction) * 100).roundToInt()
        if (spendsMore) "Up $percent% on last month" else "Down $percent% on last month"
    }
}

@Composable
fun InsightsContent(state: InsightsUiState, onBack: () -> Unit) {
    val spacing = LocalSpacing.current
    val scroll = rememberScrollState()

    FieldScaffold(Modifier.fillMaxSize()) { haze ->
        Box(Modifier.fillMaxSize()) {

            Column(
                Modifier
                    .fillMaxSize()
                    .hazeSource(haze)
                    .verticalScroll(scroll)
                    .padding(top = CollapsingHeaderHeight, bottom = spacing.xxl)
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {

                SectionLabel("This month")
                Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                    MoneyText(money = state.monthSpend, style = AmountTextStyle)
                    Text(
                        text = changeInWords(state.changeFraction, state.spendsMore),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (state.categories.isNotEmpty()) {
                    SectionLabel("By category", top = spacing.lg)
                    state.categories.forEach { CategoryRow(it) }
                }

                if (state.merchants.isNotEmpty()) {
                    SectionLabel("Top merchants", top = spacing.lg)
                    state.merchants.forEach { merchant ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = spacing.screenHorizontal,
                                    vertical = spacing.sm,
                                )
                                .clearAndSetSemantics {
                                    contentDescription =
                                        "${merchant.name}, ${merchant.amount.format()}"
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = merchant.name,
                                style = MaterialTheme.typography.bodyLarge,
                                // Set explicitly: DESIGN.md 1.6, an unset role is
                                // whatever the last provider left behind, which on a
                                // dark ground reads as near-black.
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            MoneyText(money = merchant.amount, direction = null)
                        }
                    }
                }

                if (state.categories.isEmpty()) {
                    Text(
                        text = "Nothing spent this month yet. Once there is, this is where " +
                            "it gets compared against last month.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = spacing.screenHorizontal,
                            vertical = spacing.md,
                        ),
                    )
                }
            }

            CollapsingTopBar(
                heading = "Insights",
                subline = changeInWords(state.changeFraction, state.spendsMore),
                collapse = scroll.collapseFraction(),
                hazeState = haze,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun CategoryRow(insight: CategoryInsight) {
    val spacing = LocalSpacing.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = insight.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // The word, not just a colour: DESIGN.md 1.3 forbids colour as the
                // only signal, and being over is the whole point of setting a limit.
                if (insight.isOverBudget) {
                    Text(
                        text = "  over",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            MoneyText(money = insight.amount, direction = null)
        }

        // Against the limit where one is set, and nothing where none is -- an absent
        // limit draws no bar rather than an empty one.
        insight.budgetFraction?.let { fraction ->
            Box(
                Modifier
                    .padding(top = spacing.xs)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(4.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (insight.isOverBudget) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        ),
                )
            }
        }

        insight.changeFraction?.let { change ->
            Text(
                text = changeInWords(change, insight.spendsMore),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
