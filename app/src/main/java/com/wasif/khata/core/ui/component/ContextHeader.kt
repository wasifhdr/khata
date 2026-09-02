package com.wasif.khata.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.wasif.khata.core.ui.theme.LocalSpacing
import com.wasif.khata.core.ui.theme.PageHeadingStyle
import com.wasif.khata.core.ui.theme.PageSublineStyle

/**
 * The one centred block on a module page. It carries context rather than a
 * label: the subline answers the question the screen is being asked -- is this
 * current, which month, what has it assumed -- instead of restating what the
 * user already knows from having tapped to get here.
 *
 * Module pages never show the app name; that belongs to the home screen alone.
 */
@Composable
fun ContextHeader(
    heading: String,
    subline: String,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = heading,
            style = PageHeadingStyle,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = subline,
            style = PageSublineStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.xs),
        )
    }
}
