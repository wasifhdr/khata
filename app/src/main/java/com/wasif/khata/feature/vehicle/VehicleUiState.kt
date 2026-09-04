package com.wasif.khata.feature.vehicle

import androidx.compose.runtime.Stable
import com.wasif.khata.core.data.dao.ItemSpend
import com.wasif.khata.core.data.dao.ServiceSummary
import com.wasif.khata.core.data.dao.YearSpend

data class VehicleUiState(
    val vehicleName: String = "",
    val registration: String = "",
    val odometerKm: Int? = null,
    val services: List<ServiceSummary> = emptyList(),

    /** All derived. Absent rather than zero whenever the number would be invented. */
    val totalMinor: Long? = null,
    val thisYearMinor: Long? = null,
    val costPerKm: Double? = null,

    val spendByItem: List<ItemSpend> = emptyList(),
    val spendByYear: List<YearSpend> = emptyList(),
) {
    val isEmpty: Boolean get() = services.isEmpty()
}

@Stable
interface VehicleActions {
    fun onNameChange(value: String)
    fun onRegistrationChange(value: String)
    fun onOdometerChange(value: String)
}
