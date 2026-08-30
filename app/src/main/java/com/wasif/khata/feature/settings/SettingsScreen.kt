package com.wasif.khata.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wasif.khata.core.prefs.HomeView
import com.wasif.khata.core.prefs.KhataPreferences
import com.wasif.khata.core.ui.component.ContextHeader
import com.wasif.khata.core.ui.theme.FieldIntensity
import com.wasif.khata.core.ui.theme.FieldPalette
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.core.ui.theme.LocalSpacing
import com.wasif.khata.core.ui.theme.isLightColor

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    SettingsContent(
        prefs = viewModel.state.collectAsStateWithLifecycle().value,
        onBack = onBack,
        onHomeViewSelected = viewModel::onHomeViewSelected,
        onMonthlyBudgetChanged = viewModel::onMonthlyBudgetChanged,
        onFieldSelected = viewModel::onFieldSelected,
        onGroundSelected = viewModel::onGroundSelected,
        onAccentSelected = viewModel::onAccentSelected,
        onIntensitySelected = viewModel::onIntensitySelected,
        onResetTheme = viewModel::onResetTheme,
    )
}

@Composable
fun SettingsContent(
    prefs: KhataPreferences,
    onBack: () -> Unit,
    onHomeViewSelected: (HomeView) -> Unit,
    onMonthlyBudgetChanged: (Long?) -> Unit,
    onFieldSelected: (FieldPalette) -> Unit,
    onGroundSelected: (Color) -> Unit,
    onAccentSelected: (Color) -> Unit,
    onIntensitySelected: (FieldIntensity) -> Unit,
    onResetTheme: () -> Unit,
) {
    val spacing = LocalSpacing.current

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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

        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = spacing.xxl)) {
            ContextHeader(heading = "Settings", subline = "Home · theme · budget")

            Section("Home view")
            Row(
                Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                HomeView.entries.forEach { view ->
                    val selected = prefs.homeView == view
                    Box(
                        Modifier
                            .weight(1f)
                            .height(spacing.minTouchTarget)
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                },
                            )
                            .clickable { onHomeViewSelected(view) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = view.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
            // I6: KhataNavHost freezes its start destination for the process
            // lifetime -- back from the root has to keep exiting the app, which
            // stops being true the instant the graph could be rebuilt under a
            // live back stack. So this control cannot take effect immediately;
            // saying so here is the other half of that fix; freezing alone
            // would make the control look broken instead.
            Text(
                text = "Takes effect the next time you open Khata",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(
                    start = spacing.screenHorizontal,
                    end = spacing.screenHorizontal,
                    top = spacing.xs,
                ),
            )

            Section("Monthly budget")
            MonthlyBudgetField(
                current = prefs.monthlyBudgetMinor,
                onChange = onMonthlyBudgetChanged,
            )

            Section("Field")
            SwatchGrid(
                swatches = KhataPalette.fields.map { it.name to it.keyStop },
                selectedIndex = KhataPalette.fields.indexOf(prefs.themeSpec.field),
                onSelect = { onFieldSelected(KhataPalette.fields[it]) },
            )

            Section("Ground")
            SwatchGrid(
                swatches = KhataPalette.grounds.map { it.name to it.color },
                selectedIndex = KhataPalette.grounds.indexOfFirst { it.color == prefs.themeSpec.ground },
                onSelect = { onGroundSelected(KhataPalette.grounds[it].color) },
            )

            Section("Accent")
            SwatchGrid(
                swatches = KhataPalette.accents.map { it.name to it.color },
                selectedIndex = KhataPalette.accents.indexOfFirst { it.color == prefs.themeSpec.accent },
                onSelect = { onAccentSelected(KhataPalette.accents[it].color) },
            )

            Section("Field intensity")
            Row(
                Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                FieldIntensity.entries.forEach { level ->
                    val selected = prefs.themeSpec.intensity == level
                    Box(
                        Modifier
                            .weight(1f)
                            .height(spacing.minTouchTarget)
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                },
                            )
                            .clickable { onIntensitySelected(level) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = level.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }

            Box(
                Modifier
                    .padding(spacing.screenHorizontal)
                    .fillMaxWidth()
                    .height(spacing.minTouchTarget)
                    .clip(MaterialTheme.shapes.small)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                    .clickable { onResetTheme() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Reset to default",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun MonthlyBudgetField(current: Long?, onChange: (Long?) -> Unit) {
    val spacing = LocalSpacing.current
    // Keyed on the stored value so an external change re-seeds the field, but
    // held locally so a half-typed number is not fighting the store on every
    // keystroke.
    var text by rememberSaveable(current) {
        mutableStateOf(current?.let { (it / 100).toString() } ?: "")
    }

    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val digits = input.filter { it.isDigit() }.take(9)
            text = digits
            // Empty means unset, not zero -- the ring keys off the difference.
            onChange(if (digits.isEmpty()) null else digits.toLong() * 100)
        },
        label = { Text("Taka per month") },
        supportingText = {
            Text(
                if (text.isEmpty()) {
                    "No budget — the ring is hidden"
                } else {
                    "Shown as a ring on the wallet card"
                },
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
    )
}

@Composable
private fun Section(title: String) {
    val spacing = LocalSpacing.current
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(
            start = spacing.screenHorizontal,
            end = spacing.screenHorizontal,
            top = spacing.lg,
            bottom = spacing.sm,
        ),
    )
}

/**
 * Renders [swatches] (name to colour, kept as one pair so the label can never
 * drift from the colour it names -- see `KhataPalette.NamedSwatch`).
 *
 * Selection was border colour alone: `primary` at 2dp vs `outlineVariant` at
 * 1dp, and the unselected width barely reads as a border at all. That is
 * exactly the "colour is never the sole signal" rule applied to the one
 * screen whose subject is colour, so a checkmark badge is the second signal
 * here -- its own two colours flip based on the swatch's lightness so it stays
 * legible whether the swatch is a near-black ground or a pastel accent.
 */
@Composable
private fun SwatchGrid(
    swatches: List<Pair<String, Color>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        swatches.chunked(4).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                row.forEachIndexed { colIndex, (name, colour) ->
                    val index = rowIndex * 4 + colIndex
                    val selected = index == selectedIndex
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelect(index) }
                            .semantics {
                                contentDescription = if (selected) "$name, selected" else name
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(colour)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = MaterialTheme.shapes.small,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                // onSurface/heroStops is the pair ContrastTest
                                // already proves clears 4.5:1 against each
                                // other; isLightColor picks which one of the
                                // two goes on the badge vs the checkmark so
                                // the badge itself stays visible on the swatch.
                                val badgeBg = if (isLightColor(colour)) {
                                    KhataPalette.heroStops.last()
                                } else {
                                    KhataPalette.onSurface
                                }
                                val badgeIcon = if (isLightColor(colour)) {
                                    KhataPalette.onSurface
                                } else {
                                    KhataPalette.heroStops.last()
                                }
                                Box(
                                    Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(badgeBg),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = badgeIcon,
                                        modifier = Modifier.size(12.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = spacing.xs).fillMaxWidth(),
                        )
                    }
                }
                // Keep a short last row's cells the same width as a full row's.
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}
