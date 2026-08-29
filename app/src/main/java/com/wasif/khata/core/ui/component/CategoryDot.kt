package com.wasif.khata.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.core.ui.theme.LocalCategoryColors

@Composable
fun CategoryDot(
    token: String?,
    modifier: Modifier = Modifier,
    lowConfidence: Boolean = false,
) {
    val colours = LocalCategoryColors.current
    val known = token?.let { colours[it] }
    val colour = known ?: KhataPalette.categories.getValue("category_neutral")

    // Two independent signals, because colour is never load-bearing alone: the
    // ring is the pattern, the row's metadata line carries the word.
    val description = when {
        lowConfidence -> "Low confidence"
        known == null -> "Uncategorised"
        else -> null
    }

    Box(
        modifier = modifier
            .size(8.dp)
            .background(colour, CircleShape)
            .then(
                if (lowConfidence) {
                    Modifier.border(2.5.dp, colour.copy(alpha = 0.35f), CircleShape)
                } else {
                    Modifier
                },
            )
            .then(
                if (description != null) {
                    Modifier.semantics { contentDescription = description }
                } else {
                    Modifier
                },
            ),
    )
}
