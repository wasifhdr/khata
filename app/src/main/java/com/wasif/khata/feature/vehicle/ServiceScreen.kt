package com.wasif.khata.feature.vehicle

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wasif.khata.core.data.repository.otherMinor
import com.wasif.khata.core.model.Money
import com.wasif.khata.core.time.toDhakaLocalDate
import com.wasif.khata.core.ui.component.FieldScaffold
import com.wasif.khata.core.ui.component.KhataGlass
import com.wasif.khata.core.ui.component.MoneyText
import com.wasif.khata.core.ui.component.NavCircle
import com.wasif.khata.core.ui.component.Pill
import com.wasif.khata.core.ui.component.SectionLabel
import com.wasif.khata.core.ui.theme.LocalSpacing
import java.time.format.DateTimeFormatter

private val DayFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

@Composable
fun ServiceScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: ServiceViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ServiceContent(state = state, onBack = onBack, onEdit = onEdit)
}

/**
 * One job in full. The "other" line appears only when every item carries a cost --
 * with an unpriced item in the list the remainder is partly that item, and showing it
 * would present an unknown as a known.
 */
@Composable
fun ServiceContent(
    state: ServiceDetailUiState,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    val summary = state.summary

    FieldScaffold(Modifier.fillMaxSize()) { haze ->
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = spacing.sm, vertical = spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                NavCircle(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
                summary?.let {
                    NavCircle(
                        icon = Icons.Filled.Edit,
                        description = "Edit service",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { onEdit(it.id) },
                    )
                }
            }

            if (summary == null) return@Column

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            ) {
                Column(
                    Modifier.padding(
                        horizontal = spacing.screenHorizontal,
                        vertical = spacing.sm,
                    ),
                ) {
                    Text(
                        text = summary.servicedAt.toDhakaLocalDate().format(DayFormat),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = listOfNotNull(
                            summary.odometerKm?.let { "$it km" },
                            summary.placeName,
                        ).joinToString(" · ").ifBlank { "No reading, no workshop recorded" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    summary.mapsUrl?.let { url ->
                        Box(Modifier.padding(top = spacing.sm)) {
                            Pill(
                                text = "Open in Maps",
                                selected = false,
                                onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                },
                            )
                        }
                    }
                }

                SectionLabel("What it cost")
                Column(Modifier.padding(horizontal = spacing.screenHorizontal)) {
                    if (summary.costMinor == null) {
                        Text(
                            text = "No amount recorded",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        MoneyText(
                            money = Money(summary.costMinor),
                            direction = null,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                }

                if (state.items.isNotEmpty()) {
                    SectionLabel("What was done")
                    state.items.forEach { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = spacing.screenHorizontal,
                                    vertical = spacing.xs,
                                ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            item.costMinor?.let { MoneyText(money = Money(it), direction = null) }
                        }
                    }

                    otherMinor(summary)?.let { other ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = spacing.screenHorizontal,
                                    vertical = spacing.xs,
                                ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                // Labour and VAT, named for what it is rather than
                                // left as a gap the reader has to work out.
                                text = "Labour and everything else",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            MoneyText(money = Money(other), direction = null)
                        }
                    }
                }

                if (state.photoModels.isNotEmpty()) {
                    SectionLabel("Photos")
                    KhataGlass(
                        hazeState = haze,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.screenHorizontal),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(spacing.sm)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            state.photoModels.forEach { model ->
                                AsyncImage(
                                    model = model,
                                    contentDescription = "Photo of this service",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(120.dp)
                                        .clip(MaterialTheme.shapes.small),
                                )
                            }
                        }
                    }
                }

                summary.note?.let { note ->
                    SectionLabel("Notes")
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(
                            horizontal = spacing.screenHorizontal,
                            vertical = spacing.xs,
                        ),
                    )
                }
            }
        }
    }
}
