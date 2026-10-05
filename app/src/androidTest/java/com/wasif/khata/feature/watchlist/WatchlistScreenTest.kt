package com.wasif.khata.feature.watchlist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.wasif.khata.core.data.dao.TitleSummary
import com.wasif.khata.core.data.entity.TitleKind
import com.wasif.khata.core.ui.theme.KhataTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WatchlistScreenTest {

    @get:Rule val compose = createComposeRule()

    private val watchlistTab = hasText("Watchlist") and hasClickAction()

    private fun summary(
        id: Long = 1,
        name: String = "Heat",
        year: Int? = 1995,
        kind: TitleKind = TitleKind.FILM,
        posterSha: String? = null,
        lastWatchedAt: Long? = null,
        watchCount: Int = 0,
        verdict: Double? = null,
    ) = TitleSummary(
        id = id,
        name = name,
        year = year,
        kind = kind,
        tmdbId = 949,
        tmdbRating = 7.9,
        tmdbRatingAt = 1_787_205_600_000L,
        posterMediaId = null,
        posterSha = posterSha,
        note = null,
        lastWatchedAt = lastWatchedAt,
        watchCount = watchCount,
        verdict = verdict,
    )

    @Test
    fun watched_and_watchlist_tabs_switch_the_list_shown() {
        compose.setContent {
            KhataTheme {
                WatchlistContent(
                    state = WatchlistUiState(
                        queue = listOf(TitleCard(summary(id = 1, name = "Heat"))),
                        watched = listOf(
                            TitleCard(
                                summary(
                                    id = 2,
                                    name = "The Wire",
                                    kind = TitleKind.SERIES,
                                    // 2026-08-20 12:00 Dhaka.
                                    lastWatchedAt = 1_787_205_600_000L,
                                    watchCount = 1,
                                    verdict = 4.5,
                                ),
                            ),
                        ),
                    ),
                    onBack = {},
                    onOpenTitle = {},
                    onAddTitle = {},
                )
            }
        }

        compose.onNodeWithText("Watched").assertIsDisplayed()
        compose.onNode(watchlistTab).assertIsDisplayed()
        compose.onNodeWithText("The Wire").assertIsDisplayed()
        // The user's own average, starred. TMDB's 7.9 is not shown in a list.
        compose.onNodeWithText("★ 4.5").assertIsDisplayed()
        compose.onNodeWithText("Heat").assertDoesNotExist()

        compose.onNode(watchlistTab).performClick()

        compose.onNodeWithText("Heat").assertIsDisplayed()
        compose.onNodeWithText("The Wire").assertDoesNotExist()

        compose.onNodeWithText("Watched").performClick()

        compose.onNodeWithText("The Wire").assertIsDisplayed()
        compose.onNodeWithText("Heat").assertDoesNotExist()
    }

    @Test
    fun a_title_with_no_poster_shows_its_initials_rather_than_a_placeholder_graphic() {
        compose.setContent {
            KhataTheme {
                WatchlistContent(
                    state = WatchlistUiState(queue = listOf(TitleCard(summary(name = "Heat")))),
                    onBack = {},
                    onOpenTitle = {},
                    onAddTitle = {},
                )
            }
        }

        compose.onNode(watchlistTab).performClick()
        compose.onNodeWithText("HE").assertIsDisplayed()
        compose.onNodeWithText("1995 · Film").assertIsDisplayed()
    }

    @Test
    fun an_empty_module_says_so_and_the_plus_button_adds_to_the_active_tab() {
        val addedWatched = mutableListOf<Boolean>()

        compose.setContent {
            KhataTheme {
                WatchlistContent(
                    state = WatchlistUiState(),
                    onBack = {},
                    onOpenTitle = {},
                    onAddTitle = { watched -> addedWatched += watched },
                )
            }
        }

        compose.onNodeWithText("Nothing watched yet. Tap + to log something you've seen.").assertIsDisplayed()

        compose.onNodeWithContentDescription("Log watched").performClick()
        assertEquals(listOf(true), addedWatched)

        compose.onNode(watchlistTab).performClick()
        compose.onNodeWithText("Nothing on the watchlist yet. Tap + to add something you mean to watch.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add to watchlist").performClick()
        assertEquals(listOf(true, false), addedWatched)
    }

    @Test
    fun the_add_screen_shows_the_manual_fields_alongside_the_search_box() {
        compose.setContent {
            KhataTheme {
                AddTitleContent(
                    state = AddTitleUiState(),
                    actions = NoopAddTitleActions,
                    onBack = {},
                )
            }
        }

        // Both present at once: with no key or no network the fields are the screen.
        compose.onNodeWithText("A film or a series").assertIsDisplayed()
        compose.onNodeWithText("What is it called?").assertIsDisplayed()
        compose.onNodeWithText("Film").assertIsDisplayed()
        compose.onNodeWithText("Series").assertIsDisplayed()
    }

    private object NoopAddTitleActions : AddTitleActions {
        override fun onQueryChange(value: String) = Unit
        override fun onResultPick(result: com.wasif.khata.core.watch.TmdbResult) = Unit
        override fun onNameChange(value: String) = Unit
        override fun onYearChange(value: String) = Unit
        override fun onKindChange(kind: TitleKind) = Unit
        override fun onNoteChange(value: String) = Unit
        override fun onRecommenderInputChange(value: String) = Unit
        override fun onRecommenderAdded() = Unit
        override fun onRecommenderRemoved(name: String) = Unit
        override fun onLogWatchNowChange(value: Boolean) = Unit
        override fun onSave() = Unit
    }
}
