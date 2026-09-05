package com.wasif.khata.feature.vehicle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wasif.khata.core.data.dao.ServiceSummary
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.time.toDhakaLocalDate
import com.wasif.khata.core.ui.component.CollapsingHeaderHeight
import com.wasif.khata.core.ui.component.CollapsingTopBar
import com.wasif.khata.core.ui.component.FieldScaffold
import com.wasif.khata.core.ui.component.collapseFraction
import com.wasif.khata.core.ui.component.KhataGlass
import com.wasif.khata.core.ui.component.MoneyText
import com.wasif.khata.core.ui.component.NavCircle
import com.wasif.khata.core.ui.component.Pill
import com.wasif.khata.core.ui.component.SectionLabel
import com.wasif.khata.core.ui.theme.LocalSpacing
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.time.format.DateTimeFormatter

private val DayFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

@Composable
fun VehicleScreen(
    onBack: () -> Unit,
    onOpenService: (Long) -> Unit,
    onLogService: () -> Unit,
    onOpenCosts: () -> Unit,
    viewModel: VehicleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    VehicleContent(
        state = state,
        actions = viewModel,
        onBack = onBack,
        onOpenService = onOpenService,
        onLogService = onLogService,
        onOpenCosts = onOpenCosts,
    )
}

/**
 * The history, with what it has cost sitting above it. Every figure in the header is
 * derived and every one of them is absent rather than zero when the number would be
 * invented -- a car with one odometer reading has no cost per kilometre, and saying
 * so is the honest answer.
 */
@Composable
fun VehicleContent(
    state: VehicleUiState,
    actions: VehicleActions,
    onBack: () -> Unit,
    onOpenService: (Long) -> Unit,
    onLogService: () -> Unit,
    onOpenCosts: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val list = rememberLazyListState()

    FieldScaffold(Modifier.fillMaxSize()) { haze ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize().hazeSource(haze),
                contentPadding = PaddingValues(top = CollapsingHeaderHeight, bottom = spacing.xxl),
            ) {
                item { VehicleHeader(haze = haze, state = state, actions = actions) }

                item { CostSummary(haze = haze, state = state, onOpenCosts = onOpenCosts) }

                if (state.isEmpty) {
                    item {
                        Text(
                            text = "No services yet. The first one you log starts the history.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = spacing.screenHorizontal,
                                vertical = spacing.lg,
                            ),
                        )
                    }
                } else {
                    item { SectionLabel("History", top = spacing.lg) }
                    items(state.services, key = { it.id }) { row ->
                        ServiceRow(row) { onOpenService(row.id) }
                    }
                }

                item { Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) }
            }

            CollapsingTopBar(
                heading = "Car service",
                subline = Subline,
                collapse = list.collapseFraction(),
                hazeState = haze,
                onBack = onBack,
            )

            // The one control that starts a job, where the thumb already is.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
            ) {
                Pill(
                    text = "Log a service",
                    selected = true,
                    modifier = Modifier.fillMaxWidth().testTag("logService"),
                    onClick = onLogService,
                )
            }
        }
    }
}

/**
 * Name, registration and odometer, edited in place. Three fields opened twice a year
 * do not earn a screen of their own, and the edit is explicit rather than
 * save-on-blur so a half-typed reading is never committed.
 */
private const val Subline = "What has been done, and what it cost"

@Composable
private fun VehicleHeader(haze: HazeState, state: VehicleUiState, actions: VehicleActions) {
    val spacing = LocalSpacing.current
    var editing by remember { mutableStateOf(false) }

    KhataGlass(
        hazeState = haze,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
    ) {
        Column(Modifier.fillMaxWidth().padding(spacing.md)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    if (editing) {
                        OutlinedTextField(
                            value = state.vehicleName,
                            onValueChange = actions::onNameChange,
                            label = { Text("Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("vehicleNameField"),
                        )
                        OutlinedTextField(
                            value = state.registration,
                            onValueChange = actions::onRegistrationChange,
                            label = { Text("Registration") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = spacing.sm)
                                .testTag("registrationField"),
                        )
                        OutlinedTextField(
                            value = state.odometerKm?.toString().orEmpty(),
                            onValueChange = actions::onOdometerChange,
                            label = { Text("Odometer") },
                            suffix = { Text("km") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = spacing.sm)
                                .testTag("vehicleOdometerField"),
                        )
                    } else {
                        Text(
                            text = state.vehicleName,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = listOfNotNull(
                                state.registration.takeIf { it.isNotBlank() },
                                state.odometerKm?.let { "$it km" },
                            ).joinToString(" · ").ifBlank { "No registration or reading yet" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                NavCircle(
                    icon = if (editing) Icons.Filled.Check else Icons.Filled.Edit,
                    description = if (editing) "Done editing the car" else "Edit the car",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { editing = !editing },
                )
            }
        }
    }
}

@Composable
private fun CostSummary(haze: HazeState, state: VehicleUiState, onOpenCosts: () -> Unit) {
    val spacing = LocalSpacing.current
    KhataGlass(
        hazeState = haze,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal)
            .clickable(onClick = onOpenCosts),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Figure(label = "TOTAL", minor = state.totalMinor)
            Figure(label = "THIS YEAR", minor = state.thisYearMinor)
            Column {
                Text(
                    text = "PER KM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    // Absent, never invented: fewer than two readings has no answer.
                    text = state.costPerKm?.let { Money(it.toLong()).format() } ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun Figure(label: String, minor: Long?) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (minor == null) {
            Text(
                text = "—",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            MoneyText(
                money = Money(minor),
                direction = null,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun ServiceRow(row: ServiceSummary, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = listOfNotNull(
                    row.servicedAt.toDhakaLocalDate().format(DayFormat),
                    row.odometerKm?.let { "$it km" },
                    row.placeName,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            row.itemNames?.let { names ->
                Text(
                    text = names,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        row.costMinor?.let { MoneyText(money = Money(it), direction = null) }
    }
}
