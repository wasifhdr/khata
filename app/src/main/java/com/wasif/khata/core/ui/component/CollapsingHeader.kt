package com.wasif.khata.core.ui.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.wasif.khata.core.ui.theme.LocalSpacing
import com.wasif.khata.core.ui.theme.LocalThemeSpec
import com.wasif.khata.core.ui.theme.PageHeadingCollapsedStyle
import com.wasif.khata.core.ui.theme.PageHeadingStyle
import com.wasif.khata.core.ui.theme.PageSublineStyle
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/** How far a page falls before its heading has finished collapsing. */
private val CollapseOver = 160.dp

/**
 * The room a page leaves for its header before anything is scrolled. Content is
 * padded down by this much so the heading sits in open space; past that point the
 * content passes underneath.
 */
val CollapsingHeaderHeight = 268.dp

/** What is left once the page has been read: one row, like any app bar. */
private val CollapsedHeight = 64.dp

/**
 * Every page opens with its heading centred in open space and gives that space up
 * as the page is read, ending as a single row shared with the back button — the
 * shape home and wallet set, carried through the whole app.
 *
 * Collapsed, the row is glass and the content passes blurred underneath it, so the
 * page reads as one surface moving rather than two panes stacked.
 *
 * Place this inside a [Box] over content that has been padded down by
 * [CollapsingHeaderHeight] and registered as a Haze source, and drive [collapse]
 * with the matching [collapseFraction].
 */
@Composable
fun BoxScope.CollapsingTopBar(
    heading: String,
    subline: String,
    collapse: Float,
    hazeState: HazeState,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    // The wallet is a root when it is the home view, where the glyph opens the hub
    // rather than going back -- back from a root exits the app, and the settings
    // gear lives only on the hub.
    navIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    navDescription: String = "Back",
) {
    val spacing = LocalSpacing.current

    // Cross-faded rather than tweened between two type sizes: a heading caught
    // mid-scale is legible at neither end, and the two states want different
    // alignment as well as different size.
    val expandedAlpha = ((1f - collapse) * 2f - 0.4f).coerceIn(0f, 1f)
    val collapsedAlpha = (collapse * 2f - 1f).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(lerp(CollapsingHeaderHeight, CollapsedHeight, collapse) + statusBarHeight()),
    ) {
        Box(Modifier.fillMaxSize().collapsingGlass(hazeState, collapse))

        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            if (onBack != null) {
                NavCircle(
                    icon = navIcon,
                    description = navDescription,
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(horizontal = spacing.sm, vertical = spacing.xs),
                )
            }

            if (expandedAlpha > 0f) {
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(horizontal = spacing.screenHorizontal)
                        .graphicsLayer { alpha = expandedAlpha },
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

            if (collapsedAlpha > 0f) {
                // Beside the back button, not under it: one row, the way any bar
                // reads once the page is being worked in rather than arrived at.
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(CollapsedHeight)
                        .padding(
                            start = if (onBack != null) spacing.xxl + spacing.md else spacing.screenHorizontal,
                            end = spacing.screenHorizontal,
                        )
                        .graphicsLayer { alpha = collapsedAlpha },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = heading,
                            // Same family and weight as the expanded heading; only
                            // the size changes.
                            style = PageHeadingCollapsedStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = subline,
                            style = PageSublineStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The bar's surface: blurred backdrop, faint tint, and a hairline along the bottom,
 * faded in as the page collapses. Separate from [KhataGlass] because a bar pinned to
 * the top edge has no top or sides to catch light, so the bevel would be wrong.
 */
@Composable
fun Modifier.collapsingGlass(hazeState: HazeState, collapse: Float): Modifier {
    val spec = LocalThemeSpec.current
    val hairline = MaterialTheme.colorScheme.outlineVariant
    if (collapse <= 0f) return this
    return this
        .graphicsLayer { alpha = collapse }
        .then(
            if (spec.usesSolidSurfaces) {
                // No field means nothing to refract; a blur over nothing is a bug
                // that looks like one.
                Modifier.background(spec.ground.copy(alpha = 0.92f))
            } else {
                Modifier.hazeEffect(
                    state = hazeState,
                    style = HazeDefaults.style(
                        backgroundColor = spec.ground,
                        tint = HazeTint(Color.White.copy(alpha = 0.055f)),
                        blurRadius = 22.dp,
                        noiseFactor = 0.04f,
                    ),
                )
            },
        )
        .drawBehind {
            drawRect(
                color = hairline,
                topLeft = Offset(0f, size.height - 1f),
                size = Size(size.width, 1f),
            )
        }
}

@Composable
private fun statusBarHeight(): Dp =
    with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }

/** 0 while the page is at rest, 1 once it has been scrolled past the header's air. */
@Composable
fun ScrollState.collapseFraction(over: Dp = CollapseOver): Float {
    val px = with(LocalDensity.current) { over.toPx() }
    return remember(this, px) {
        derivedStateOf { (value / px).coerceIn(0f, 1f) }
    }.value
}

/**
 * The same for a list. Anything past the first item counts as fully collapsed: the
 * offset resets on every item boundary, so it alone would make the header spring
 * back open halfway down a long ledger.
 */
@Composable
fun LazyListState.collapseFraction(over: Dp = CollapseOver): Float {
    val px = with(LocalDensity.current) { over.toPx() }
    return remember(this, px) {
        derivedStateOf {
            if (firstVisibleItemIndex > 0) 1f
            else (firstVisibleItemScrollOffset / px).coerceIn(0f, 1f)
        }
    }.value
}
