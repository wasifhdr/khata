package com.wasif.khata.feature.vehicle

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.wasif.khata.core.data.dao.ItemSpend
import com.wasif.khata.core.data.dao.ServiceSummary
import com.wasif.khata.core.ui.theme.KhataTheme
import org.junit.Rule
import org.junit.Test

class VehicleScreenTest {

    @get:Rule val compose = createComposeRule()

    private val noopActions = object : VehicleActions {
        override fun onNameChange(value: String) = Unit
        override fun onRegistrationChange(value: String) = Unit
        override fun onOdometerChange(value: String) = Unit
    }

    private fun service(
        costMinor: Long? = 500_00L,
        itemNames: String? = "Oil filter, Brake pads",
        placeName: String? = "Navana Workshop",
        odometerKm: Int? = 42_000,
    ) = ServiceSummary(
        id = 1,
        // 2026-08-20 12:00 in Dhaka, which is 06:00 UTC the same day.
        servicedAt = 1_787_205_600_000L,
        odometerKm = odometerKm,
        costMinor = costMinor,
        note = null,
        placeId = 1,
        placeName = placeName,
        mapsUrl = null,
        itemNames = itemNames,
        itemCount = 2,
        itemCostTotal = 380_00L,
        unpricedItemCount = 0,
    )

    @Test
    fun a_service_row_shows_its_items_and_its_workshop() {
        compose.setContent {
            KhataTheme {
                VehicleContent(
                    state = VehicleUiState(
                        vehicleName = "Car",
                        odometerKm = 42_000,
                        services = listOf(service()),
                        totalMinor = 500_00L,
                    ),
                    actions = noopActions,
                    onBack = {},
                    onOpenService = {},
                    onLogService = {},
                    onOpenCosts = {},
                )
            }
        }

        compose.onNodeWithText("Oil filter, Brake pads").assertIsDisplayed()
        compose.onNodeWithText("20 Aug 2026 · 42000 km · Navana Workshop").assertIsDisplayed()
    }

    @Test
    fun an_undefined_cost_per_km_shows_a_dash_rather_than_a_number() {
        compose.setContent {
            KhataTheme {
                VehicleContent(
                    // One reading is not a span, so costPerKm is null upstream.
                    state = VehicleUiState(
                        vehicleName = "Car",
                        services = listOf(service()),
                        totalMinor = 500_00L,
                        costPerKm = null,
                    ),
                    actions = noopActions,
                    onBack = {},
                    onOpenService = {},
                    onLogService = {},
                    onOpenCosts = {},
                )
            }
        }

        compose.onNodeWithText("PER KM").assertIsDisplayed()
        compose.onNodeWithText("—").assertIsDisplayed()
    }

    @Test
    fun an_empty_module_says_so_rather_than_showing_zeroes() {
        compose.setContent {
            KhataTheme {
                VehicleContent(
                    state = VehicleUiState(vehicleName = "Car"),
                    actions = noopActions,
                    onBack = {},
                    onOpenService = {},
                    onLogService = {},
                    onOpenCosts = {},
                )
            }
        }

        compose.onNodeWithText("No services yet. The first one you log starts the history.")
            .assertIsDisplayed()
        compose.onNodeWithText("Log a service").assertIsDisplayed()
    }

    @Test
    fun the_costs_screen_lists_spend_by_item() {
        compose.setContent {
            KhataTheme {
                CostsContent(
                    state = VehicleUiState(
                        totalMinor = 500_00L,
                        spendByItem = listOf(ItemSpend("Oil filter", 250_00L, 2)),
                    ),
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("Oil filter").assertIsDisplayed()
        compose.onNodeWithText("2 times").assertIsDisplayed()
    }
}
