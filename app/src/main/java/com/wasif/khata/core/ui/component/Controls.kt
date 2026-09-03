package com.wasif.khata.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wasif.khata.core.ui.theme.LocalSpacing

/**
 * A tap-to-choose control, and the app's only filled button. Selection is carried
 * by fill, not by colour alone — the caller is expected to word the label so it
 * reads correctly either way.
 *
 * A disabled control drops to the quieter of the two text tiers rather than to
 * `outline`, which is reserved for borders.
 */
@Composable
fun Pill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
    leadingIcon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Box(
        modifier
            .height(spacing.minTouchTarget)
            .clip(MaterialTheme.shapes.small)
            // Selected controls stay opaque: a translucent selected chip reads as less
            // committed than an opaque one, which inverts what selection means. A
            // disabled one gives the fill up, or it still reads as the live answer.
            .background(
                if (selected && enabled) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = spacing.md)
            // Added rather than cleared, so the label stays findable by text and the
            // disabled state survives alongside the sentence.
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val content = when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
            selected -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    // The label already says what this does; the mark is decoration
                    // beside it, and a screen reader repeating it would be noise.
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(spacing.xs))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = content,
            )
        }
    }
}

/** A bare icon in a touch-target-sized circle: back, close, delete, the month arrows. */
@Composable
fun NavCircle(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    iconSize: Dp = 24.dp,
) {
    val spacing = LocalSpacing.current
    Box(
        modifier
            .size(spacing.minTouchTarget)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            // Disabled rather than hidden: a control that vanishes is harder to
            // understand than one that is visibly unavailable. WCAG exempts a
            // disabled control from the border floor, but "exempt from 3:1" is not
            // "exempt from visible" -- `outline` is dimmer than any enabled tint
            // but still plainly present, not a hairline.
            tint = if (enabled) tint else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** The uppercase label above a group of controls. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, top: Dp? = null) {
    val spacing = LocalSpacing.current
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(
            start = spacing.screenHorizontal,
            end = spacing.screenHorizontal,
            top = top ?: spacing.lg,
            bottom = spacing.sm,
        ),
    )
}
