package com.wasif.khata.feature.restaurants

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.wasif.khata.core.data.dao.RestaurantSummary
import com.wasif.khata.core.ui.theme.KhataTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RestaurantsScreenTest {

    @get:Rule val compose = createComposeRule()

    private fun card(
        id: Long,
        name: String,
        visitCount: Int = 0,
    ) = RestaurantCard(
        summary = RestaurantSummary(
            id = id,
            name = name,
            placeId = null,
            coverMediaId = null,
            coverSha = null,
            note = null,
            placeName = null,
            mapsUrl = null,
            lastVisitedAt = if (visitCount > 0) 1_787_205_600_000L else null,
            visitCount = visitCount,
            dishAverage = null,
            ambianceAverage = null,
        ),
    )

    @Test
    fun visited_and_wishlist_tabs_switch_the_list_shown() {
        compose.setContent {
            KhataTheme {
                RestaurantsContent(
                    state = RestaurantsUiState(
                        been = listOf(card(id = 1, name = "Sultan's Dine", visitCount = 1)),
                        wishlist = listOf(card(id = 2, name = "Kacchi Bhai", visitCount = 0)),
                    ),
                    onBack = {},
                    onLogVisit = {},
                    onOpenRestaurant = {},
                    onAddToWishlist = {},
                )
            }
        }

        compose.onNodeWithText("Visited").assertIsDisplayed()
        compose.onNodeWithText("Wishlist").assertIsDisplayed()
        compose.onNodeWithText("Sultan's Dine").assertIsDisplayed()
        compose.onNodeWithText("Kacchi Bhai").assertDoesNotExist()

        compose.onNodeWithText("Wishlist").performClick()

        compose.onNodeWithText("Kacchi Bhai").assertIsDisplayed()
        compose.onNodeWithText("Sultan's Dine").assertDoesNotExist()

        compose.onNodeWithText("Visited").performClick()

        compose.onNodeWithText("Sultan's Dine").assertIsDisplayed()
        compose.onNodeWithText("Kacchi Bhai").assertDoesNotExist()
    }

    @Test
    fun the_plus_button_adds_to_the_active_tab() {
        var visitsLogged = 0
        var wishlistsAdded = 0

        compose.setContent {
            KhataTheme {
                RestaurantsContent(
                    state = RestaurantsUiState(),
                    onBack = {},
                    onLogVisit = { visitsLogged++ },
                    onOpenRestaurant = {},
                    onAddToWishlist = { wishlistsAdded++ },
                )
            }
        }

        compose.onNodeWithText("Nothing visited yet. Tap + to log a visit.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Log a visit").performClick()
        assertEquals(1, visitsLogged)
        assertEquals(0, wishlistsAdded)

        compose.onNodeWithText("Wishlist").performClick()
        compose.onNodeWithText(
            "Nothing on the wishlist yet. Tap + to add a place, or share one from Maps.",
        ).assertIsDisplayed()
        compose.onNodeWithContentDescription("Add to wishlist").performClick()
        assertEquals(1, visitsLogged)
        assertEquals(1, wishlistsAdded)
    }
}
