package com.wasif.khata

import app.cash.turbine.test
import com.wasif.khata.core.prefs.HomeView
import com.wasif.khata.core.prefs.KhataPreferences
import com.wasif.khata.core.prefs.PreferencesRepository
import com.wasif.khata.core.ui.theme.FieldIntensity
import com.wasif.khata.core.ui.theme.ThemeSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MainViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    // Null until the store has produced something, which is the cold-start
    // situation the splash hold exists for. A StateFlow rather than a
    // replayless SharedFlow: the latter suspends on emit until a subscriber
    // exists, and under StandardTestDispatcher the collector has not run yet.
    private val emissions = MutableStateFlow<KhataPreferences?>(null)

    private val repo = object : PreferencesRepository {
        override val preferences: Flow<KhataPreferences> = emissions.filterNotNull()
        override suspend fun setTheme(spec: ThemeSpec) = Unit
        override suspend fun resetTheme() = Unit
        override suspend fun setHomeView(view: HomeView) = Unit
        override suspend fun setMonthlyBudget(minor: Long?) = Unit
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `starts loading and becomes ready on the first emission`() = runTest(dispatcher) {
        val vm = MainViewModel(repo)

        vm.state.test {
            assertEquals(MainUiState.Loading, awaitItem())

            val prefs = KhataPreferences.Default.copy(
                themeSpec = ThemeSpec.Default.copy(intensity = FieldIntensity.Dim),
                homeView = HomeView.Wallet,
            )
            emissions.value = prefs

            assertEquals(MainUiState.Ready(prefs), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
