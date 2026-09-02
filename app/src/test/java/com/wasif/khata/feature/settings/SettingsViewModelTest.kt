package com.wasif.khata.feature.settings

import com.wasif.khata.core.prefs.HomeView
import com.wasif.khata.core.prefs.KhataPreferences
import com.wasif.khata.core.prefs.PreferencesRepository
import com.wasif.khata.core.ui.theme.FieldIntensity
import com.wasif.khata.core.ui.theme.KhataPalette
import com.wasif.khata.core.ui.theme.ThemeSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val prefs = MutableStateFlow(KhataPreferences.Default)
    private var lastSaved: ThemeSpec? = null
    private var resetCalled = false

    private val repo = object : PreferencesRepository {
        override val preferences: Flow<KhataPreferences> = prefs
        override suspend fun setTheme(spec: ThemeSpec) {
            lastSaved = spec
        }
        override suspend fun resetTheme() {
            resetCalled = true
        }
        override suspend fun setHomeView(view: HomeView) = Unit
        override suspend fun setMonthlyBudget(minor: Long?) = Unit
        override suspend fun setSmsPermissionRequested() = Unit
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `changing one axis preserves the other three`() = runTest(dispatcher) {
        prefs.value = KhataPreferences.Default.copy(
            themeSpec = ThemeSpec.Default.copy(intensity = FieldIntensity.Dim),
        )
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()

        vm.onAccentSelected(KhataPalette.accents[2].color)
        advanceUntilIdle()

        assertEquals(KhataPalette.accents[2].color, lastSaved?.accent)
        // The tuner edits one axis at a time; the rest must survive untouched.
        assertEquals(FieldIntensity.Dim, lastSaved?.intensity)
        assertEquals(ThemeSpec.Default.field, lastSaved?.field)
    }

    @Test
    fun `reset delegates rather than writing the default itself`() = runTest(dispatcher) {
        val vm = SettingsViewModel(repo)
        advanceUntilIdle()

        vm.onResetTheme()
        advanceUntilIdle()

        // Reset must clear the stored keys, not write today's default as an
        // explicit value -- otherwise a later change of default is invisible to
        // anyone who ever pressed reset.
        assertTrue(resetCalled)
    }
}
