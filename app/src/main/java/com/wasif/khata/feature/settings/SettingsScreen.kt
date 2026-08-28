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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wasif.khata.core.ui.component.ContextHeader
import com.wasif.khata.core.ui.theme.FieldIntensity
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.core.ui.theme.LocalSpacing

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val prefs = viewModel.state.collectAsStateWithLifecycle().value
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
            ContextHeader(heading = "Settings", subline = "Theme and budget")

            Section("Monthly budget")
            MonthlyBudgetField(
                current = prefs.monthlyBudgetMinor,
                onChange = viewModel::onMonthlyBudgetChanged,
            )

            Section("Field")
            SwatchGrid(
                colours = KhataPalette.fields.map { it.keyStop },
                selectedIndex = KhataPalette.fields.indexOf(prefs.themeSpec.field),
                onSelect = { viewModel.onFieldSelected(KhataPalette.fields[it]) },
            )

            Section("Ground")
            SwatchGrid(
                colours = KhataPalette.grounds,
                selectedIndex = KhataPalette.grounds.indexOf(prefs.themeSpec.ground),
                onSelect = { viewModel.onGroundSelected(KhataPalette.grounds[it]) },
            )

            Section("Accent")
            SwatchGrid(
                colours = KhataPalette.accents,
                selectedIndex = KhataPalette.accents.indexOf(prefs.themeSpec.accent),
                onSelect = { viewModel.onAccentSelected(KhataPalette.accents[it]) },
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
                            .clickable { viewModel.onIntensitySelected(level) },
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
                    .clickable { viewModel.onResetTheme() },
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

@Composable
private fun SwatchGrid(
    colours: List<Color>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        colours.chunked(4).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                row.forEachIndexed { colIndex, colour ->
                    val index = rowIndex * 4 + colIndex
                    Box(
                        Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(colour)
                            .border(
                                width = if (index == selectedIndex) 2.dp else 1.dp,
                                color = if (index == selectedIndex) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = MaterialTheme.shapes.small,
                            )
                            .clickable { onSelect(index) },
                    )
                }
                // Keep a short last row's cells the same width as a full row's.
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}
