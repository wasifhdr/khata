package com.wasif.khata.core.permission

import com.wasif.khata.core.prefs.HomeView
import com.wasif.khata.core.prefs.KhataPreferences
import com.wasif.khata.core.prefs.PreferencesRepository
import com.wasif.khata.core.ui.theme.ThemeSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SmsPermissionRepositoryTest {

    private class FakeChecker(
        var granted: Boolean = false,
        var rationale: Boolean = true,
    ) : SmsPermissionChecker {
        override fun isGranted(): Boolean = granted
        override fun shouldShowRationale(): Boolean = rationale
    }

    private val requested = MutableStateFlow(false)

    private val prefs = object : PreferencesRepository {
        override val preferences = requested.map {
            KhataPreferences.Default.copy(hasRequestedSmsPermission = it)
        }
        override suspend fun setTheme(spec: ThemeSpec) = Unit
        override suspend fun resetTheme() = Unit
        override suspend fun setHomeView(view: HomeView) = Unit
        override suspend fun setMonthlyBudget(minor: Long?) = Unit
        override suspend fun setSmsPermissionRequested() {
            requested.value = true
        }
        override suspend fun setBackfilled() = Unit
    }

    private fun repository(checker: FakeChecker) = SmsPermissionRepository(checker, prefs)

    @Test
    fun `a never-requested permission reports NOT_REQUESTED, not DENIED`() = runTest {
        val repo = repository(FakeChecker(granted = false))

        // The two need different copy: one asks, the other explains why it is asking again.
        assertEquals(SmsPermissionState.NOT_REQUESTED, repo.observe().first())
    }

    @Test
    fun `granted reports GRANTED regardless of whether it was ever requested`() = runTest {
        val repo = repository(FakeChecker(granted = true))

        assertEquals(SmsPermissionState.GRANTED, repo.observe().first())

        requested.value = true
        assertEquals(SmsPermissionState.GRANTED, repo.observe().first())
    }

    @Test
    fun `denied once still shows the rationale and reports DENIED`() = runTest {
        val repo = repository(FakeChecker(granted = false, rationale = true))
        requested.value = true

        assertEquals(SmsPermissionState.DENIED, repo.observe().first())
    }

    @Test
    fun `denied twice reports PERMANENTLY_DENIED because the system dialog stops appearing`() = runTest {
        val repo = repository(FakeChecker(granted = false, rationale = false))
        requested.value = true

        assertEquals(SmsPermissionState.PERMANENTLY_DENIED, repo.observe().first())
    }

    @Test
    fun `only PERMANENTLY_DENIED needs the app settings route`() = runTest {
        val checker = FakeChecker(granted = false, rationale = false)
        val repo = repository(checker)
        requested.value = true

        assertEquals(true, repo.observe().first().needsAppSettings)

        checker.rationale = true
        assertEquals(false, repo.observe().first().needsAppSettings)
    }

    @Test
    fun `only GRANTED enables automatic capture`() = runTest {
        assertEquals(true, SmsPermissionState.GRANTED.enablesCapture)
        assertEquals(
            emptyList<SmsPermissionState>(),
            listOf(
                SmsPermissionState.NOT_REQUESTED,
                SmsPermissionState.DENIED,
                SmsPermissionState.PERMANENTLY_DENIED,
            ).filter { it.enablesCapture },
        )
    }

    @Test
    fun `marking requested moves a fresh install off NOT_REQUESTED`() = runTest {
        val repo = repository(FakeChecker(granted = false, rationale = true))
        assertEquals(SmsPermissionState.NOT_REQUESTED, repo.observe().first())

        repo.onRequested()

        assertEquals(SmsPermissionState.DENIED, repo.observe().first())
    }
}
