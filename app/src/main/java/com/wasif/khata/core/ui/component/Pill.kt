package com.wasif.khata.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.wasif.khata.core.ui.theme.LocalSpacing

/**
 * A tap-to-choose control. Selection is carried by fill, not by colour alone —
 * the caller is expected to word the label so it reads correctly either way.
 */
@Composable
fun Pill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Box(
        modifier
            .height(spacing.minTouchTarget)
            .clip(MaterialTheme.shapes.small)
            // Selected controls stay opaque: a translucent selected chip reads as less
            // committed than an opaque one, which inverts what selection means.
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
            )
            .clickable(onClickLabel = contentDescription, onClick = onClick)
            .padding(horizontal = spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
