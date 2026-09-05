package com.wasif.khata.feature.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wasif.khata.core.data.entity.VehicleEntity
import com.wasif.khata.core.data.repository.VehicleRepository
import com.wasif.khata.core.data.repository.costPerKm
import com.wasif.khata.core.time.KhataClock
import com.wasif.khata.core.time.toDhakaLocalDate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VehicleViewModel @Inject constructor(
    private val repository: VehicleRepository,
    private val clock: KhataClock,
) : ViewModel(), VehicleActions {

    // The vehicle is created on first open rather than seeded by the migration, so
    // the row exists on a fresh install and after an upgrade alike.
    private val vehicleId = MutableStateFlow<Long?>(null)

    init {
        viewModelScope.launch { vehicleId.value = repository.findOrCreateVehicle() }
    }

    val state: StateFlow<VehicleUiState> = vehicleId
        .filterNotNull()
        .flatMapLatest { id ->
            combine(
                repository.observeVehicle(),
                repository.observeServices(id),
                repository.costTotals(id),
                repository.spendByItem(id),
                repository.spendByYear(id),
            ) { vehicle, services, totals, byItem, byYear ->
                val thisYear = clock.now().toDhakaLocalDate().year.toString()
                VehicleUiState(
                    vehicleName = vehicle?.name.orEmpty(),
                    registration = vehicle?.registration.orEmpty(),
                    odometerKm = vehicle?.odometerKm,
                    services = services,
                    totalMinor = totals.totalMinor,
                    // Absent, not zero: a year with no services spent nothing, and
                    // "৳0 this year" reads as a measurement rather than a silence.
                    thisYearMinor = byYear.firstOrNull { it.year == thisYear }?.totalMinor,
                    costPerKm = costPerKm(totals),
                    spendByItem = byItem,
                    spendByYear = byYear,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = VehicleUiState(),
        )

    override fun onNameChange(value: String) = edit { it.copy(name = value) }

    override fun onRegistrationChange(value: String) =
        edit { it.copy(registration = value.takeIf { v -> v.isNotBlank() }) }

    /**
     * Typed by hand here, so an unreadable value clears the reading rather than
     * writing a guess: the header is the only place the car's own odometer is set
     * directly, and a half-typed number is not a measurement.
     */
    override fun onOdometerChange(value: String) =
        edit { it.copy(odometerKm = value.trim().takeIf { v -> v.isNotBlank() }?.toIntOrNull()) }

    private fun edit(change: (VehicleEntity) -> VehicleEntity) {
        viewModelScope.launch {
            repository.vehicle()?.let { repository.saveVehicle(change(it)) }
        }
    }
}
